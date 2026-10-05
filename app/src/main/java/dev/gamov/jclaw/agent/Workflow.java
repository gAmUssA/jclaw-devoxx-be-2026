package dev.gamov.jclaw.agent;

import static dev.gamov.jclaw.domain.Contracts.*;

import dev.gamov.jclaw.domain.Delivery;
import dev.gamov.jclaw.domain.SharedPolicy;
import dev.gamov.jclaw.observability.GraphEvents;
import dev.gamov.jclaw.serialization.Json;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.chat.ChatModel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Native Agentic loop and typed roles. Run state belongs to one request, across both critics. */
public final class Workflow {
  public static final int MAX_REFINEMENTS =
      SharedPolicy.resource("workflow/policy.json").required("maxRefinements").intValue();

  public sealed interface Result permits Blocked, Reviewed {}

  public record Blocked(String reason) implements Result {}

  public record Reviewed(
      DeclineRequest request,
      DeclineDeployment plan,
      DeclineCritique critique,
      String runId,
      int refinements)
      implements Result {
    public Reviewed(DeclineRequest request, DeclineDeployment plan, DeclineCritique critique) {
      this(request, plan, critique, UUID.randomUUID().toString(), 0);
    }
  }

  public sealed interface Event permits Candidate, Verdict, Stage, Graph {}

  public enum Phase {
    STARTED,
    COMPLETED,
    FAILED
  }

  public record Stage(String name, Phase phase, int attempt) implements Event {}

  public record Graph(String name, String agentId, Phase phase) implements Event {}

  public record Candidate(DeclineRequest request, DeclineDeployment plan, int attempt)
      implements Event {}

  public record Verdict(DeclineReview review, DeclineCritique critique, int attempt)
      implements Event {}

  public static final class Run {
    private final String id = UUID.randomUUID().toString();
    private DeclineRequest request;
    private final OrganizerContext organizerContext;
    private int refinements;
    private DeclineDeployment previous;
    private String feedback = "";
    private Result result;
    private boolean skipJudge;
    private String stage;

    private Run(DeclineRequest request, OrganizerContext organizerContext) {
      this.request = request;
      this.organizerContext = organizerContext;
    }

    public String id() {
      return id;
    }

    public int refinements() {
      return refinements;
    }

    public DeclineRequest request() {
      return request;
    }
  }

  private final Consumer<Event> events;
  private final UntypedAgent loop;
  private final UntypedAgent human;
  private final WorkflowReports reports = new WorkflowReports();

