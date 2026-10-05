package dev.gamov.jclaw.agent;

import static dev.gamov.jclaw.domain.Contracts.Scenario;
import static dev.gamov.jclaw.testing.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.gamov.jclaw.domain.Delivery;
import dev.gamov.jclaw.serialization.Json;
import dev.langchain4j.exception.TimeoutException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeWorkflowTest {
  @Test
  void nativeAgentsParseTypedContractsAndReviewExactCurrentRequest() {
    var draft = new ModelEndpoint(Json.write(PLAN));
    var review = new ModelEndpoint(Json.write(APPROVAL));
    var events = new ArrayList<Workflow.Event>();
    var result = new Workflow(draft, review, events::add).run(REQUEST);
    assertInstanceOf(Workflow.Reviewed.class, result);
    var verdict =
        events.stream()
            .filter(Workflow.Verdict.class::isInstance)
            .map(Workflow.Verdict.class::cast)
            .toList()
            .getLast();
    assertEquals(REQUEST, verdict.review().request());
    assertEquals(PLAN, verdict.review().plan());
    assertEquals(1, draft.requests.size());
    assertEquals(1, review.requests.size());
    assertTrue(review.requests.getFirst().messages().toString().contains("No fabricated facts"));
    assertTrue(
        review.requests.getFirst().messages().toString().contains(verdict.review().toString()));
    assertTrue(draft.requests.getFirst().toolSpecifications().isEmpty());
    assertTrue(review.requests.getFirst().toolSpecifications().isEmpty());
  }

  @Test
  void seventhRejectionBlocksAfterPreciselySixRefinements() {
    var draft =
        new ModelEndpoint(
            java.util.Collections.nCopies(7, Json.write(PLAN)).toArray(String[]::new));
    var review =
        new ModelEndpoint(java.util.Collections.nCopies(7, REJECTION).toArray(String[]::new));
    assertInstanceOf(Workflow.Blocked.class, new Workflow(draft, review, event -> {}).run(REQUEST));
    assertEquals(7, draft.requests.size());
    assertEquals(7, review.requests.size());
    assertTrue(draft.requests.getLast().messages().toString().contains("Be more precise"));
  }

  @Test
  void refinementApprovalReturnsOnlyNewlyReviewedCandidate() {
    var revised = Json.write(PLAN).replace("proficiency message", "revised message");
    var draft = new ModelEndpoint(Json.write(PLAN), revised);
    var review = new ModelEndpoint(REJECTION, Json.write(APPROVAL));
    var events = new ArrayList<Workflow.Event>();
    var result = (Workflow.Reviewed) new Workflow(draft, review, events::add).run(REQUEST);
    assertEquals("Literal reviewed revised message", result.plan().messageToOrganizer());
    assertEquals(2, draft.requests.size());
    assertEquals(2, review.requests.size());
    assertTrue(draft.requests.getLast().messages().toString().contains("Be more precise"));
    assertTrue(review.requests.getLast().messages().toString().contains(result.plan().toString()));
    assertEquals(
        List.of("draft", "verify", "refine", "verify"),
        events.stream()
            .filter(Workflow.Stage.class::isInstance)
            .map(Workflow.Stage.class::cast)
            .filter(stage -> stage.phase() == Workflow.Phase.STARTED)
            .map(Workflow.Stage::name)
            .toList());
  }

  @Test
  void claimedSupportingEventCannotReachCritic() {
    var draft =
        new ModelEndpoint(
            Json.write(PLAN)
                .replace(
                    "\"fakeCalendarEventId\":null", "\"fakeCalendarEventId\":\"invented-event\""));
    var review = new ModelEndpoint(Json.write(APPROVAL));
    assertInstanceOf(Workflow.Blocked.class, new Workflow(draft, review, event -> {}).run(REQUEST));
    assertTrue(review.requests.isEmpty());
  }

  @Test
  void missingCriticVerdictIsInvalidRatherThanImplicitApproval() {
    var review = new ModelEndpoint("{\"tier\":\"AIRTIGHT\",\"feedback\":\"Missing verdict\"}");
    var events = new ArrayList<Workflow.Event>();
    var result =
        new Workflow(new ModelEndpoint(Json.write(PLAN)), review, events::add).run(REQUEST);
    assertInstanceOf(Workflow.Blocked.class, result);
    assertTrue(events.stream().noneMatch(Workflow.Verdict.class::isInstance));
    assertEquals(1, review.requests.size());
  }

  @Test
  void unavailableCriticBlocksWithoutAutomaticRefinement() {
    var draft = new ModelEndpoint(Json.write(PLAN));
    var review = new ModelEndpoint(new TimeoutException("Provider timeout fixture"));
    var events = new ArrayList<Workflow.Event>();
    assertInstanceOf(Workflow.Blocked.class, new Workflow(draft, review, events::add).run(REQUEST));
    assertEquals(1, draft.requests.size());
    assertEquals(1, review.requests.size());
    assertTrue(events.stream().noneMatch(Workflow.Verdict.class::isInstance));
    assertEquals(
        new Workflow.Stage("verify", Workflow.Phase.FAILED, 1),
        events.stream().filter(Workflow.Stage.class::isInstance).toList().getLast());
  }

  @Test
  void bothCriticsShareSixRefinementsAndApprovalAtTheLimitRemainsValid() {
    var draft =
        new ModelEndpoint(
            java.util.Collections.nCopies(7, Json.write(PLAN)).toArray(String[]::new));
    var verdicts = new ArrayList<String>();
    verdicts.add(REJECTION);
    verdicts.addAll(java.util.Collections.nCopies(6, Json.write(APPROVAL)));
    var review = new ModelEndpoint(verdicts.toArray(String[]::new));
    var events = new ArrayList<Workflow.Event>();
    var workflow = new Workflow(draft, review, events::add);
    var run = workflow.begin(REQUEST);
    var candidate = (Workflow.Reviewed) workflow.resume(run);
    assertEquals(1, run.refinements());
    for (int index = 0; index < 5; index++) {
      candidate =
          (Workflow.Reviewed)
              workflow.reject(run, candidate, "Keep proficiency; revision " + index);
      assertEquals(run.id(), candidate.runId());
      assertEquals(Scenario.ORGANIZER, candidate.request().organizerName());
    }
    assertEquals(6, candidate.refinements());
    assertEquals(7, draft.requests.size());
    assertEquals(7, review.requests.size());
    assertTrue(
        review
            .requests
            .getLast()
            .messages()
            .toString()
            .contains("Human feedback: Keep proficiency; revision 4"));
    var gate = new Workflow.ApprovalGate();
    gate.propose(candidate);
    assertEquals(
        PLAN.messageToOrganizer(),
        gate.approve(
                Delivery.candidateId(
                    REQUEST.eventId(), REQUEST.organizerName(), PLAN.messageToOrganizer()))
            .message());
    assertInstanceOf(Workflow.Blocked.class, workflow.reject(run, candidate, "Rejected at six"));
    assertEquals(7, draft.requests.size());
    assertEquals(6, run.refinements());
    assertTrue(
        events.stream()
            .filter(Workflow.Graph.class::isInstance)
            .map(Workflow.Graph.class::cast)
            .anyMatch(graph -> graph.name().equals("humanReview")));
  }

  @Test
  void separateRequestStartsWithItsOwnFullBudget() {
    var replies = new ArrayList<>(java.util.Collections.nCopies(7, REJECTION));
    replies.add(Json.write(APPROVAL));
    var workflow =
        new Workflow(
            new ModelEndpoint(
                java.util.Collections.nCopies(8, Json.write(PLAN)).toArray(String[]::new)),
            new ModelEndpoint(replies.toArray(String[]::new)),
            event -> {});
    var first = workflow.begin(REQUEST);
    assertInstanceOf(Workflow.Blocked.class, workflow.resume(first));
    var second = workflow.begin(REQUEST);
    var approved = (Workflow.Reviewed) workflow.resume(second);
    assertEquals(6, first.refinements());
    assertEquals(0, approved.refinements());
    assertNotEquals(first.id(), second.id());
  }
}
