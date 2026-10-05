package dev.gamov.jclaw.app;

import static dev.gamov.jclaw.domain.Contracts.*;

import dev.gamov.jclaw.agent.AgentRoles;
import dev.gamov.jclaw.agent.GeminiDecider;
import dev.gamov.jclaw.agent.JevDecider;
import dev.gamov.jclaw.agent.TurnDecider;
import dev.gamov.jclaw.agent.Workflow;
import dev.gamov.jclaw.domain.Delivery;
import dev.gamov.jclaw.memory.SentHistory;
import dev.gamov.jclaw.serialization.Json;
import dev.gamov.jclaw.tools.McpTools;
import dev.gamov.jclaw.tools.MemoryTools;
import dev.gamov.jclaw.tools.SkillCatalog;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All action capabilities stay here. Native agents receive read-only tool objects. */
public final class DemoSession {
  private final DemoMode mode;
  private final McpTools mcp;
  private final SentHistory history;
  private final MemoryTools memories;
  private final SessionDisplay display;
  private final MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(30);
  private final TurnDecider decider;
  private final List<TurnDecider.Message> conversation = new ArrayList<>();
  private final AgentRoles.Chat chat;
  private final Workflow workflow;
  private final Workflow.ApprovalGate gate = new Workflow.ApprovalGate();
  private final Map<String, List<ExcuseFlavor>> proposed = new HashMap<>();
  private Workflow.Reviewed pending;
  private Workflow.Run run;

  public DemoSession(
      DemoMode mode,
      ChatModel identifyModel,
      ChatModel draftModel,
      ChatModel reviewModel,
      McpTools mcp,
      SentHistory history,
      MemoryTools memories,
      SkillCatalog skills,
      SessionDisplay display) {
    this(
        mode,
        new GeminiDecider(identifyModel),
        identifyModel,
        draftModel,
        reviewModel,
        mcp,
        history,
        memories,
        skills,
        display);
  }

  public DemoSession(
      DemoMode mode,
      TurnDecider decider,
      ChatModel chatModel,
      ChatModel draftModel,
      ChatModel reviewModel,
      McpTools mcp,
      SentHistory history,
      MemoryTools memories,
      SkillCatalog skills,
      SessionDisplay display) {
    this.mode = mode;
    this.mcp = mcp;
    this.history = history;
    this.memories = memories;
    this.display = display;
    this.decider = decider;
    var tools = new ArrayList<Object>();
    // checkpoint:begin tools 2
    if (mode.round() >= 2) tools.add(new McpTools.ReadOnly(mcp));
    // checkpoint:end tools
    // checkpoint:begin memory 3
    if (mode.round() >= 3) tools.add(memories);
    // checkpoint:end memory
    // checkpoint:begin skills 4
    if (mode.round() >= 4) tools.add(skills);
    // checkpoint:end skills
    var systemPrompt =
        "You are Viktor's personal assistant. Never send or claim external actions. "
            + "A language rewrite sends nothing and cannot replace a reviewed candidate. "
            + "User context: "
            + Scenario.USER_CONTEXT
            + (mode.round() >= 2
                ? "\nUse getCalendar for calendar facts. Past declines have no reasons."
                : "")
            + (mode.round() >= 3
                ? "\nUse recallSentHistory to retrieve the reasons from durable memory."
                : "")
            + (mode.round() >= 4
                ? "\nRead a relevant runtime skill body through readSkill before applying it. "
                    + "Corporate-speak defaults to eleven; preserve facts, intent and commitments. "
                    + "\nSkill catalog: "
                    + skills.discover()
                : "");
    var chatBuilder =
        AiServices.builder(AgentRoles.Chat.class)
            .chatModel(chatModel)
            .systemMessageProvider(ignored -> systemPrompt)
            .tools(tools.toArray());
    // checkpoint:begin conversation 3
    if (mode.round() >= 3) chatBuilder.chatMemory(memory);
    // checkpoint:end conversation
    chat = chatBuilder.build();
    // checkpoint:begin workflow 5
    workflow =
        mode.round() >= 5
            ? new Workflow(
                draftModel,
                reviewModel,
                event -> {
                  if (event instanceof Workflow.Candidate candidate) {
                    var flavors =
                        proposed.computeIfAbsent(
                            candidate.request().eventId(), ignored -> new ArrayList<>());
                    if (!flavors.contains(candidate.plan().flavor()))
                      flavors.add(candidate.plan().flavor());
                  }
                  display.workflow(event);
                })
            : null;
    // checkpoint:end workflow
  }

  public void block() {
    gate.hold();
    pending = null;
    run = null;
  }

  public List<Path> writeReports(Path directory) throws IOException {
    return mode.round() >= 7 ? workflow.writeReports(directory) : List.of();
  }

  public void submit(String instruction) {
    submit(instruction, pending == null ? null : candidateId(pending));
  }