  public Workflow(ChatModel draftModel, ChatModel reviewModel, Consumer<Event> events) {
    this.events = events;
    var drafter =
        AgenticServices.agentBuilder(AgentRoles.Drafter.class)
            .chatModel(draftModel)
            .name("draftOrRefine")
            .outputKey("plan")
            .build();
    var critic =
        AgenticServices.agentBuilder(AgentRoles.Critic.class)
            .chatModel(reviewModel)
            .name("judge")
            .outputKey("critique")
            .build();
    var prepare =
        AgenticServices.nonAiAgentBuilder(
                scope -> {
                  var run = run(scope);
                  if (run.previous != null) run.refinements++;
                  run.skipJudge = false;
                  run.stage = run.previous == null ? "draft" : "refine";
                  events.accept(new Stage(run.stage, Phase.STARTED, run.refinements + 1));
                  scope.writeState("request", run.request);
                  scope.writeState("organizerContext", run.organizerContext);
                  scope.writeState(
                      "previous",
                      run.previous == null ? "No previous plan" : Json.write(run.previous));
                  scope.writeState("feedback", run.feedback);
                  return run.refinements;
                })
            .name("prepareCandidate")
            .outputKey("refinements")
            .build();
    var reviewInput =
        AgenticServices.nonAiAgentBuilder(
                scope -> {
                  var run = run(scope);
                  var plan = (DeclineDeployment) scope.readState("plan");
                  events.accept(new Candidate(run.request, plan, run.refinements + 1));
                  run.previous = plan;
                  if (plan.fakeCalendarEventId() != null) {
                    run.result =
                        new Blocked("Draft claimed a supporting event this workflow cannot create");
                    run.skipJudge = true;
                  } else if (run.request.recentlyUsedFlavors().contains(plan.flavor())
                      || run.request.previouslyProposedFlavors().contains(plan.flavor())) {
                    run.feedback = "Do not reuse a sent or previously proposed flavor.";
                    run.skipJudge = true;
                    if (run.refinements == MAX_REFINEMENTS) run.result = exhausted(run.feedback);
                  }
                  events.accept(
                      new Stage(
                          run.stage,
                          run.skipJudge ? Phase.FAILED : Phase.COMPLETED,
                          run.refinements + 1));
                  if (!run.skipJudge) {
                    run.stage = "verify";
                    events.accept(new Stage("verify", Phase.STARTED, run.refinements + 1));
                  }
                  return new DeclineReview(run.request, plan);
                })
            .name("assembleExactReview")
            .outputKey("review")
            .build();
    var verdict =
        AgenticServices.nonAiAgentBuilder(
                scope -> {
                  var run = run(scope);
                  var critique = (DeclineCritique) scope.readState("critique");
                  boolean approved =
                      critique.approved() && critique.tier() != PlausibilityTier.HR_WILL_NOTICE;
                  events.accept(
                      new Stage(
                          "verify",
                          approved ? Phase.COMPLETED : Phase.FAILED,
                          run.refinements + 1));
                  events.accept(
                      new Verdict(
                          new DeclineReview(run.request, run.previous),
                          critique,
                          run.refinements + 1));
                  if (approved)
                    run.result =
                        new Reviewed(run.request, run.previous, critique, run.id, run.refinements);
                  else if (critique.approved())
                    run.result = new Blocked("Judge returned a contradictory approval");
                  else if (critique.feedback().isBlank())
                    run.result = new Blocked("Judge returned no rejection feedback");
                  else {
                    run.feedback = critique.feedback();
                    if (run.refinements == MAX_REFINEMENTS) run.result = exhausted(run.feedback);
                  }
                  return run.refinements;
                })
            .name("judgeVerdict")
            .outputKey("refinements")
            .build();
    var judgeSequence =
        AgenticServices.sequenceBuilder()
            .name("judgeCurrentCandidate")
            .subAgents(critic, verdict)
            .build();
    var judge =
        AgenticServices.conditionalBuilder()
            .name("reviewValidCandidate")
            .subAgents(scope -> !run(scope).skipJudge, judgeSequence)
            .build();
    loop =
        AgenticServices.loopBuilder()
            .name("requestScopedReviewLoop")
            .subAgents(prepare, drafter, reviewInput, judge)
            .maxIterations(MAX_REFINEMENTS + 1)
            .exitCondition(scope -> run(scope).result != null)
            .output(scope -> run(scope).result)
            .listener(new GraphEvents(events))
            .listener(reports.review())
            .build();
    var humanNode =
        AgenticServices.humanInTheLoopBuilder()
            .description("Human verdict on the exact current Judge-approved candidate")
            .responseProvider(scope -> scope.readState("humanFeedback"))
            .outputKey("humanVerdict")
            .build();
    human =
        AgenticServices.sequenceBuilder()
            .name("humanReview")
            .subAgents(humanNode)
            .outputKey("humanVerdict")
            .listener(new GraphEvents(events))
            .listener(reports.human())
            .build();
  }

  private static Run run(AgenticScope scope) {
    return (Run) scope.readState("run");
  }

  public List<Path> writeReports(Path directory) throws IOException {
    return reports.write(directory);
  }

  private static Blocked exhausted(String feedback) {
    return new Blocked(
        "Rejected at the shared limit of " + MAX_REFINEMENTS + " refinements: " + feedback);
  }

