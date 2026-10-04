package dev.gamov.jclaw;

import static dev.gamov.jclaw.Contracts.*;
import static dev.gamov.jclaw.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JevSessionTest {
  @TempDir Path state;

  private DemoSession session(
      JevDeciderTest.Endpoint endpoint,
      ModelEndpoint chat,
      ModelEndpoint draft,
      ModelEndpoint judge,
      McpTools mcp,
      Display display) {
    var history = new SentHistory(state.resolve("sent-history.json"));
    return new DemoSession(
        DemoMode.GUARDRAILS,
        new JevDecider(endpoint, lines -> {}, display::workflow),
        chat,
        draft,
        judge,
        mcp,
        history,
        new MemoryTools(ROOT.resolve(".shared/memory/documents"), history, line -> {}),
        new SkillCatalog(ROOT.resolve(".shared/skills"), line -> {}),
        display);
  }

  @Test
  void jevRunsOnceAcrossMixedCriticsThenExactReceiptCreatesOneSentFact() {
    var request = JevDecider.request("Decline training", List.of(), JevDeciderTest.calendar());
    var endpoint =
        new JevDeciderTest.Endpoint(
            JevDeciderTest.declineResponse(request, 1, 1, Scenario.EVENT_ID));
    var revised =
        new DeclineDeployment(
            ExcuseFlavor.ALREADY_PROFICIENT,
            null,
            "Hi Dana, I build AI agents professionally; please excuse me from the introductory training.",
            "I build these systems professionally.");
    var draft = new ModelEndpoint(Json.write(PLAN), Json.write(PLAN), Json.write(revised));
    var judge = new ModelEndpoint(REJECTION, Json.write(APPROVAL), Json.write(APPROVAL));
    var chat = new ModelEndpoint("No chat should run");
    var display = new Display();
    var trace = new ArrayList<String>();
    try (var mcp = new McpTools(ROOT, "success", trace::add)) {
      var session = session(endpoint, chat, draft, judge, mcp, display);
      session.submit(
          "Get me out of the Basic AI Proficiency Training on Tuesday, run by Dana from People Ops. Don't reuse an excuse I've already used on her - tell me which ones you're avoiding.");
      session.submit(
          "Make the email shorter and more direct. Keep the proficiency reason; remove the Tuesday-afternoon reference.");
      assertEquals(1, endpoint.requests.size());
      assertEquals(3, draft.requests.size());
      assertEquals(3, judge.requests.size());
      assertTrue(chat.requests.isEmpty());
      var latest = display.reviews.getLast();
      assertEquals(3, latest.attempt());
      assertEquals(Scenario.ORGANIZER, latest.review().request().organizerName());
      assertEquals(Scenario.EVENT_ID, latest.review().request().eventId());
      assertTrue(
          latest
              .review()
              .request()
              .userInstruction()
              .contains("Human feedback: Make the email shorter"));
      assertTrue(draft.requests.getLast().messages().toString().contains("Previous plan:"));
      assertEquals(
          2, trace.stream().filter(line -> line.startsWith("MCP getCalendar INPUT")).count());
      assertEquals(
          1,
          trace.stream()
              .filter(line -> line.startsWith("MCP getOrganizerSensitivity INPUT"))
              .count());
      session.submit("send");
      var history = new SentHistory(state.resolve("sent-history.json")).read();
      assertEquals(1, history.size());
      assertEquals(revised.messageToOrganizer(), history.getFirst().message());
      assertEquals(Scenario.ORGANIZER, history.getFirst().organizerName());
      assertTrue(display.kinds.indexOf("DELIVERED") < display.kinds.indexOf("MEMORY_SAVED"));
      session.submit("send");
      assertEquals(
          1, trace.stream().filter(line -> line.startsWith("MCP sendDecline INPUT")).count());
    }
  }

  @Test
  void uncertainJevAsksWithoutDraftJudgeSendOrHistory() {
    var request = JevDecider.request("Uncertain target", List.of(), JevDeciderTest.calendar());
    var endpoint =
        new JevDeciderTest.Endpoint(
            JevDeciderTest.declineResponse(request, 1, .59, Scenario.EVENT_ID));
    var draft = new ModelEndpoint(Json.write(PLAN));
    var judge = new ModelEndpoint(Json.write(APPROVAL));
    var display = new Display();
    try (var mcp = new McpTools(ROOT, "success", line -> {})) {
      session(endpoint, new ModelEndpoint("Unused chat"), draft, judge, mcp, display)
          .submit("Uncertain target");
    }
    assertTrue(draft.requests.isEmpty());
    assertTrue(judge.requests.isEmpty());
    assertFalse(display.states.contains("HUMAN"));
    assertTrue(display.chat.getLast().contains("Please name one calendar event"));
    assertTrue(new SentHistory(state.resolve("sent-history.json")).read().isEmpty());
  }

  @Test
  void jevContractOrTransportErrorsStopWithoutGeminiFallback() {
    var request = JevDecider.request("Decline", List.of(), JevDeciderTest.calendar());
    var valid = JevDeciderTest.declineResponse(request, 1, 1, Scenario.EVENT_ID);
    var wrongModel =
        DecisionResponse.builder().modelName("unvalidated-model").answers(valid.answers()).build();
    for (var endpoint :
        List.of(
            new JevDeciderTest.Endpoint(wrongModel),
            new JevDeciderTest.Endpoint(new TimeoutException("Network timeout fixture")))) {
      var chat = new ModelEndpoint(ROUTE);
      var draft = new ModelEndpoint(Json.write(PLAN));
      var judge = new ModelEndpoint(Json.write(APPROVAL));
      var display = new Display();
      try (var mcp = new McpTools(ROOT, "success", line -> {})) {
        var session = session(endpoint, chat, draft, judge, mcp, display);
        assertThrows(RuntimeException.class, () -> session.submit("Decline"));
      }
      assertTrue(draft.requests.isEmpty());
      assertTrue(judge.requests.isEmpty());
      assertTrue(chat.requests.isEmpty());
      assertTrue(
          display.stages.contains(new Workflow.Stage("jevDecision", Workflow.Phase.FAILED, 1)));
      assertFalse(display.kinds.contains("HUMAN_APPROVE"));
      assertTrue(new SentHistory(state.resolve("sent-history.json")).read().isEmpty());
    }
  }

  @Test
  void humanRejectionAtSixBlocksWithoutDeliveryAndNewRequestGetsAFreshBudget() {
    var request = JevDecider.request("Decline training", List.of(), JevDeciderTest.calendar());
    var endpoint =
        new JevDeciderTest.Endpoint(
            JevDeciderTest.declineResponse(request, 1, 1, Scenario.EVENT_ID));
    var plans = new ArrayList<String>();
    for (int i = 0; i < 7; i++)
      plans.add(
          Json.write(
              new DeclineDeployment(
                  ExcuseFlavor.ALREADY_PROFICIENT,
                  null,
                  "Proficiency candidate " + i,
                  "Experience")));
    plans.add(
        Json.write(
            new DeclineDeployment(
                ExcuseFlavor.DEADLINE, null, "Separate request candidate", "Deadline")));
    var draft = new ModelEndpoint(plans.toArray(String[]::new));
    var judge =
        new ModelEndpoint(
            java.util.Collections.nCopies(8, Json.write(APPROVAL)).toArray(String[]::new));
    var display = new Display();
    var trace = new ArrayList<String>();
    try (var mcp = new McpTools(ROOT, "success", trace::add)) {
      var session = session(endpoint, new ModelEndpoint("Unused chat"), draft, judge, mcp, display);
      session.submit("Decline training");
      for (int i = 1; i <= 6; i++) session.submit("Please shorten revision " + i);
      assertEquals(7, draft.requests.size());
      assertEquals(7, judge.requests.size());
      assertEquals(1, endpoint.requests.size());
      var latest = display.reviews.getLast();
      assertEquals(7, latest.attempt());
      assertTrue(
          latest
              .review()
              .request()
              .userInstruction()
              .contains("Human feedback: Please shorten revision 6"));
      session.submit("I reject this final candidate too");
      assertEquals("BLOCKED", display.states.getLast());
      session.submit("send");
      assertEquals(7, draft.requests.size());
      assertEquals(
          0, trace.stream().filter(line -> line.startsWith("MCP sendDecline INPUT")).count());
      assertTrue(new SentHistory(state.resolve("sent-history.json")).read().isEmpty());
      session.submit("/new Decline training using a different reason");
      assertEquals(2, endpoint.requests.size());
      assertEquals(8, draft.requests.size());
      assertEquals(1, display.reviews.getLast().attempt());
      assertEquals("HUMAN", display.states.getLast());
      session.submit("hold");
    }
  }

  @Test
  void confirmedDeliveryAndHistoryFailureRemainSeparateFacts() throws IOException {
    var request = JevDecider.request("Decline training", List.of(), JevDeciderTest.calendar());
    var endpoint =
        new JevDeciderTest.Endpoint(
            JevDeciderTest.declineResponse(request, 1, 1, Scenario.EVENT_ID));
    var display = new Display();
    var trace = new ArrayList<String>();
    try (var mcp = new McpTools(ROOT, "success", trace::add)) {
      var session =
          session(
              endpoint,
              new ModelEndpoint("Unused chat"),
              new ModelEndpoint(Json.write(PLAN)),
              new ModelEndpoint(Json.write(APPROVAL)),
              mcp,
              display);
      session.submit("Decline training");
      // A filesystem boundary failure after review must not erase the matching receipt.
      Files.createDirectory(state.resolve("sent-history.json"));
      assertThrows(UncheckedIOException.class, () -> session.submit("send"));
      assertTrue(display.kinds.contains("DELIVERED"));
      assertTrue(display.kinds.contains("MEMORY_FAILED"));
      assertFalse(display.kinds.contains("MEMORY_SAVED"));
      assertTrue(display.stages.contains(new Workflow.Stage("memory", Workflow.Phase.FAILED, 1)));
      session.submit("send");
      assertEquals(
          1, trace.stream().filter(line -> line.startsWith("MCP sendDecline INPUT")).count());
      try (var entries = Files.list(state.resolve("sent-history.json"))) {
        assertEquals(0, entries.count());
      }
    }
  }
}
