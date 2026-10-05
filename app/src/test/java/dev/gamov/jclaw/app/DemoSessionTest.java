package dev.gamov.jclaw.app;

import static dev.gamov.jclaw.domain.Contracts.*;
import static dev.gamov.jclaw.testing.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.gamov.jclaw.domain.Delivery;
import dev.gamov.jclaw.memory.SentHistory;
import dev.gamov.jclaw.serialization.Json;
import dev.gamov.jclaw.tools.McpTools;
import dev.gamov.jclaw.tools.MemoryTools;
import dev.gamov.jclaw.tools.SkillCatalog;
import dev.langchain4j.model.chat.ChatModel;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoSessionTest {
  @TempDir Path state;

  private SentHistory history() {
    return new SentHistory(state.resolve("history.json"));
  }

  private DemoSession session(
      DemoMode mode,
      ChatModel identify,
      ChatModel draft,
      ChatModel review,
      McpTools mcp,
      Display display) {
    return new DemoSession(
        mode,
        identify,
        draft,
        review,
        mcp,
        history(),
        new MemoryTools(ROOT.resolve(".shared/memory/documents"), history(), text -> {}),
        new SkillCatalog(ROOT.resolve(".shared/skills"), text -> {}),
        display);
  }

  @Test
  void styleRewriteCannotReplaceReviewedCandidateBeforeApproval() {
    var identify = new ModelEndpoint(ROUTE, "A rewritten chat message");
    var display = new Display();
    try (var mcp = new McpTools(ROOT, "success", text -> {})) {
      var session =
          session(
              DemoMode.GUARDRAILS,
              identify,
              new ModelEndpoint(Json.write(PLAN)),
              new ModelEndpoint(Json.write(APPROVAL)),
              mcp,
              display);
      session.submit("Decline the training");
      session.submit("/chat Rewrite language with facts unchanged");
      assertTrue(history().read().isEmpty());
      session.submit("send");
      assertEquals(PLAN.messageToOrganizer(), history().read().getFirst().message());
      assertEquals(1, display.kinds.stream().filter("DELIVERED"::equals).count());
      assertEquals(1, display.reviews.size());
    }
  }

  @Test
  void substantiveRejectionGetsFreshReviewWithLatestConstraintsAndCanonicalRecipient() {
    var next =
        new DeclineDeployment(
            ExcuseFlavor.DEADLINE,
            null,
            "Literal reviewed deadline message",
            "Explain the real deadline");
    var display = new Display();
    var identify = new ModelEndpoint(ROUTE);
    var draft = new ModelEndpoint(Json.write(PLAN), Json.write(next));
    var review = new ModelEndpoint(Json.write(APPROVAL), Json.write(APPROVAL));
    try (var mcp = new McpTools(ROOT, "success", text -> {})) {
      var session = session(DemoMode.GUARDRAILS, identify, draft, review, mcp, display);
      session.submit("Decline the training run by Dana");
      session.submit("Use the actual delivery deadline; do not mention proficiency");
      var latest = display.reviews.getLast().review().request();
      assertEquals(Scenario.ORGANIZER, latest.organizerName());
      assertEquals(new HashSet<>(Scenario.BURNED), new HashSet<>(latest.recentlyUsedFlavors()));
      assertEquals(List.of(), latest.previouslyProposedFlavors());
      assertTrue(
          latest
              .userInstruction()
              .contains(
                  "Decline the training run by Dana\nHuman feedback: Use the actual delivery deadline; do not mention proficiency"));
      assertEquals(1, identify.requests.size());
      assertEquals(2, draft.requests.size());
      assertEquals(2, review.requests.size());
      assertTrue(identify.requests.getLast().messages().toString().contains("Calendar:"));
      assertTrue(draft.requests.getLast().messages().toString().contains(latest.toString()));
      assertTrue(
          review
              .requests
              .getLast()
              .messages()
              .toString()
              .contains(new DeclineReview(latest, next).toString()));
      session.submit("send");
      assertEquals(next.messageToOrganizer(), history().read().getFirst().message());
      assertEquals(1, display.kinds.stream().filter("HUMAN_REJECT"::equals).count());
    }
  }

  @Test
  void invalidOrUnavailableCriticCannotReachHumanApprovalOrSend() {
    for (var review :
        List.of(
            new ModelEndpoint("{\"tier\":\"AIRTIGHT\",\"feedback\":\"Missing verdict\"}"),
            new ModelEndpoint(
                new dev.langchain4j.exception.TimeoutException("Provider timeout fixture")))) {
      var display = new Display();
      try (var mcp = new McpTools(ROOT, "success", text -> {})) {
        var session =
            session(
                DemoMode.GUARDRAILS,
                new ModelEndpoint(
                    ROUTE, "{\"intent\":\"CHAT\",\"eventId\":\"\"}", "No reviewed candidate"),
                new ModelEndpoint(Json.write(PLAN)),
                review,
                mcp,
                display);
        session.submit("Decline training");
        assertTrue(display.states.contains("BLOCKED"));
        assertFalse(display.states.contains("HUMAN"));
        session.submit("send");
        assertFalse(display.states.contains("SENDING"));
        assertFalse(display.kinds.contains("HUMAN_APPROVE"));
        assertTrue(history().read().isEmpty());
      }
    }
  }

  @Test
  void roundFiveHasNoActionGateAndHoldSendsNothing() {
    try (var mcp = new McpTools(ROOT, "success", text -> {})) {
      var display = new Display();
      var session =
          session(
              DemoMode.WORKFLOW,
              new ModelEndpoint(ROUTE),
              new ModelEndpoint(Json.write(PLAN)),
              new ModelEndpoint(Json.write(APPROVAL)),
              mcp,
              display);
      session.submit("Decline training");
      assertTrue(display.states.contains("PROPOSAL"));
      assertFalse(display.states.contains("HUMAN"));
      assertTrue(history().read().isEmpty());
    }
    try (var mcp = new McpTools(ROOT, "success", text -> {})) {
      var display = new Display();
      var session =
          session(
              DemoMode.GUARDRAILS,
              new ModelEndpoint(ROUTE),
              new ModelEndpoint(Json.write(PLAN)),
              new ModelEndpoint(Json.write(APPROVAL)),
              mcp,
              display);
      session.submit("Decline training");
      session.submit("hold");
      assertTrue(display.states.contains("HELD"));
      assertTrue(history().read().isEmpty());
    }
  }

  @Test
  void queuedApprovalOfOldCandidateCannotAuthorizeRevisedText() {
    var revised =
        new DeclineDeployment(
            ExcuseFlavor.ALREADY_PROFICIENT,
            null,
            "Shorter proficiency message",
            "Shorter explanation");
    var oldId =
        Delivery.candidateId(REQUEST.eventId(), REQUEST.organizerName(), PLAN.messageToOrganizer());
    var newId =
        Delivery.candidateId(
            REQUEST.eventId(), REQUEST.organizerName(), revised.messageToOrganizer());
    try (var mcp = new McpTools(ROOT, "success", line -> {})) {
      var session =
          session(
              DemoMode.GUARDRAILS,
              new ModelEndpoint(ROUTE),
              new ModelEndpoint(Json.write(PLAN), Json.write(revised)),
              new ModelEndpoint(Json.write(APPROVAL), Json.write(APPROVAL)),
              mcp,
              new Display());
      session.submit("Decline training");
      session.submit("Make it shorter but preserve the reason");
      assertThrows(IllegalStateException.class, () -> session.submit("send", oldId));
      assertTrue(history().read().isEmpty());
      session.submit("send", newId);
      assertEquals(revised.messageToOrganizer(), history().read().getFirst().message());
    }
  }
}
