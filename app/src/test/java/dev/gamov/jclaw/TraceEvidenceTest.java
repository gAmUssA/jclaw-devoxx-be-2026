package dev.gamov.jclaw;

import static dev.gamov.jclaw.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceEvidenceTest {
  @TempDir Path state;

  @Test
  void nativeModelCallbacksPersistInputsOutputsAndMeasuredDuration() throws IOException {
    var times = new ArrayDeque<>(List.of(1_000_000L, 751_000_000L));
    var visible = new ArrayList<String>();
    var file = state.resolve("trace.jsonl");
    var evidence = new TraceEvidence(file, visible::add, times::removeFirst);
    var model =
        new ModelEndpoint(
            evidence.modelListener("review", "gemini-fixture"), "Fixed review response");
    assertEquals("Fixed review response", model.chat("Exact fictional review input"));
    var lines = Files.readAllLines(file).stream().map(Json::tree).toList();
    assertEquals(
        List.of("MODEL_INPUT", "MODEL_OUTPUT"),
        lines.stream().map(line -> line.get("kind").asText()).toList());
    assertTrue(lines.getFirst().get("text").asText().contains("Exact fictional review input"));
    assertTrue(lines.getLast().get("text").asText().contains("durationMs=750"));
    assertTrue(lines.getLast().get("text").asText().contains("Fixed review response"));
    assertEquals(2, visible.size());
    assertTrue(times.isEmpty());
  }

  @Test
  void nativeFailureCallbackRecordsDurationWithoutProviderErrorBody() throws IOException {
    var times = new ArrayDeque<>(List.of(1_000_000L, 251_000_000L));
    var file = state.resolve("trace.jsonl");
    var evidence = new TraceEvidence(file, line -> {}, times::removeFirst);
    var model =
        new ModelEndpoint(
            evidence.modelListener("review", "gemini-fixture"),
            new TimeoutException("Sensitive error-body fixture"));
    assertThrows(TimeoutException.class, () -> model.chat("Fictional input"));
    var lines = Files.readAllLines(file).stream().map(Json::tree).toList();
    assertEquals("MODEL_ERROR", lines.getLast().get("kind").asText());
    assertTrue(lines.getLast().get("text").asText().contains("durationMs=250"));
    assertTrue(lines.getLast().get("text").asText().contains("TimeoutException"));
    assertFalse(Files.readString(file).contains("Sensitive error-body fixture"));
  }

  @Test
  void nativeDecisionCallbacksRetainParentAcrossAsyncCompletionAndRecordActualIdentity()
      throws IOException {
    var times = new ArrayDeque<>(List.of(1_000_000L, 126_000_000L));
    var file = state.resolve("decision-trace.jsonl");
    var evidence = new TraceEvidence(file, line -> {}, times::removeFirst);
    evidence.event(
        "STAGE", Json.write(new Workflow.Stage("jevDecision", Workflow.Phase.STARTED, 1)));
    var completion = new CompletableFuture<DecisionResponse>();
    DecisionModel endpoint =
        new DecisionModel() {
          @Override
          public List<DecisionModelListener> listeners() {
            return List.of(evidence.decisionListener());
          }

          @Override
          public DecisionResponse doDecide(DecisionRequest request) {
            throw new UnsupportedOperationException("Async boundary fixture");
          }

          @Override
          public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
            return completion;
          }
        };
    var request = JevDecider.request("Fictional request", List.of(), JevDeciderTest.calendar());
    var result = endpoint.decideAsync(request);
    evidence.event(
        "STAGE",
        Json.write(new Workflow.Stage("otherApplicationStage", Workflow.Phase.STARTED, 1)));
    var response = JevDeciderTest.declineResponse(request, 1, 1, Contracts.Scenario.EVENT_ID);
    completion.complete(response);
    assertEquals(response.choice("event").value(), result.join().choice("event").value());
    assertEquals(
        response.choice("intent").probabilities(), result.join().choice("intent").probabilities());
    var lines = Files.readAllLines(file).stream().map(Json::tree).toList();
    var input =
        lines.stream()
            .filter(line -> line.required("kind").asText().equals("DECISION_INPUT"))
            .findFirst()
            .orElseThrow();
    var output = lines.getLast();
    assertEquals("DECISION_OUTPUT", output.required("kind").asText());
    assertEquals(input.required("parent"), output.required("parent"));
    assertEquals("jevDecision/1", output.required("parent").asText());
    assertTrue(output.required("text").asText().contains("durationMs=125"));
    assertTrue(
        output.required("text").asText().contains("requested=jev-1.13.0 returned=jev-1.13.0"));
    assertTrue(output.required("text").asText().contains("probabilities="));
    assertTrue(input.required("text").asText().contains("Fictional request"));
    assertTrue(input.required("text").asText().contains("questions="));
    assertEquals(input.required("traceId"), output.required("traceId"));
    java.time.Instant.parse(output.required("timestamp").asText());
  }

  @Test
  void decisionFailureHasNoInventedResponseUsageOrCredentialEcho() throws IOException {
    var times = new ArrayDeque<>(List.of(1_000_000L, 26_000_000L));
    var file = state.resolve("decision-error.jsonl");
    var evidence = new TraceEvidence(file, line -> {}, times::removeFirst);
    DecisionModel endpoint =
        new DecisionModel() {
          @Override
          public List<DecisionModelListener> listeners() {
            return List.of(evidence.decisionListener());
          }

          @Override
          public DecisionResponse doDecide(DecisionRequest request) {
            throw new TimeoutException("Sensitive network-body fixture");
          }
        };
    assertThrows(
        TimeoutException.class,
        () ->
            endpoint.decide(
                JevDecider.request("Fictional request", List.of(), JevDeciderTest.calendar())));
    var text = Files.readString(file);
    assertTrue(text.contains("DECISION_ERROR"));
    assertTrue(text.contains("durationMs=25"));
    assertFalse(text.contains("DECISION_OUTPUT"));
    assertFalse(text.contains("Sensitive network-body fixture"));
    assertFalse(text.contains("usage="));
    assertFalse(text.contains("returned="));
  }
}
