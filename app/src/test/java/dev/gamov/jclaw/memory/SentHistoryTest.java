package dev.gamov.jclaw.memory;

import static dev.gamov.jclaw.domain.Contracts.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.gamov.jclaw.domain.Delivery;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SentHistoryTest {
  @TempDir Path state;
  private final DeclineSend send =
      new DeclineSend("call", "candidate", "event", "Dana", "Literal sent message");
  private final DeclineReceipt receipt =
      new DeclineReceipt(true, "call", "candidate", "event", "Dana", "2026-04-02T12:30:00Z");

  @Test
  void restartPreservesLiteralSendsAndDuplicateReceiptDoesNotDuplicateHistory() {
    var path = state.resolve("history.json");
    var history = new SentHistory(path);
    assertTrue(history.read().isEmpty());
    history.record(send, receipt, ExcuseFlavor.DEADLINE);
    history.record(send, receipt, ExcuseFlavor.DEADLINE);
    var restarted = new SentHistory(path).read();
    assertEquals(1, restarted.size());
    assertEquals(send.message(), restarted.getFirst().message());
    assertEquals(send.organizerName(), restarted.getFirst().organizerName());
  }

  @Test
  void invalidAndRefusedReceiptsCannotBecomeHistory() {
    var history = new SentHistory(state.resolve("history.json"));
    assertThrows(
        Delivery.Unconfirmed.class,
        () ->
            history.record(
                send,
                new DeclineReceipt(
                    true, "wrong", "candidate", "event", "Dana", receipt.deliveredAt()),
                ExcuseFlavor.DEADLINE));
    assertThrows(
        Delivery.Refused.class,
        () ->
            history.record(
                send,
                new DeclineReceipt(false, "call", "candidate", "event", "Dana", null),
                ExcuseFlavor.DEADLINE));
    assertTrue(history.read().isEmpty());
  }
}
