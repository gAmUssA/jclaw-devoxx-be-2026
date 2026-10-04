package dev.gamov.jclaw;

import static dev.gamov.jclaw.Contracts.*;
import static dev.gamov.jclaw.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs unchanged on main and every derived checkpoint; unavailable features are tested at entry.
 */
class CheckpointBoundaryTest {
  @TempDir Path state;

  private static int maximum() {
    return Arrays.stream(DemoMode.values()).mapToInt(DemoMode::round).max().orElseThrow();
  }

  @Test
  void unavailableCommandsAreRejectedBeforeProviderOrStateInitialization() {
    var commands =
        List.of("chatbot", "tools", "memory", "skills", "workflow", "guardrails", "observability");
    for (int round = maximum() + 1; round <= 7; round++) {
      var command = commands.get(round - 1);
      assertThrows(
          IllegalArgumentException.class, () -> Main.main(new String[] {command, "plain"}));
    }
    assertEquals(Math.min(maximum(), 6), DemoMode.defaultMode().round());
  }

  @Test
  void highestAvailableRoundExposesOnlyItsCapabilitiesThroughNativeServices() {
    var mode =
        Arrays.stream(DemoMode.values())
            .filter(value -> value.round() == maximum())
            .findFirst()
            .orElseThrow();
    var request = JevDecider.request("Decline training", List.of(), JevDeciderTest.calendar());
    var decisions =
        new JevDeciderTest.Endpoint(
            JevDeciderTest.declineResponse(request, 1, 1, Scenario.EVENT_ID));
    var chat = new ModelEndpoint("Conversation fixture");
    var draft = new ModelEndpoint(Json.write(PLAN));
    var judge = new ModelEndpoint(Json.write(APPROVAL));
    var display = new Display();
    var trace = new ArrayList<String>();
    var history = new SentHistory(state.resolve("history.json"));
    try (var mcp = new McpTools(ROOT, "success", trace::add)) {
      var session =
          new DemoSession(
              mode,
              new JevDecider(decisions, lines -> {}),
              chat,
              draft,
              judge,
              mcp,
              history,
              new MemoryTools(ROOT.resolve(".shared/memory/documents"), history, line -> {}),
              new SkillCatalog(ROOT.resolve(".shared/skills"), line -> {}),
              display);
      session.submit("Decline training");
      if (mode.round() < 5) {
        assertTrue(decisions.requests.isEmpty());
        assertTrue(draft.requests.isEmpty());
        assertTrue(judge.requests.isEmpty());
        var tools =
            chat.requests.getFirst().toolSpecifications().stream()
                .map(tool -> tool.name())
                .toList();
        assertEquals(mode.round() >= 2, tools.contains("getCalendar"));
        assertEquals(mode.round() >= 2, tools.contains("getOrganizerSensitivity"));
        assertEquals(mode.round() >= 3, tools.contains("recallSentHistory"));
        assertEquals(mode.round() >= 4, tools.contains("readSkill"));
        assertFalse(tools.contains("sendDecline"));
        session.submit("send");
        assertTrue(trace.isEmpty());
      } else {
        assertEquals(1, decisions.requests.size());
        assertEquals(1, draft.requests.size());
        assertEquals(1, judge.requests.size());
        assertEquals(mode.round() >= 6, display.states.contains("HUMAN"));
        session.submit("send");
        assertEquals(mode.round() >= 6 ? 1 : 0, history.read().size());
      }
      assertEquals(
          mode.round() >= 6 ? 1 : 0,
          trace.stream().filter(line -> line.startsWith("MCP sendDecline INPUT")).count());
    }
  }

  @Test
  void roundFiveStopsAtProposalEvenWhenAskedToSend() {
    var selected = Arrays.stream(DemoMode.values()).filter(value -> value.round() == 5).findFirst();
    assumeTrue(selected.isPresent(), "Workflow removed from this checkpoint");
    var request = JevDecider.request("Decline training", List.of(), JevDeciderTest.calendar());
    var decisions =
        new JevDeciderTest.Endpoint(
            JevDeciderTest.declineResponse(request, 1, 1, Scenario.EVENT_ID));
    var display = new Display();
    var history = new SentHistory(state.resolve("history.json"));
    var trace = new ArrayList<String>();
    try (var mcp = new McpTools(ROOT, "success", trace::add)) {
      var session =
          new DemoSession(
              selected.orElseThrow(),
              new JevDecider(decisions, lines -> {}),
              new ModelEndpoint("Unused chat"),
              new ModelEndpoint(Json.write(PLAN)),
              new ModelEndpoint(Json.write(APPROVAL)),
              mcp,
              history,
              new MemoryTools(ROOT.resolve(".shared/memory/documents"), history, line -> {}),
              new SkillCatalog(ROOT.resolve(".shared/skills"), line -> {}),
              display);
      session.submit("Decline training");
      session.submit("send");
      assertTrue(display.states.contains("PROPOSAL"));
      assertFalse(display.states.contains("HUMAN"));
      assertTrue(history.read().isEmpty());
      assertEquals(
          0, trace.stream().filter(line -> line.startsWith("MCP sendDecline INPUT")).count());
    }
  }
}
