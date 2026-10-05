package dev.gamov.jclaw.domain;

import static dev.gamov.jclaw.domain.Contracts.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.gamov.jclaw.serialization.Json;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryTest {
  private final DeclineSend send =
      new DeclineSend(
          "call", Delivery.candidateId("event", "Dana", "No thanks"), "event", "Dana", "No thanks");

  private DeclineReceipt receipt() {
    return new DeclineReceipt(
        true,
        send.callId(),
        send.candidateId(),
        send.eventId(),
        send.organizerName(),
        "2026-04-02T12:30:00+02:00");
  }

  @Test
  void acceptsMatchingSuccessfulReceipt() {
    assertEquals(receipt(), Delivery.confirm(Json.write(receipt()), false, send));
  }

  @Test
  void everyIdentityMismatchIsUnknownEvenWhenDeliveredFalse() {
    for (var receipt :
        List.of(
            new DeclineReceipt(
                true, "wrong", send.candidateId(), "event", "Dana", "2026-04-02T12:30:00Z"),
            new DeclineReceipt(true, "call", "wrong", "event", "Dana", "2026-04-02T12:30:00Z"),
            new DeclineReceipt(
                true, "call", send.candidateId(), "wrong", "Dana", "2026-04-02T12:30:00Z"),
            new DeclineReceipt(
                true, "call", send.candidateId(), "event", "wrong", "2026-04-02T12:30:00Z"))) {
      assertThrows(
          Delivery.Unconfirmed.class, () -> Delivery.confirm(Json.write(receipt), false, send));
      var refused =
          new DeclineReceipt(
              false,
              receipt.callId(),
              receipt.candidateId(),
              receipt.eventId(),
              receipt.organizerName(),
              null);
      assertThrows(
          Delivery.Unconfirmed.class, () -> Delivery.confirm(Json.write(refused), false, send));
    }
  }

  @Test
  void missingMalformedErrorAndInvalidTimestampsAreUnknown() {
    assertThrows(Delivery.Unconfirmed.class, () -> Delivery.confirm(null, false, send));
    for (var payload :
        List.of(
            "not JSON",
            "{}",
            "{\"delivered\":\"true\"}",
            Json.write(new DeclineReceipt(true, "call", send.candidateId(), "event", "Dana", null)),
            Json.write(
                new DeclineReceipt(
                    true, "call", send.candidateId(), "event", "Dana", "2026-04-02T12:30:00")))) {
      assertThrows(Delivery.Unconfirmed.class, () -> Delivery.confirm(payload, false, send));
    }
    assertThrows(
        Delivery.Unconfirmed.class, () -> Delivery.confirm(Json.write(receipt()), true, send));
  }

  @Test
  void matchingExplicitRefusalIsDistinct() {
    var refusal = new DeclineReceipt(false, "call", send.candidateId(), "event", "Dana", null);
    assertThrows(Delivery.Refused.class, () -> Delivery.confirm(Json.write(refusal), false, send));
  }

  @Test
  void candidateHashUsesUnambiguousUtf8Lengths() throws NoSuchAlgorithmException {
    assertNotEquals(Delivery.candidateId("ab", "c", "d"), Delivery.candidateId("a", "bc", "d"));
    var expected =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest("2:é4:Dana2:No".getBytes(StandardCharsets.UTF_8)));
    assertEquals(expected, Delivery.candidateId("é", "Dana", "No"));
  }
}
