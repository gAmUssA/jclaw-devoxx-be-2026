package dev.gamov.jclaw.app;

import static dev.gamov.jclaw.domain.Contracts.*;

import com.jbaruch.jclaw.tui.CandidateView;
import com.jbaruch.jclaw.tui.ChatKind;
import com.jbaruch.jclaw.tui.DemoOutcome;
import com.jbaruch.jclaw.tui.JclawTui;
import com.jbaruch.jclaw.tui.StageState;
import com.jbaruch.jclaw.tui.TraceKind;
import com.jbaruch.jclaw.tui.TraceStageState;
import dev.gamov.jclaw.agent.JevDecider;
import dev.gamov.jclaw.agent.TurnDecider;
import dev.gamov.jclaw.agent.Workflow;
import dev.gamov.jclaw.domain.Delivery;
import dev.gamov.jclaw.memory.SentHistory;
import dev.gamov.jclaw.model.ModelLineup;
import dev.gamov.jclaw.model.ModelProviders;
import dev.gamov.jclaw.observability.TraceEvidence;
import dev.gamov.jclaw.observability.TraceLogProvider;
import dev.gamov.jclaw.serialization.Json;
import dev.gamov.jclaw.tools.McpTools;
import dev.gamov.jclaw.tools.MemoryTools;
import dev.gamov.jclaw.tools.SkillCatalog;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;

public final class Main {
  private record Turn(String text, String candidateId) {}

  private Main() {}

