package dev.gamov.jclaw.model;

import static dev.gamov.jclaw.testing.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import dev.gamov.jclaw.agent.Workflow;
import dev.gamov.jclaw.app.DemoMode;
import dev.gamov.jclaw.observability.TraceEvidence;
import dev.gamov.jclaw.serialization.Json;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelProvidersTest {
  private static final Map<String, String> KEYS =
      Map.of(
          "GOOGLE_API_KEY", "google-key-fixture",
          "ANTHROPIC_API_KEY", "anthropic-key-fixture",
          "OPENAI_API_KEY", "openai-key-fixture");
  private static final ModelLineup LINEUP = ModelLineup.fromEnvironment(Map.of());
  @TempDir Path temporary;

  @Test
  void earlyRoundsStartWithoutDraftOrJudgeCredentials() {
    var traces = new ArrayList<String>();
    var models =
        ModelProviders.create(
            DemoMode.CHATBOT,
            LINEUP,
            Map.of("GOOGLE_AI_API_KEY", "google-alias-fixture"),
            evidence(traces));
    assertNotNull(models.chat());
    assertEquals(List.of("PROVIDER chat=gemini-3.7-flash (Gemini API)"), traces);
  }

  @Test
  void missingCredentialsBlockBeforeProviderInitializationWithAnActionableSafeMessage() {
    var traces = new ArrayList<String>();
    var evidence = evidence(traces);
    var google =
        assertThrows(
            ModelProviders.MissingSetting.class,
            () -> ModelProviders.create(DemoMode.CHATBOT, LINEUP, Map.of(), evidence));
    assertTrue(google.getMessage().contains("GOOGLE_API_KEY or GOOGLE_AI_API_KEY"));
    var mode = workflowMode();
    var anthropic =
        assertThrows(
            ModelProviders.MissingSetting.class,
            () ->
                ModelProviders.create(
                    mode, LINEUP, Map.of("GOOGLE_API_KEY", KEYS.get("GOOGLE_API_KEY")), evidence));
    assertTrue(anthropic.getMessage().startsWith("Set ANTHROPIC_API_KEY in .env"));
    var openai =
        assertThrows(
            ModelProviders.MissingSetting.class,
            () ->
                ModelProviders.create(
                    mode,
                    LINEUP,
                    Map.of(
                        "GOOGLE_API_KEY", KEYS.get("GOOGLE_API_KEY"),
                        "ANTHROPIC_API_KEY", KEYS.get("ANTHROPIC_API_KEY"),
                        "OPENAI_API_KEY", " "),
                    evidence));
    assertTrue(openai.getMessage().startsWith("Set OPENAI_API_KEY in .env"));
    assertTrue(traces.isEmpty());
    assertFalse(openai.getMessage().contains("key-fixture"));
  }

  @Test
  void nativeProviderHttpRequestsDraftRefineAndJudgeTheExactTypedCandidates() throws IOException {
    var mode = workflowMode();
    var traces = new ArrayList<String>();
    var events = new ArrayList<Workflow.Event>();
    try (var fixture = new ProviderHttpFixture(List.of(REJECTION, Json.write(APPROVAL)), 200)) {
      var models = ModelProviders.create(mode, LINEUP, KEYS, evidence(traces), fixture.endpoints());
      var result =
          (Workflow.Reviewed)
              new Workflow(models.draft(), models.review(), events::add).run(REQUEST);
      assertEquals(1, result.refinements());
      assertEquals("Literal reviewed revised message", result.plan().messageToOrganizer());
      assertEquals(
          List.of("draft", "verify", "refine", "verify"),
          events.stream()
              .filter(Workflow.Stage.class::isInstance)
              .map(Workflow.Stage.class::cast)
              .filter(stage -> stage.phase() == Workflow.Phase.STARTED)
              .map(Workflow.Stage::name)
              .toList());
      assertEquals(4, fixture.calls.size());
      var draft = fixture.calls.get(0);
      var firstReview = fixture.calls.get(1);
      var refine = fixture.calls.get(2);
      var finalReview = fixture.calls.get(3);
      assertTrue(draft.path().endsWith("/messages"));
      assertTrue(firstReview.path().endsWith("/chat/completions"));
      assertEquals("anthropic-key-fixture", draft.apiKey());
      assertEquals("Bearer openai-key-fixture", firstReview.authorization());
      assertEquals("claude-opus-5-5", Json.tree(draft.body()).required("model").asText());
      assertEquals("gpt-6-astra", Json.tree(firstReview.body()).required("model").asText());
      assertTrue(
          Json.tree(firstReview.body())
              .required("response_format")
              .required("json_schema")
              .required("strict")
              .asBoolean());
      assertFalse(Json.tree(firstReview.body()).required("store").asBoolean());
      assertTrue(draft.body().contains("No fabricated facts"));
      assertTrue(firstReview.body().contains(PLAN.messageToOrganizer()));
      assertTrue(refine.body().contains("Be more precise"));
      assertTrue(finalReview.body().contains(result.plan().messageToOrganizer()));
      for (var call : fixture.calls) {
        var tools = Json.tree(call.body()).path("tools");
        assertTrue(tools.isMissingNode() || tools.isNull() || tools.isEmpty());
      }
      // Opus 5.5 cannot disable adaptive thinking or accept forced tool use.
      assertFalse(draft.body().contains("\"type\":\"disabled\""));
      assertFalse(Json.tree(draft.body()).has("temperature"));
      var trace = String.join("\n", traces);
      assertTrue(trace.contains("draft/refine / claude-opus-5-5 (Anthropic API)"));
      assertTrue(trace.contains("review / gpt-6-astra (OpenAI API)"));
      assertTrue(trace.contains("MODEL_OUTPUT"));
      assertFalse(trace.contains("key-fixture"));
      assertFalse(trace.contains("private-thinking-fixture"));
    }
  }

  @Test
  void nativeOpenAiFailureBlocksWithoutRefinementOrProviderFallback() throws IOException {
    var mode = workflowMode();
    var traces = new ArrayList<String>();
    var events = new ArrayList<Workflow.Event>();
    try (var fixture =
        new ProviderHttpFixture(
            List.of("{\"error\":{\"message\":\"openai-key-fixture\",\"type\":\"server_error\"}}"),
            503)) {
      var models = ModelProviders.create(mode, LINEUP, KEYS, evidence(traces), fixture.endpoints());
      assertInstanceOf(
          Workflow.Blocked.class,
          new Workflow(models.draft(), models.review(), events::add).run(REQUEST));
      assertEquals(2, fixture.calls.size());
      assertTrue(events.stream().noneMatch(Workflow.Verdict.class::isInstance));
      assertEquals(
          new Workflow.Stage("verify", Workflow.Phase.FAILED, 1),
          events.stream().filter(Workflow.Stage.class::isInstance).toList().getLast());
      var trace = Files.readString(temporary.resolve("trace.jsonl"));
      assertTrue(trace.contains("MODEL_ERROR"));
      assertTrue(trace.contains("review / gpt-6-astra (OpenAI API)"));
      assertFalse(trace.contains("openai-key-fixture"));
    }
  }

  private TraceEvidence evidence(List<String> traces) {
    return new TraceEvidence(temporary.resolve("trace.jsonl"), traces::add);
  }

  private static DemoMode workflowMode() {
    var mode = Arrays.stream(DemoMode.values()).filter(value -> value.round() >= 5).findFirst();
    assumeTrue(mode.isPresent(), "Workflow removed from this checkpoint");
    return mode.orElseThrow();
  }

  private record HttpCall(String path, String body, String apiKey, String authorization) {}

  /** Local network boundary; native clients and the real Agentic workflow execute. */
  private static final class ProviderHttpFixture implements AutoCloseable {
    private final HttpServer server;
    private final List<HttpCall> calls = new CopyOnWriteArrayList<>();
    private int drafts;
    private int reviews;

    ProviderHttpFixture(List<String> reviewReplies, int reviewStatus) throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            var path = exchange.getRequestURI().getPath();
            calls.add(
                new HttpCall(
                    path,
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    exchange.getRequestHeaders().getFirst("x-api-key"),
                    exchange.getRequestHeaders().getFirst("Authorization")));
            String response;
            int status;
            if (path.endsWith("/messages")) {
              var plan =
                  drafts++ == 0
                      ? Json.write(PLAN)
                      : Json.write(PLAN).replace("proficiency message", "revised message");
              response =
                  Json.write(
                      Map.of(
                          "id", "msg_fixture",
                          "type", "message",
                          "role", "assistant",
                          "model", LINEUP.draft(),
                          "content",
                              List.of(
                                  Map.of(
                                      "type", "thinking",
                                      "thinking", "private-thinking-fixture",
                                      "signature", "signature-fixture"),
                                  Map.of("type", "text", "text", plan)),
                          "stop_reason", "end_turn",
                          "usage", Map.of("input_tokens", 20, "output_tokens", 12)));
              status = 200;
            } else if (path.endsWith("/chat/completions")) {
              var verdict = reviewReplies.get(reviews++);
              response =
                  reviewStatus == 200
                      ? Json.write(
                          Map.of(
                              "id",
                              "chatcmpl_fixture",
                              "object",
                              "chat.completion",
                              "created",
                              1,
                              "model",
                              LINEUP.review(),
                              "choices",
                              List.of(
                                  Map.of(
                                      "index",
                                      0,
                                      "message",
                                      Map.of("role", "assistant", "content", verdict),
                                      "finish_reason",
                                      "stop")),
                              "usage",
                              Map.of(
                                  "prompt_tokens",
                                  15,
                                  "completion_tokens",
                                  10,
                                  "total_tokens",
                                  25)))
                      : verdict;
              status = reviewStatus;
            } else {
              response = "Unexpected provider path";
              status = 404;
            }
            var bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
              output.write(bytes);
            }
          });
      server.start();
    }

    ModelProviders.Endpoints endpoints() {
      var base = "http://127.0.0.1:" + server.getAddress().getPort();
      return new ModelProviders.Endpoints(base + "/anthropic/", base + "/openai/");
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
