package dev.gamov.jclaw;

import static dev.gamov.jclaw.Contracts.*;
import static dev.gamov.jclaw.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpDeliveryTest {
  @TempDir Path state;

  @Test
  void canonicalCalendarUsesFictionalDateWithoutReasons() {
    try (var mcp = new McpTools(ROOT, "success", text -> {})) {
      var event =
          mcp.events().stream()
              .filter(value -> value.id().equals(Scenario.EVENT_ID))
              .findFirst()
              .orElseThrow();
      assertEquals(Scenario.ORGANIZER, event.organizer());
      assertEquals("2026-10-06T15:00:00+02:00", event.start());
      assertFalse(event.declined());
      assertFalse(mcp.calendarJson().contains("FAMILY_OBLIGATION"));
    }
  }

  @Test
  void actualStdioOrganizerConfirmsAllFiveFieldsAndLiteralHistory() {
    try (var mcp = new McpTools(ROOT, "success", text -> {})) {
      var send = Delivery.envelope(REQUEST, PLAN);
      var receipt = mcp.send(send);
      var history = new SentHistory(state.resolve("history.json"));
      history.record(send, receipt, PLAN.flavor());
      assertEquals(send.message(), history.read().getFirst().message());
      assertEquals(send.callId(), receipt.callId());
      assertEquals(send.candidateId(), receipt.candidateId());
    }
  }

  @Test
  void allSharedFailureFixturesLeaveHistoryAbsent() {
    for (var mode :
        List.of("refused", "wrong-call", "wrong-candidate", "wrong-event", "malformed", "error")) {
      var file = state.resolve(mode + ".json");
      try (var mcp = new McpTools(ROOT, mode, text -> {})) {
        var send = Delivery.envelope(REQUEST, PLAN);
        var expected = mode.equals("refused") ? Delivery.Refused.class : Delivery.Unconfirmed.class;
        assertThrows(
            expected, () -> new SentHistory(file).record(send, mcp.send(send), PLAN.flavor()));
        assertFalse(Files.exists(file), mode);
      }
    }
  }
}
