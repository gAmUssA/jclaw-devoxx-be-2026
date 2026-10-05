package dev.gamov.jclaw.app;

import static dev.gamov.jclaw.domain.Contracts.*;

import dev.gamov.jclaw.agent.AgentRoles;
import dev.gamov.jclaw.agent.GeminiDecider;
import dev.gamov.jclaw.agent.TurnDecider;
import dev.gamov.jclaw.agent.Workflow;
import dev.gamov.jclaw.domain.Delivery;
import dev.gamov.jclaw.memory.SentHistory;
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
    chat = chatBuilder.build();
    workflow = null;
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
  }

  private static String candidateId(Workflow.Reviewed candidate) {
    return Delivery.candidateId(
        candidate.request().eventId(),
        candidate.request().organizerName(),
        candidate.plan().messageToOrganizer());
  }

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
}
