package dev.gamov.jclaw;

import static dev.gamov.jclaw.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
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

class GeminiToolRoundTripTest {
  private static final String SIGNATURE = "c2lnbmF0dXJlLWZpeHR1cmU=";
  private static final String SECOND_SIGNATURE = "c2Vjb25kLXNpZ25hdHVyZS1maXh0dXJl";
  @TempDir Path temporary;

  @Test
  void nativeSignedParallelAndSequentialCallsReturnRealMcpFactsWithoutPrivateTraceData()
      throws IOException {
    try (var provider = new GeminiFixture(200)) {
      var trace = new ArrayList<String>();
      var display = new Display();
      var model = model(provider, trace);
      try (var mcp = new McpTools(ROOT, "success", trace::add)) {
        session(model, mcp, display).submit("Read the training time and Dana's sensitivity.");
      }
      assertEquals(List.of("Training starts at 15:00; Dana is TOUCHY."), display.chat);
      assertEquals(List.of("CHAT"), display.states);
      assertEquals(3, provider.requests.size());
      var followUp = Json.tree(provider.requests.get(1)).required("contents");
      var calls = followUp.get(1).required("parts");
      assertEquals(SIGNATURE, calls.get(0).required("thoughtSignature").asText());
      assertFalse(calls.get(1).has("thoughtSignature"));
      assertEquals("calendar-call", calls.get(0).required("functionCall").required("id").asText());
      assertEquals(
          "sensitivity-call", calls.get(1).required("functionCall").required("id").asText());
      var results = followUp.get(2).required("parts");
      assertEquals(2, results.size());
      assertTrue(results.get(0).toString().contains(Contracts.Scenario.EVENT_ID));
      assertTrue(results.get(0).toString().contains("T15:00:00"));
      assertTrue(results.get(1).toString().contains("TOUCHY"));
      var sequential = Json.tree(provider.requests.get(2)).required("contents");
      assertEquals(
          SIGNATURE,
          sequential.get(1).required("parts").get(0).required("thoughtSignature").asText());
      assertEquals(
          SECOND_SIGNATURE,
          sequential.get(3).required("parts").get(0).required("thoughtSignature").asText());
      assertTrue(sequential.get(4).toString().contains("TOUCHY"));
      for (var request : provider.requests) {
        assertFalse(request.contains("private-thinking-fixture"));
      }
      var evidence = Files.readString(temporary.resolve("trace.jsonl"));
      assertTrue(evidence.contains("TOUCHY"));
      assertTrue(evidence.contains("MODEL_OUTPUT"));
      assertFalse(evidence.contains("thought_signature"));
      assertFalse(evidence.contains(SIGNATURE));
      assertFalse(evidence.contains(SECOND_SIGNATURE));
      assertFalse(evidence.contains("private-thinking-fixture"));
      assertFalse(evidence.contains("google-key-fixture"));
      assertFalse(Files.exists(temporary.resolve("history.json")));
    }
  }

  @Test
  void nativeGoogleUnavailableResponseFailsOnceWithoutCredentialEcho() throws IOException {
    try (var provider = new GeminiFixture(503)) {
      var trace = new ArrayList<String>();
      var display = new Display();
      var model = model(provider, trace);
      try (var mcp = new McpTools(temporary, "success", text -> fail("Unexpected MCP call"))) {
        var session = session(model, mcp, display);
        assertThrows(
            dev.langchain4j.exception.InternalServerException.class,
            () -> session.submit("Read the fictional invitation."));
      }
      assertTrue(display.stages.contains(new Workflow.Stage("chat", Workflow.Phase.FAILED, 1)));
      assertTrue(display.chat.isEmpty());
      assertEquals(1, provider.requests.size());
      var evidence = Files.readString(temporary.resolve("trace.jsonl"));
      assertTrue(evidence.contains("MODEL_ERROR"));
      assertFalse(evidence.contains("google-key-fixture"));
    }
  }

  private dev.langchain4j.model.chat.ChatModel model(GeminiFixture provider, List<String> trace) {
    return ModelProviders.gemini(
        "chat",
        "gemini-3.7-flash",
        "google-key-fixture",
        new TraceEvidence(temporary.resolve("trace.jsonl"), trace::add),
        provider.endpoint());
  }

  private DemoSession session(
      dev.langchain4j.model.chat.ChatModel model, McpTools mcp, Display display) {
    var mode = Arrays.stream(DemoMode.values()).filter(value -> value.round() == 2).findFirst();
    assumeTrue(mode.isPresent(), "Tools removed from this checkpoint");
    var history = new SentHistory(temporary.resolve("history.json"));
    return new DemoSession(
        mode.orElseThrow(),
        model,
        model,
        model,
        mcp,
        history,
        new MemoryTools(ROOT.resolve(".shared/memory/documents"), history, text -> {}),
        new SkillCatalog(ROOT.resolve(".shared/skills"), text -> {}),
        display);
  }

  private static Map<String, Object> function(String id, String name, Map<String, String> args) {
    return Map.of("id", id, "name", name, "args", args);
  }

  /** Fixed responses at the network boundary; the native Google client and MCP servers execute. */
  private static final class GeminiFixture implements AutoCloseable {
    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    GeminiFixture(int status) throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            requests.add(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            List<Map<String, Object>> parts =
                switch (requests.size()) {
                  case 1 ->
                      List.of(
                          Map.of("text", "private-thinking-fixture", "thought", true),
                          Map.of(
                              "functionCall",
                              function("calendar-call", "getCalendar", Map.of()),
                              "thoughtSignature",
                              SIGNATURE),
                          Map.of(
                              "functionCall",
                              function(
                                  "sensitivity-call",
                                  "getOrganizerSensitivity",
                                  Map.of("name", "Dana from People Ops"))));
                  case 2 ->
                      List.of(
                          Map.of(
                              "functionCall",
                              function(
                                  "sensitivity-again",
                                  "getOrganizerSensitivity",
                                  Map.of("name", "Dana from People Ops")),
                              "thoughtSignature",
                              SECOND_SIGNATURE));
                  default -> List.of(Map.of("text", "Training starts at 15:00; Dana is TOUCHY."));
                };
            var body =
                status == 200
                    ? Json.write(
                        Map.of(
                            "candidates",
                            List.of(
                                Map.of(
                                    "content",
                                    Map.of("role", "model", "parts", parts),
                                    "finishReason",
                                    "STOP")),
                            "modelVersion",
                            "gemini-3.7-flash",
                            "usageMetadata",
                            Map.of(
                                "promptTokenCount",
                                20,
                                "candidatesTokenCount",
                                10,
                                "totalTokenCount",
                                30)))
                    : "{\"error\":{\"code\":503,\"message\":\"google-key-fixture\",\"status\":\"UNAVAILABLE\"}}";
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.start();
    }

    String endpoint() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
