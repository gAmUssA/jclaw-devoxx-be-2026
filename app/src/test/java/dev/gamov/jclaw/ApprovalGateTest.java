package dev.gamov.jclaw;

import static dev.gamov.jclaw.Contracts.*;
import static dev.gamov.jclaw.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApprovalGateTest {
  private final String id =
      Delivery.candidateId(REQUEST.eventId(), REQUEST.organizerName(), PLAN.messageToOrganizer());

  @Test
  void approvalSendsExactReviewedMessageOnlyOnce() {
    var gate = new Workflow.ApprovalGate();
    gate.propose(new Workflow.Reviewed(REQUEST, PLAN, APPROVAL));
    var send = gate.approve(id);
    assertEquals(PLAN.messageToOrganizer(), send.message());
    assertEquals(Scenario.ORGANIZER, send.organizerName());
    assertThrows(IllegalStateException.class, () -> gate.approve(id));
  }

  @Test
  void holdRemovesSendCapability() {
    var gate = new Workflow.ApprovalGate();
    gate.propose(new Workflow.Reviewed(REQUEST, PLAN, APPROVAL));
    gate.hold();
    assertThrows(IllegalStateException.class, () -> gate.approve(id));
  }

  @Test
  void humanCannotOverrideRejectionOrApproveDifferentMessage() {
    var gate = new Workflow.ApprovalGate();
    assertThrows(
        IllegalStateException.class,
        () ->
            gate.propose(
                new Workflow.Reviewed(
                    REQUEST,
                    PLAN,
                    new DeclineCritique(PlausibilityTier.THIN, false, "Not ready"))));
    gate.propose(new Workflow.Reviewed(REQUEST, PLAN, APPROVAL));
    assertThrows(IllegalStateException.class, () -> gate.approve("rewritten-candidate"));
  }

  @Test
  void rejectionPreservesRequestConstraintsAndAppendsHumanFeedback() {
    var gate = new Workflow.ApprovalGate();
    gate.propose(new Workflow.Reviewed(REQUEST, PLAN, APPROVAL));
    var next = gate.reject("Choose another approach");
    assertEquals(
        "No fabricated facts\nHuman feedback: Choose another approach", next.userInstruction());
    assertEquals(REQUEST.recentlyUsedFlavors(), next.recentlyUsedFlavors());
    assertEquals(List.of(), next.previouslyProposedFlavors());
    assertThrows(IllegalStateException.class, () -> gate.approve(id));
  }
}
