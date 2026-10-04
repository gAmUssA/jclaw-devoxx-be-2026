package dev.gamov.jclaw;

import static dev.gamov.jclaw.Contracts.*;
import static dev.gamov.jclaw.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoundBoundaryTest {
  @TempDir Path state;

  private DemoSession session(
      DemoMode mode, ModelEndpoint model, McpTools mcp, SkillCatalog skills) {
    var history = new SentHistory(state.resolve("history.json"));
    return new DemoSession(
        mode,
        model,
        new ModelEndpoint(Json.write(PLAN)),
        new ModelEndpoint(Json.write(APPROVAL)),
        mcp,
        history,
        new MemoryTools(ROOT.resolve(".shared/memory/documents"), history, text -> {}),
        skills,
        new Display());
  }

  private SkillCatalog skills() {
    return new SkillCatalog(ROOT.resolve(".shared/skills"), text -> {});
  }

  private static DemoMode mode(int round) {
    var selected =
        java.util.Arrays.stream(DemoMode.values())
            .filter(value -> value.round() == round)
            .findFirst();
    assumeTrue(selected.isPresent(), "Feature removed from this checkpoint");
    return selected.orElseThrow();
  }

  private static AiMessage tool(String id, String name, String arguments) {
    return AiMessage.from(
        ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build());
  }

  @Test
  void conversationMemoryStartsInRoundThreeAndResetsOnRestart() {
    for (var mode :
        java.util.Arrays.stream(DemoMode.values()).filter(value -> value.round() <= 3).toList()) {
      var model = new ModelEndpoint("First fixed response", "Second fixed response");
      // No jars exist here: a chat turn must not start either MCP server.
      try (var mcp = new McpTools(state, "success", text -> fail("Unexpected MCP call"))) {
        var session = session(mode, model, mcp, skills());
        session.submit("Remember the marker penguin-lantern");
        session.submit("What marker did I give you?");
        assertEquals(
            mode.round() == 3,
            model.requests.getLast().messages().toString().contains("penguin-lantern"));
        var restartedModel = new ModelEndpoint("Fresh conversation");
        session(mode, restartedModel, mcp, skills()).submit("What marker did I give you?");
        assertFalse(
            restartedModel.requests.getFirst().messages().toString().contains("penguin-lantern"));
      }
    }
  }

  @Test
  void nativeCalendarToolRoundTripUsesTheSharedStdioJarWithoutDeclineReasons() {
    var model =
        new ModelEndpoint(tool("calendar-1", "getCalendar", "{}"), AiMessage.from("Calendar read"));
    try (var mcp = new McpTools(ROOT, "success", text -> {})) {
      session(mode(2), model, mcp, skills()).submit("Read the training invitation");
      var result = model.requests.getLast().messages().toString();
      assertTrue(result.contains(Scenario.EVENT_ID));
      assertTrue(result.contains(Scenario.ORGANIZER));
      assertFalse(result.contains("FAMILY_OBLIGATION"));
      assertEquals(
          List.of("getCalendar", "getOrganizerSensitivity"),
          model.requests.getFirst().toolSpecifications().stream()
              .map(tool -> tool.name())
              .toList());
    }
  }

  @Test
  void durableMemoryToolRetrievesLiteralConfirmedHistoryAcrossNewSessions() {
    var history = new SentHistory(state.resolve("history.json"));
    var send = Delivery.envelope(REQUEST, PLAN);
    history.record(
        send,
        new DeclineReceipt(
            true,
            send.callId(),
            send.candidateId(),
            send.eventId(),
            send.organizerName(),
            "2026-04-02T12:30:00Z"),
        PLAN.flavor());
    var model =
        new ModelEndpoint(
            tool(
                "memory-1",
                "recallSentHistory",
                Json.write(java.util.Map.of("organizerName", Scenario.ORGANIZER))),
            AiMessage.from("History read"));
    try (var mcp = new McpTools(state, "success", text -> fail("Unexpected MCP call"))) {
      session(mode(3), model, mcp, skills()).submit("Which reasons have I sent Dana?");
      var result = model.requests.getLast().messages().toString();
      assertTrue(result.contains(PLAN.messageToOrganizer()));
      assertTrue(result.contains("FAMILY_OBLIGATION"));
      assertTrue(result.contains("CALENDAR_CONFLICT"));
      assertTrue(result.contains("CUSTOMER_ESCALATION"));
    }
  }

  @Test
  void runtimeSkillIsMetadataFirstThenBodyThroughARealReadOnlyTool() throws IOException {
    var reads = new ArrayList<String>();
    var catalog = new SkillCatalog(ROOT.resolve(".shared/skills"), reads::add);
    var model =
        new ModelEndpoint(
            tool("skill-1", "readSkill", "{\"name\":\"corporate-speak\"}"),
            AiMessage.from("Prepared corporate rewrite fixture"));
    try (var mcp = new McpTools(state, "success", text -> fail("Unexpected MCP call"))) {
      session(mode(4), model, mcp, catalog)
          .submit("Rewrite in corporate-speak: We fixed the cache bug.");
      var before = model.requests.getFirst().messages().toString();
      var after = model.requests.getLast().messages().toString();
      assertTrue(before.contains("Metadata[name=corporate-speak"));
      assertFalse(before.contains("# Corporate-speak, up to 11"));
      assertTrue(after.contains("# Corporate-speak, up to 11"));
      assertTrue(after.contains("default to 11"));
      assertTrue(after.contains("Do not invent approvals"));
      assertEquals(List.of("readSkill(corporate-speak)"), reads);
      assertFalse(Files.exists(state.resolve("history.json")));
    }
  }

  @Test
  void skillFollowUpUsesTheCurrentDraftAndPreviousElevenRewriteWithoutActions() {
    var reads = new ArrayList<String>();
    var eleven = "We operationalized the cache remediation to accelerate reliable outcomes.";
    var four = "We fixed the cache bug to improve reliability.";
    var model =
        new ModelEndpoint(
            AiMessage.from("We fixed the cache bug."),
            tool("skill-11", "readSkill", "{\"name\":\"corporate-speak\"}"),
            AiMessage.from(eleven),
            AiMessage.from(four));
    try (var mcp = new McpTools(state, "success", text -> fail("Unexpected MCP call"))) {
      var session =
          session(
              mode(4), model, mcp, new SkillCatalog(ROOT.resolve(".shared/skills"), reads::add));
      session.submit("Draft a status update saying we fixed the cache bug.");
      session.submit("Rewrite in corporate-speak");
      var rewrite = model.requests.get(1).messages().toString();
      assertTrue(rewrite.contains("We fixed the cache bug."));
      session.submit("tone it down to 4");
      var followUp = model.requests.getLast().messages().toString();
      assertTrue(followUp.contains(eleven));
      assertTrue(followUp.contains("tone it down to 4"));
      assertTrue(followUp.contains("default to 11"));
      assertTrue(followUp.contains("Do not invent approvals"));
      assertEquals(List.of("readSkill(corporate-speak)"), reads);
      assertFalse(Files.exists(state.resolve("history.json")));
      assertTrue(
          model.requests.stream()
              .flatMap(request -> request.toolSpecifications().stream())
              .noneMatch(specification -> specification.name().equals("sendDecline")));
    }
  }
}
