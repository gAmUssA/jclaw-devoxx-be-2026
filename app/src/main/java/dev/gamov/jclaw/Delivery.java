package dev.gamov.jclaw;

import static dev.gamov.jclaw.Contracts.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class Delivery {
  private Delivery() {}

  public static final class Unconfirmed extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public Unconfirmed(String message) {
      super(message);
    }
  }

  public static final class Refused extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public Refused(String message) {
      super(message);
    }
  }

  public static String candidateId(String event, String organizer, String message) {
    var canonical = new StringBuilder();
    for (var value : List.of(event, organizer, message)) {
      requireText(value, "candidate field");
      canonical.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("JDK must provide SHA-256", impossible);
    }
  }

  public static DeclineSend envelope(DeclineRequest request, DeclineDeployment plan) {
    return new DeclineSend(
        UUID.randomUUID().toString(),
        candidateId(request.eventId(), request.organizerName(), plan.messageToOrganizer()),
        request.eventId(),
        request.organizerName(),
        plan.messageToOrganizer());
  }

  public static DeclineReceipt confirm(String payload, boolean toolError, DeclineSend expected) {
    if (toolError || payload == null)
      throw new Unconfirmed("Missing receipt or tool error; check before retrying");
    final DeclineReceipt receipt;
    try {
      var tree = Json.tree(payload);
      if (tree == null || !tree.path("delivered").isBoolean())
        throw new IllegalArgumentException("Invalid delivered flag");
      receipt = Json.read(payload, DeclineReceipt.class);
    } catch (IllegalArgumentException invalid) {
      throw new Unconfirmed("Malformed receipt; check the organizer before retrying");
    }
    if (!receipt.callId().equals(expected.callId())
        || !receipt.candidateId().equals(expected.candidateId())
        || !receipt.eventId().equals(expected.eventId())
        || !receipt.organizerName().equals(expected.organizerName())) {
      throw new Unconfirmed(
          "Receipt identities differ from this approved send; check before retrying");
    }
    if (!receipt.delivered())
      throw new Refused("Organizer refused delivery; no sent history was written");
    if (receipt.deliveredAt() == null)
      throw new Unconfirmed("Success receipt has no offset timestamp; check before retrying");
    try {
      OffsetDateTime.parse(receipt.deliveredAt());
    } catch (DateTimeParseException invalid) {
      throw new Unconfirmed("Invalid delivery timestamp; check before retrying");
    }
    return receipt;
  }
}