  public static void main(String[] args) throws Exception {
    var lineup = ModelLineup.fromEnvironment(System.getenv());
    if (Arrays.asList(args).contains("--help")) {
      System.out.println("Usage: ./jclaw [" + DemoMode.commands() + "|preview] [plain]");
      System.out.println(
          "Java 21 / LangChain4j. Jev Identify; Gemini chat; Claude Draft/Refine; OpenAI Judge. Rounds 5–7 require TYPESAFE_API_KEY, ANTHROPIC_API_KEY and OPENAI_API_KEY; chat requires GOOGLE_API_KEY.");
      System.out.println(lineup.providerLegend());
      System.out.println(
          "Ctrl+C exits the dashboard; /quit exits plain mode. Send requires a reviewed candidate and explicit 'send'.");
      System.out.println(
          "/copy copies the latest reply or candidate; /copy reply and /copy candidate select explicitly.");
      System.out.println("In observability mode, /report shows the native HTML report paths.");
      return;
    }
    boolean preview = args.length > 0 && args[0].equals("preview");
    var mode = preview || args.length == 0 ? DemoMode.defaultMode() : DemoMode.parse(args[0]);
    boolean plain = Arrays.asList(args).contains("plain");
    var root = Path.of(env("JCLAW_ROOT", ".")).toAbsolutePath().normalize();
    Files.createDirectories(root.resolve("state"));
    var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    var queue = new LinkedBlockingQueue<Turn>();
    var approvalCandidate = new AtomicReference<String>();
    var latestCandidate = new AtomicReference<String>();
    var copy = new CopyCommands();
    final JclawTui dashboard;
    if (plain) dashboard = null;
    else {
      JclawTui.Companion.quietStdStreams(root.resolve("state/tui.log"));
      var features = new ArrayList<String>();
      if (mode.round() >= 2) features.add("MCP");
      if (mode.round() >= 3) features.add("MEMORY");
      if (mode.round() >= 4) features.add("SKILLS");
      if (mode.round() >= 5) features.add("WORKFLOW");
      if (mode.round() >= 6) features.add("GUARDRAILS");
      features.add(preview ? "UI FIXTURE DATA" : "LOCAL TRACE");
      var flow = new ArrayList<String>();
      if (mode.round() >= 5) {
        flow.addAll(
            List.of(
                "readCalendar",
                "→",
                lineup.decider().equals("jev") ? "jevDecision" : "identifyComparison",
                "→",
                "assembleRequest",
                "→",
                "draft",
                "→",
                "verify",
                "⇄",
                "refine"));
        if (mode.round() >= 6) flow.addAll(List.of("→", "human", "→", "send", "→", "memory"));
      } else flow.add("chat");
      dashboard =
          new JclawTui(
              text -> {
                queue.add(new Turn(text, approvalCandidate.get()));
                return Unit.INSTANCE;
              },
              "j-claw / Java / LangChain4j",
              features,
              flow,
              Workflow.MAX_REFINEMENTS + 1,
              preview
                  ? "LANGCHAIN4J / UI FIXTURE DATA"
                  : "LANGCHAIN4J / R" + mode.round() + " " + mode.name(),
              mode.round() == 5,
              false,
              preview
                  ? "Prepared UI fixture · no providers, sends or framework evidence"
                  : mode.round() >= 5
                      ? lineup.providerLegend()
                      : "chat: "
                          + lineup.chat()
                          + " Gemini API"
                          + (mode.round() >= 2 ? " | calendar: mock" : ""),
              "Gemini",
              mode.round() >= 5
                  ? (lineup.decider().equals("jev") ? "Jev decides" : "Gemini comparison decides")
                      + "; Java assembles; Claude drafts; OpenAI judges"
                  : "Gemini chat with this round's available tools");
    }
    var evidence =
        new TraceEvidence(
            root.resolve("state/trace.jsonl"),
            line -> {
              if (dashboard == null) System.err.println(line);
              else if (line.startsWith("MCP MCP getCalendar INPUT "))
                dashboard.toolCall(
                    "getCalendar", line.substring("MCP MCP getCalendar INPUT ".length()));
              else if (line.startsWith("MCP MCP sendDecline INPUT "))
                dashboard.toolCall(
                    "sendDecline", line.substring("MCP MCP sendDecline INPUT ".length()));
              else if (line.startsWith("MCP MCP getOrganizerSensitivity INPUT "))
                dashboard.toolCall("getOrganizerSensitivity", line);
              else if (line.startsWith("SKILL readSkill(")) dashboard.toolCall("readSkill", line);
              else if (line.startsWith("MEMORY MEMORY_READ "))
                dashboard.toolCall("recallSentHistory", line);
              else
                dashboard.trace(
                    line, line.startsWith("MODEL_") ? TraceKind.LLM : TraceKind.RUNNING);
            });
    TraceLogProvider.setSink(line -> evidence.event("TOOL_STDERR", line));
    var display =
        new SessionDisplay() {
          @Override
          public void reply(String text) {
            copy.reply(text);
            chat(text);
          }

          @Override
          public void chat(String text) {
            if (dashboard == null) System.out.println(text);
            else dashboard.chat(text, ChatKind.JCLAW);
          }

          @Override
          public void event(String kind, String text) {
            evidence.event(kind, text);
            if (kind.equals("TURN_STARTED")) copy.clearCandidate();
            if (dashboard == null) return;
            switch (kind) {
              case "TURN_STARTED" -> {
                dashboard.resetFlow();
              }
              case "DECISION" -> dashboard.decision(List.of(text.split("\n")));
              case "DELIVERED" ->
                  dashboard.deliveryConfirmed(Json.read(text, DeclineReceipt.class).callId());
              case "MEMORY_SAVED" -> dashboard.memorySaved();
              case "MEMORY_FAILED" -> dashboard.memoryFailed();
              default -> {}
            }
          }

          @Override
          public void workflow(Workflow.Event event) {
            switch (event) {
              case Workflow.Stage stage -> {
                if (stage.phase() == Workflow.Phase.STARTED
                    && (stage.name().equals("draft") || stage.name().equals("refine")))
                  approvalCandidate.set(null);
                evidence.event("STAGE", Json.write(stage));
                if (dashboard != null) {
                  var provider =
                      switch (stage.name()) {
                        case "draft", "refine" -> lineup.draft() + " Anthropic API";
                        case "verify" -> lineup.review() + " OpenAI API";
                        case "send" -> "organizer MCP / mock";
                        case "memory" -> "local confirmed-send history";
                        case "human" -> "human / exact candidate";
                        case "readCalendar", "assembleRequest" ->
                            "application / read-only MCP and context";
                        case "jevDecision" ->
                            lineup.decider().equals("jev")
                                ? JevDecider.MODEL + " native DecisionModel"
                                : lineup.identify() + " Gemini comparison";
                        case "identifyComparison" -> lineup.identify() + " Gemini comparison";
                        default -> lineup.chat() + " Gemini API";
                      };
                  if (stage.name().equals("chat")
                      && stage.phase() == Workflow.Phase.STARTED
                      && mode.round() < 5) dashboard.resetFlow();
                  dashboard.stage(
                      stage.name(),
                      switch (stage.phase()) {
                        case STARTED -> StageState.ACTIVE;
                        case COMPLETED -> StageState.DONE;
                        case FAILED -> StageState.FAILED;
                      });
                  dashboard.traceStage(
                      stage.name(), provider, TraceStageState.valueOf(stage.phase().name()));
                  if (!stage.name().equals("human")) {
                    if (stage.phase() == Workflow.Phase.STARTED) dashboard.startBusy();
                    else dashboard.stopBusy();
                  }
                }
              }
              case Workflow.Graph graph -> evidence.graph(graph);
              case Workflow.Candidate candidate -> {
                copy.candidate(candidate.plan().messageToOrganizer());
                latestCandidate.set(
                    Delivery.candidateId(
                        candidate.request().eventId(),
                        candidate.request().organizerName(),
                        candidate.plan().messageToOrganizer()));
                evidence.event(
                    "CANDIDATE",
                    "attempt="
                        + candidate.attempt()
                        + " request="
                        + candidate.request()
                        + " plan="
                        + candidate.plan());
                if (dashboard != null)
                  dashboard.candidate(
                      new CandidateView(
                          candidate.plan().flavor().name(),
                          candidate.request().organizerName(),
                          candidate.request().eventId(),
                          candidate.plan().messageToOrganizer(),
                          candidate.plan().hallwayScript(),
                          candidate.request().userInstruction(),
                          candidate.attempt(),
                          Delivery.candidateId(
                              candidate.request().eventId(),
                              candidate.request().organizerName(),
                              candidate.plan().messageToOrganizer())));
              }
              case Workflow.Verdict verdict -> {
                evidence.event(
                    "VERDICT", "attempt=" + verdict.attempt() + " " + verdict.critique());
                if (dashboard != null)
                  dashboard.reviewResult(
                      verdict.critique().approved(), verdict.critique().feedback());
              }
            }
          }

          @Override
          public void state(String state, String text) {
            approvalCandidate.set(state.equals("HUMAN") ? latestCandidate.get() : null);
            evidence.event("STATE", state + " " + text);
            if (dashboard != null) {
              dashboard.outcome(DemoOutcome.valueOf(state), text);
              if (state.equals("BLOCKED") || state.equals("UNCONFIRMED"))
                dashboard.finishTraceStages(TraceStageState.FAILED);
            }
          }
        };
    if (dashboard == null)
      runSession(
          preview,
          mode,
          lineup,
          root,
          input,
          queue,
          approvalCandidate,
          null,
          evidence,
          display,
          copy);
    else {
      var worker =
          new Thread(
              () -> {
                try {
                  runSession(
                      preview,
                      mode,
                      lineup,
                      root,
                      input,
                      queue,
                      approvalCandidate,
                      dashboard,
                      evidence,
                      display,
                      copy);
                } catch (ModelProviders.MissingSetting missing) {
                  display.state("BLOCKED", missing.getMessage());
                } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                } catch (IOException error) {
                  display.state("BLOCKED", "Local I/O failed; check state permissions");
                }
              },
              "langchain4j-session");
      worker.setDaemon(true);
      worker.setUncaughtExceptionHandler(
          (thread, error) ->
              display.state(
                  "BLOCKED",
                  "Session stopped: "
                      + error.getClass().getSimpleName()
                      + "; inspect trace and restart"));
      worker.start();
      try {
        dashboard.run();
      } finally {
        dashboard.finishTraceStages(TraceStageState.CANCELLED);
        worker.interrupt();
        worker.join(3000);
        JclawTui.Companion.restoreStdStreams();
      }
    }
  }

  private static void runSession(
      boolean preview,
      DemoMode mode,
      ModelLineup lineup,
      Path root,
      BufferedReader input,
      LinkedBlockingQueue<Turn> queue,
      AtomicReference<String> approvalCandidate,
      JclawTui dashboard,
      TraceEvidence evidence,
      SessionDisplay display,
      CopyCommands copy)
      throws IOException, InterruptedException {
    if (preview) {
      display.reply("UI FIXTURE DATA: prepared layout rehearsal; no providers or actions");
      if (dashboard != null) {
        copy.candidate("Prepared message: I already build AI agents.");
        dashboard.candidate(
            new CandidateView(
                "ALREADY_PROFICIENT",
                Scenario.ORGANIZER,
                Scenario.EVENT_ID,
                "Prepared message: I already build AI agents.",
                "Prepared hallway script",
                "Prepared opening request",
                1,
                null));
        dashboard.reviewResult(true, "UI fixture verdict; not a real critic decision");
      }
      display.state("PROPOSAL", "UI FIXTURE DATA only");
      if (dashboard != null) {
        while (true) {
          var turn = queue.take();
          if (turn.text().trim().equals("/quit")) return;
          if (!copy.handle(turn.text(), display::chat))
            display.chat(
                "UI FIXTURE DATA: use /copy reply or /copy candidate to copy prepared text.");
        }
      }
      return;
    }
    var key = ModelProviders.googleKey(System.getenv());
    var models = ModelProviders.create(mode, lineup, System.getenv(), evidence);
    try (var mcp =
        new McpTools(
            root, env("JCLAW_MOCK_DELIVERY", "success"), line -> evidence.event("MCP", line))) {
      var history = new SentHistory(root.resolve("state/sent-history.json"));
      var memories =
          new MemoryTools(
              root.resolve(".shared/memory/documents"),
              history,
              line -> evidence.event("MEMORY", line));
      var skills =
          new SkillCatalog(root.resolve(".shared/skills"), line -> evidence.event("SKILL", line));
      var session =
          new DemoSession(
              mode,
              decider(mode, lineup, key, evidence, display),
              models.chat(),
              models.draft(),
              models.review(),
              mcp,
              history,
              memories,
              skills,
              display);
      display.state("READY", "Ask for a plan. No send happens without reviewed human approval.");
      var reportDirectory = root.resolve("state/reports").resolve(evidence.traceId());
      saveReports(session, reportDirectory, evidence, display, true);
      while (true) {
        var line = dashboard == null ? input.readLine() : null;
        var turn = dashboard == null ? new Turn(line, approvalCandidate.get()) : queue.take();
        if (turn.text() == null || turn.text().trim().equals("/quit")) break;
        if (turn.text().isBlank()) continue;
        if (copy.handle(turn.text(), display::chat)) continue;
        if (mode.round() >= 7 && turn.text().trim().equals("/report")) {
          saveReports(session, reportDirectory, evidence, display, true);
          continue;
        }
        try {
          session.submit(turn.text(), turn.candidateId());
        } catch (Delivery.Refused refused) {
          display.chat(refused.getMessage());
          if (dashboard != null) dashboard.deliveryFailed(false);
        } catch (Delivery.Unconfirmed uncertain) {
          display.chat(uncertain.getMessage());
          if (dashboard != null) dashboard.deliveryFailed(true);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
          session.block();
          display.state(
              "BLOCKED",
              "Invalid request or model result; inspect the trace and request a fresh attempt");
        } catch (LangChain4jException unavailable) {
          session.block();
          display.state(
              "BLOCKED",
              "Provider unavailable or invalid output; inspect trace and retry a fresh request. Nothing confirmed sent.");
        } catch (UncheckedIOException local) {
          session.block();
          display.state(
              "BLOCKED",
              "Local state write failed; inspect receipt and repair directory permissions before retrying");
        } finally {
          saveReports(session, reportDirectory, evidence, display, false);
        }
      }
    }
  }

  private static void saveReports(
      DemoSession session,
      Path directory,
      TraceEvidence evidence,
      SessionDisplay display,
      boolean announce) {
    try {
      var files = session.writeReports(directory);
      if (files.isEmpty()) return;
      var paths =
          files.stream().map(Path::toString).collect(java.util.stream.Collectors.joining("\n"));
      evidence.event("HTML_REPORT", paths);
      if (announce) display.reply("Native HTML reports (updated after each turn):\n" + paths);
    } catch (IOException failure) {
      evidence.event("REPORT_ERROR", failure.getClass().getSimpleName() + "; report write failed");
      display.reply(
          "HTML report write failed; check state/reports permissions. Workflow state is retained.");
    }
  }

  private static TurnDecider decider(
      DemoMode mode,
      ModelLineup lineup,
      String googleKey,
      TraceEvidence evidence,
      SessionDisplay display) {
    return (input, conversation, calendar) -> new TurnDecider.Decision(TurnDecider.Path.CHAT, null);
  }

  private static GoogleGenAiChatModel model(
      String role, String name, String key, TraceEvidence evidence) {
    return ModelProviders.gemini(role, name, key, evidence);
  }

  private static String env(String name, String fallback) {
    var value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