  public void submit(String instruction, String approvalCandidateId) {
    requireText(instruction, "instruction");
    var answer = instruction.trim().toLowerCase(java.util.Locale.ROOT);
    // checkpoint:begin humanInput 6
    var candidate = pending;
    if (candidate != null
        && List.of("hold", "n", "no", "no thanks.", "don't send", "cancel").contains(answer)) {
      workflow.humanVerdict(candidate, instruction);
      remember(
          instruction,
          "Held proposal for " + candidate.request().eventId() + "; nothing sent or saved.");
      block();
      display.event("HUMAN_HOLD", "Candidate held; nothing sent");
      display.state("HELD", "Nothing was sent");
      return;
    }
    if (candidate != null && List.of("send", "y", "yes").contains(answer)) {
      if (!candidateId(candidate).equals(approvalCandidateId))
        throw new IllegalStateException(
            "Queued approval belongs to an earlier candidate; review this exact message again");
      memory.add(UserMessage.from(instruction));
      conversation.add(new TurnDecider.Message("user", instruction));
      workflow.humanVerdict(candidate, instruction);
      send(candidate);
      return;
    }
    if (candidate != null && instruction.startsWith("/chat ")) {
      reply(instruction.substring(6));
      display.state("HUMAN", "Ordinary chat leaves the exact reviewed candidate unchanged");
      return;
    }
    if (candidate != null && !instruction.startsWith("/new ")) {
      gate.reject(instruction);
      pending = null;
      conversation.add(new TurnDecider.Message("user", instruction));
      memory.add(UserMessage.from(instruction));
      display.event(
          "HUMAN_REJECT",
          "run=" + run.id() + " refinements=" + run.refinements() + " feedback=" + instruction);
      show(workflow.reject(run, candidate, instruction));
      return;
    }
    // checkpoint:end humanInput
    if (answer.equals("send") || answer.equals("hold")) {
      display.chat("No current reviewed candidate. Nothing was sent.");
      return;
    }
    if (instruction.startsWith("/new ")) {
      block();
      instruction = instruction.substring(5);
    }
    if (mode.round() < 5) {
      reply(instruction);
      display.state("CHAT", "Round " + mode.round() + ": no reviewed delivery capability");
      return;
    }
    // checkpoint:begin declineTurn 5
    display.event("TURN_STARTED", instruction);
    stage("readCalendar", Workflow.Phase.STARTED);
    final List<CalendarEvent> events;
    final TurnDecider.Decision route;
    try {
      events = mcp.events();
    } catch (LangChain4jException | IllegalArgumentException | IllegalStateException invalid) {
      stage("readCalendar", Workflow.Phase.FAILED);
      throw invalid;
    }
    stage("readCalendar", Workflow.Phase.COMPLETED);
    var decisionStage = decider instanceof JevDecider ? "jevDecision" : "identifyComparison";
    stage(decisionStage, Workflow.Phase.STARTED);
    try {
      route = decider.decide(instruction, List.copyOf(conversation), events);
    } catch (LangChain4jException | IllegalArgumentException | IllegalStateException invalid) {
      stage(decisionStage, Workflow.Phase.FAILED);
      throw invalid;
    }
    stage(decisionStage, Workflow.Phase.COMPLETED);
    display.event("ROUTE", route.toString());
    if (route.path() == TurnDecider.Path.ASK) {
      var question =
          "Please name one calendar event with its organizer and day, or clarify whether you want an edit instead of a new plan.";
      remember(instruction, question);
      display.chat(question);
      display.state("CHAT", "Clarification required; no draft, send or history write");
      return;
    }
    if (route.path() == TurnDecider.Path.CHAT) {
      reply(instruction);
      display.state(
          pending == null ? "CHAT" : "HUMAN",
          "Rewrite/chat sends nothing; reviewed candidate is unchanged");
      return;
    }
    var event =
        events.stream()
            .filter(value -> value.id().equals(route.eventId()) && !value.declined())
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Selected event is not an active calendar invitation; clarify the target"));
    stage("assembleRequest", Workflow.Phase.STARTED);
    var canonical =
        mcp.events().stream()
            .filter(value -> value.equals(event))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Selected calendar identity changed before Draft"));
    var sensitivity = mcp.sensitivity(canonical.organizer());
    var organizerContext = OrganizerContext.fromMcp(canonical.organizer(), sensitivity);
    display.event("ORGANIZER", canonical.organizer() + " sensitivity=" + sensitivity);
    var request =
        new DeclineRequest(
            event.id(),
            memories.usedFlavors(event.organizer()),
            event.id().equals(Scenario.EVENT_ID) ? Scenario.ATTENDEES : List.of(),
            event.organizer(),
            instruction,
            proposed.getOrDefault(event.id(), List.of()));
    stage("assembleRequest", Workflow.Phase.COMPLETED);
    display.chat(
        "Avoiding sent flavors: "
            + request.recentlyUsedFlavors()
            + ". Recipient: "
            + request.organizerName());
    memory.add(UserMessage.from(instruction));
    conversation.add(new TurnDecider.Message("user", instruction));
    display.event("WORKFLOW_STARTED", "Current canonical request: " + request);
    display.state("RUNNING", "Identify → Draft → Judge; six shared refinements");
    run = workflow.begin(request, organizerContext);
    display.event(
        "REQUEST_RUN", "run=" + run.id() + " refinements=0 limit=" + Workflow.MAX_REFINEMENTS);
    show(workflow.resume(run));
    // checkpoint:end declineTurn
  }

