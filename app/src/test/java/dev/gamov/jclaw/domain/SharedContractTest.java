package dev.gamov.jclaw.domain;

import static dev.gamov.jclaw.domain.Contracts.*;
import static dev.gamov.jclaw.testing.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import kotlinx.serialization.json.Json;
import org.junit.jupiter.api.Test;

/**
 * The upstream Kotlin serializers remain authoritative; Java messages must round-trip through them.
 */
class SharedContractTest {
  @Test
  void requestAndReviewAreReadableByAuthoritativeSerializers() {
    var shared =
        Json.Default.decodeFromString(
            jclaw.domain.DeclineRequest.Companion.serializer(),
            dev.gamov.jclaw.serialization.Json.write(REQUEST));
    assertEquals(REQUEST.eventId(), shared.getEventId());
    assertEquals(REQUEST.organizerName(), shared.getOrganizerName());
    assertEquals(REQUEST.userInstruction(), shared.getUserInstruction());
    assertEquals(
        REQUEST.recentlyUsedFlavors().stream().map(Enum::name).toList(),
        shared.getRecentlyUsedFlavors().stream().map(Enum::name).toList());
    var review =
        Json.Default.decodeFromString(
            jclaw.domain.DeclineReview.Companion.serializer(),
            dev.gamov.jclaw.serialization.Json.write(new DeclineReview(REQUEST, PLAN)));
    assertEquals(PLAN.messageToOrganizer(), review.getPlan().getMessageToOrganizer());
    assertEquals(REQUEST.userInstruction(), review.getRequest().getUserInstruction());
  }

  @Test
  void sharedDeploymentAndCritiqueRoundTripInJava() {
    var deployment =
        Json.Default.decodeFromString(
            jclaw.domain.DeclineDeployment.Companion.serializer(),
            dev.gamov.jclaw.serialization.Json.write(PLAN));
    var sharedJson =
        Json.Default.encodeToString(
            jclaw.domain.DeclineDeployment.Companion.serializer(), deployment);
    assertEquals(
        PLAN, dev.gamov.jclaw.serialization.Json.read(sharedJson, DeclineDeployment.class));
    var critique =
        Json.Default.decodeFromString(
            jclaw.domain.DeclineCritique.Companion.serializer(),
            dev.gamov.jclaw.serialization.Json.write(APPROVAL));
    var critiqueJson =
        Json.Default.encodeToString(jclaw.domain.DeclineCritique.Companion.serializer(), critique);
    assertEquals(
        APPROVAL, dev.gamov.jclaw.serialization.Json.read(critiqueJson, DeclineCritique.class));
  }

  @Test
  void receiptAndSendShareIdentityAndTargetWireFields() {
    var send = Delivery.envelope(REQUEST, PLAN);
    var shared =
        Json.Default.decodeFromString(
            jclaw.domain.DeclineSend.Companion.serializer(),
            dev.gamov.jclaw.serialization.Json.write(send));
    assertEquals(send.callId(), shared.getCallId());
    assertEquals(send.candidateId(), shared.getCandidateId());
    var receipt =
        new DeclineReceipt(
            true,
            send.callId(),
            send.candidateId(),
            send.eventId(),
            send.organizerName(),
            "2026-04-02T12:30:00Z");
    var sharedReceipt =
        Json.Default.decodeFromString(
            jclaw.domain.DeclineReceipt.Companion.serializer(),
            dev.gamov.jclaw.serialization.Json.write(receipt));
    assertEquals(
        receipt,
        dev.gamov.jclaw.serialization.Json.read(
            Json.Default.encodeToString(
                jclaw.domain.DeclineReceipt.Companion.serializer(), sharedReceipt),
            DeclineReceipt.class));
  }
}