  public Run begin(DeclineRequest request) {
    return begin(
        request, new OrganizerContext(request.organizerName(), OrganizerSensitivity.UNKNOWN));
  }

  public Run begin(DeclineRequest request, OrganizerContext organizerContext) {
    if (!request.organizerName().equals(organizerContext.organizerName()))
      throw new IllegalArgumentException("Organizer context must match the canonical request");
    return new Run(request, organizerContext);
  }

  public Result run(DeclineRequest request) {
    return resume(begin(request));
  }

  public Result resume(Run run) {
    try {
      return (Result) loop.invoke(Map.of("run", run));
    } catch (LangChain4jException invalid) {
      if (invalid instanceof dev.langchain4j.agentic.agent.AgentInvocationException) {
        boolean modelFailure = false;
        for (Throwable cause = invalid.getCause(); cause != null; cause = cause.getCause()) {
          if (cause instanceof LangChain4jException
              && !(cause instanceof dev.langchain4j.agentic.agent.AgentInvocationException))
            modelFailure = true;
        }
        if (!modelFailure) throw invalid;
      }
      events.accept(new Stage(run.stage, Phase.FAILED, run.refinements + 1));
      run.result =
          new Blocked("Draft or Judge unavailable/invalid; no candidate eligible for send");
      return run.result;
    }
  }

  public void humanVerdict(Reviewed candidate, String feedback) {
    human.invoke(
        Map.of(
            "humanFeedback",
            feedback,
            "reviewedCandidate",
            candidate,
            "candidateId",
            Delivery.candidateId(
                candidate.request().eventId(),
                candidate.request().organizerName(),
                candidate.plan().messageToOrganizer())));
    events.accept(new Stage("human", Phase.COMPLETED, candidate.refinements() + 1));
  }

  public Result reject(Run run, Reviewed candidate, String feedback) {
    requireText(feedback, "human feedback");
    if (!run.id.equals(candidate.runId()) || run.result != candidate)
      throw new IllegalStateException("Human verdict does not match the current request/candidate");
    humanVerdict(candidate, feedback);
    if (run.refinements == MAX_REFINEMENTS) return run.result = exhausted(feedback);
    var request = run.request;
    run.request =
        new DeclineRequest(
            request.eventId(),
            request.recentlyUsedFlavors(),
            request.knownAttendees(),
            request.organizerName(),
            request.userInstruction() + "\nHuman feedback: " + feedback,
            request.previouslyProposedFlavors());
    run.feedback = feedback;
    run.result = null;
    return resume(run);
  }

  public static final class ApprovalGate {
    private Reviewed pending;

    public void propose(Reviewed reviewed) {
      if (!reviewed.critique().approved()
          || reviewed.critique().tier() == PlausibilityTier.HR_WILL_NOTICE)
        throw new IllegalStateException("Rejected candidates cannot reach human approval");
      pending = reviewed;
    }

    public void hold() {
      pending = null;
    }

    public DeclineRequest reject(String instruction) {
      requireText(instruction, "new instruction");
      var request = requirePending().request();
      pending = null;
      return new DeclineRequest(
          request.eventId(),
          request.recentlyUsedFlavors(),
          request.knownAttendees(),
          request.organizerName(),
          request.userInstruction() + "\nHuman feedback: " + instruction,
          request.previouslyProposedFlavors());
    }

    public DeclineSend approve(String candidateId) {
      var reviewed = requirePending();
      var envelope = Delivery.envelope(reviewed.request(), reviewed.plan());
      if (!envelope.candidateId().equals(candidateId))
        throw new IllegalStateException("Approval does not match the reviewed message");
      pending = null;
      return envelope;
    }

    private Reviewed requirePending() {
      if (pending == null)
        throw new IllegalStateException("No reviewed candidate; request a fresh review");
      return pending;
    }
  }
}