  private static String candidateId(Workflow.Reviewed candidate) {
    return Delivery.candidateId(
        candidate.request().eventId(),
        candidate.request().organizerName(),
        candidate.plan().messageToOrganizer());
  }

  // checkpoint:begin reviewedCandidate 5
  private void show(Workflow.Result result) {
    switch (result) {
      case Workflow.Blocked blocked -> {
        block();
        display.state("BLOCKED", blocked.reason());
        display.chat("BLOCKED: " + blocked.reason() + ". Nothing was sent.");
        memory.add(AiMessage.from("Blocked: " + blocked.reason()));
        conversation.add(new TurnDecider.Message("assistant", "Blocked: " + blocked.reason()));
      }
      case Workflow.Reviewed reviewed -> {
        display.chat(reviewed.plan().messageToOrganizer());
        display.chat("Hallway script: " + reviewed.plan().hallwayScript());
        display.chat("Judge: " + reviewed.critique().feedback());
        memory.add(AiMessage.from("Reviewed proposal: " + reviewed.plan()));
        conversation.add(
            new TurnDecider.Message("assistant", "Reviewed proposal: " + reviewed.plan()));
        display.event(
            "REVIEWED", "run=" + reviewed.runId() + " refinements=" + reviewed.refinements());
        // checkpoint:begin humanPrompt 6
        if (mode.round() >= 6) {
          gate.propose(reviewed);
          pending = reviewed;
          display.state(
              "HUMAN",
              "send / hold / feedback; "
                  + reviewed.refinements()
                  + "/6 refinements; /chat for ordinary chat; /new for a separate request");
          display.workflow(
              new Workflow.Stage("human", Workflow.Phase.STARTED, reviewed.refinements() + 1));
          return;
        }
        // checkpoint:end humanPrompt
        display.state("PROPOSAL", "Round 5 ends with a reviewed proposal; no human gate or send");
      }
    }
  }

  // checkpoint:end reviewedCandidate

  private void stage(String name, Workflow.Phase phase) {
    display.workflow(new Workflow.Stage(name, phase, 1));
  }

  private void reply(String instruction) {
    stage("chat", Workflow.Phase.STARTED);
    try {
      var response = chat.reply(instruction);
      display.reply(response);
      conversation.add(new TurnDecider.Message("user", instruction));
      conversation.add(new TurnDecider.Message("assistant", response));
      stage("chat", Workflow.Phase.COMPLETED);
    } catch (LangChain4jException invalid) {
      stage("chat", Workflow.Phase.FAILED);
      throw invalid;
    }
  }

  private void remember(String instruction, String response) {
    conversation.add(new TurnDecider.Message("user", instruction));
    conversation.add(new TurnDecider.Message("assistant", response));
    memory.add(UserMessage.from(instruction));
    memory.add(AiMessage.from(response));
  }

  // checkpoint:begin send 6
  private void send(Workflow.Reviewed candidate) {
    var id =
        Delivery.candidateId(
            candidate.request().eventId(),
            candidate.request().organizerName(),
            candidate.plan().messageToOrganizer());
    var envelope = gate.approve(id);
    pending = null;
    display.event("HUMAN_APPROVE", "candidateId=" + id + " callId=" + envelope.callId());
    display.state("SENDING", "Sending the exact reviewed message to the organizer mock");
    stage("send", Workflow.Phase.STARTED);
    final DeclineReceipt receipt;
    try {
      receipt = mcp.send(envelope);
    } catch (Delivery.Refused refused) {
      stage("send", Workflow.Phase.FAILED);
      display.state("BLOCKED", "Explicit refusal; no history written");
      throw refused;
    } catch (Delivery.Unconfirmed uncertain) {
      stage("send", Workflow.Phase.FAILED);
      display.state("UNCONFIRMED", "Check the organizer before retrying; no history written");
      throw uncertain;
    }
    stage("send", Workflow.Phase.COMPLETED);
    display.event("DELIVERED", Json.write(receipt));
    display.state("DELIVERED", "Matching organizer receipt confirmed");
    memory.add(AiMessage.from("Delivered literal message: " + envelope.message()));
    conversation.add(
        new TurnDecider.Message(
            "assistant",
            "Confirmed delivered to " + envelope.organizerName() + ": " + envelope.message()));
    try {
      stage("memory", Workflow.Phase.STARTED);
      history.record(envelope, receipt, candidate.plan().flavor());
      stage("memory", Workflow.Phase.COMPLETED);
      display.event("MEMORY_SAVED", "Literal confirmed message and target persisted");
    } catch (UncheckedIOException failure) {
      stage("memory", Workflow.Phase.FAILED);
      display.event(
          "MEMORY_FAILED",
          "Delivered, but saving history failed; preserve the receipt and repair state permissions");
      throw failure;
    }
  }
  // checkpoint:end send
}
