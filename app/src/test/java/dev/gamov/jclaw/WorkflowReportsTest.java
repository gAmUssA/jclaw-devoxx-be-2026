package dev.gamov.jclaw;

import static dev.gamov.jclaw.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.gamov.jclaw.Contracts.DeclineDeployment;
import dev.gamov.jclaw.Contracts.DeclineRequest;
import dev.gamov.jclaw.Contracts.ExcuseFlavor;
import dev.langchain4j.exception.TimeoutException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowReportsTest {
  @TempDir Path temporary;

  @Test
  void nativeReportsCaptureRefinementAndHumanVerdicts() throws IOException {
    var request =
        new DeclineRequest(
            REQUEST.eventId(),
            REQUEST.recentlyUsedFlavors(),
            REQUEST.knownAttendees(),
            REQUEST.organizerName(),
            "For this fictional rehearsal, I have a hard release deadline this week and am responsible for final release verification. Ask Dana for permission to prioritize that work over Tuesday's training. Use only the deadline reason.",
            REQUEST.previouslyProposedFlavors());
    var plan =
        new DeclineDeployment(
            ExcuseFlavor.DEADLINE,
            null,
            "Hi Dana, I am responsible for final release verification against this week's deadline. May I prioritize that work and skip Tuesday's Basic AI Proficiency Training? Thanks, Viktor",
            "Dana, may I prioritize final release verification for this week's deadline over Tuesday's training?");
    var revised =
        new DeclineDeployment(
            plan.flavor(),
            null,
            "Hi Dana, may I skip Tuesday's training to prioritize final release verification for this week's deadline? Thanks, Viktor",
            "Dana, may I prioritize this week's release verification over Tuesday's training?");
    var draft = new ModelEndpoint(Json.write(plan), Json.write(plan), Json.write(revised));
    var judge = new ModelEndpoint(REJECTION, Json.write(APPROVAL), Json.write(APPROVAL));
    var events = new ArrayList<Workflow.Event>();
    var workflow = new Workflow(draft, judge, events::add);
    var run = workflow.begin(request);
    var first = (Workflow.Reviewed) workflow.resume(run);
    assertEquals(1, first.refinements());
    var second = (Workflow.Reviewed) workflow.reject(run, first, "Shorten both scripts");
    workflow.humanVerdict(second, "hold");
    var files = workflow.writeReports(temporary);
    var reviewHtml = Files.readString(files.getFirst());
    var humanHtml = Files.readString(files.getLast());
    assertEquals(2, second.refinements());
    assertEquals(3, draft.requests.size());
    assertEquals(3, judge.requests.size());
    assertTrue(reviewHtml.contains("System Topology"));
    assertTrue(reviewHtml.contains("Execution History"));
    assertTrue(reviewHtml.contains("requestScopedReviewLoop"));
    assertTrue(reviewHtml.contains("draftOrRefine"));
    assertTrue(reviewHtml.contains("judgeCurrentCandidate"));
    assertTrue(reviewHtml.contains("iter 1"));
    assertTrue(reviewHtml.contains("final release verification"));
    assertFalse(reviewHtml.contains("humanReview"));
    assertTrue(humanHtml.contains("humanReview"));
    assertTrue(humanHtml.contains("Shorten both scripts"));
    assertTrue(humanHtml.contains("hold"));
    assertFalse(humanHtml.contains("requestScopedReviewLoop"));
    assertTrue(events.stream().anyMatch(Workflow.Graph.class::isInstance));

    var fixtureDirectory = System.getProperty("jclaw.report.fixture.directory");
    if (fixtureDirectory != null) {
      var directory = Path.of(fixtureDirectory);
      workflow.writeReports(directory);
      Files.writeString(
          directory.resolve("README.txt"),
          "Provider-free native Agentic workflow fixture.\n"
              + "Three prepared Draft/Judge responses; Judge rejection, human feedback, then hold.\n"
              + "No live model calls, delivery or history writes. Not the previous live rehearsal.\n");
    }
  }

  @Test
  void nativeReportEscapesModelControlledHtml() throws IOException {
    var plan =
        new DeclineDeployment(PLAN.flavor(), null, "Message <script>alert(1)</script>", "Ask Dana");
    var workflow =
        new Workflow(
            new ModelEndpoint(Json.write(plan)),
            new ModelEndpoint(Json.write(APPROVAL)),
            event -> {});
    assertInstanceOf(Workflow.Reviewed.class, workflow.run(REQUEST));
    var html = Files.readString(workflow.writeReports(temporary).getFirst());
    assertTrue(html.contains("Message &lt;script&gt;alert(1)&lt;/script&gt;"));
    assertFalse(html.contains("<script>alert(1)</script>"));
  }

  @Test
  void nativeReportRecordsProviderFailureWithoutRawExceptionBody() throws IOException {
    var workflow =
        new Workflow(
            new ModelEndpoint(Json.write(PLAN)),
            new ModelEndpoint(new TimeoutException("Authorization: Bearer dummy-secret-fixture")),
            event -> {});
    assertInstanceOf(Workflow.Blocked.class, workflow.run(REQUEST));
    var files = workflow.writeReports(temporary);
    var reviewHtml = Files.readString(files.getFirst());
    assertTrue(reviewHtml.contains("Error in judge"));
    assertTrue(reviewHtml.contains("provider details omitted"));
    assertFalse(reviewHtml.contains("dummy-secret-fixture"));
    assertTrue(Files.readString(files.getLast()).contains("No executions recorded."));
  }

  @Test
  void unwritableReportDestinationDoesNotChangeReviewedCandidate() throws IOException {
    var workflow =
        new Workflow(
            new ModelEndpoint(Json.write(PLAN)),
            new ModelEndpoint(Json.write(APPROVAL)),
            event -> {});
    var candidate = (Workflow.Reviewed) workflow.run(REQUEST);
    var file = Files.writeString(temporary.resolve("file"), "not a directory");
    assertThrows(IOException.class, () -> workflow.writeReports(file));
    var gate = new Workflow.ApprovalGate();
    gate.propose(candidate);
    assertEquals(
        PLAN.messageToOrganizer(),
        gate.approve(Delivery.envelope(candidate.request(), candidate.plan()).candidateId())
            .message());
  }
}
