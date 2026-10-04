package dev.gamov.jclaw;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.decision.listener.DecisionModelErrorContext;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class TraceEvidence {
  private final Path file;
  private final Consumer<String> display;
  private final LongSupplier nanoTime;
  private final String traceId = UUID.randomUUID().toString();
  private final ThreadLocal<String> node = ThreadLocal.withInitial(() -> "session");
  private long sequence;

  private record Node(String agentId, String spanId, String parent) {}

  private final ThreadLocal<Deque<Node>> graphStack = ThreadLocal.withInitial(ArrayDeque::new);

  public TraceEvidence(Path file, Consumer<String> display) {
    this(file, display, System::nanoTime);
  }

  TraceEvidence(Path file, Consumer<String> display, LongSupplier nanoTime) {
    this.file = file;
    this.display = display;
    this.nanoTime = nanoTime;
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot create trace directory", error);
    }
  }

  public synchronized void event(String kind, String text) {
    if (kind.equals("STAGE")) {
      var stage = Json.read(text, Workflow.Stage.class);
      if (stage.phase() == Workflow.Phase.STARTED) node.set(stage.name() + "/" + stage.attempt());
    }
    write(kind, text, node.get());
  }

  public synchronized void graph(Workflow.Graph graph) {
    var stack = graphStack.get();
    if (graph.phase() == Workflow.Phase.STARTED) {
      var observation = new Node(graph.agentId(), UUID.randomUUID().toString(), node.get());
      write(
          "AGENTIC_GRAPH",
          "span=" + observation.spanId() + " " + Json.write(graph),
          observation.parent());
      stack.push(observation);
      node.set(observation.spanId());
    } else if (!stack.isEmpty() && stack.peek().agentId().equals(graph.agentId())) {
      var observation = stack.pop();
      write(
          "AGENTIC_GRAPH",
          "span=" + observation.spanId() + " " + Json.write(graph),
          observation.parent());
      node.set(observation.parent());
    } else {
      write("AGENTIC_GRAPH", "unpaired native callback " + Json.write(graph), node.get());
    }
  }

  private synchronized void write(String kind, String text, String parent) {
    try {
      Files.writeString(
          file,
          Json.write(
                  Map.of(
                      "traceId",
                      traceId,
                      "timestamp",
                      Instant.now().toString(),
                      "sequence",
                      ++sequence,
                      "parent",
                      parent,
                      "kind",
                      kind,
                      "text",
                      text))
              + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot write trace evidence", error);
    }
    display.accept(kind + " " + text);
  }

  public DecisionModelListener decisionListener() {
    return new DecisionModelListener() {
      private final Object startedKey = new Object();
      private final Object parentKey = new Object();
      private final Object observationKey = new Object();

      @Override
      public void onRequest(DecisionModelRequestContext context) {
        context.attributes().put(startedKey, nanoTime.getAsLong());
        context.attributes().put(parentKey, node.get());
        context.attributes().put(observationKey, UUID.randomUUID().toString());
        write(
            "DECISION_INPUT",
            "observation="
                + context.attributes().get(observationKey)
                + " requested="
                + context.decisionRequest().modelName()
                + " state="
                + Json.write(context.decisionRequest().input())
                + " questions="
                + context.decisionRequest().questions(),
            node.get());
      }

      @Override
      public void onResponse(DecisionModelResponseContext context) {
        write(
            "DECISION_OUTPUT",
            "observation="
                + context.attributes().get(observationKey)
                + " durationMs="
                + elapsed(context.attributes().get(startedKey))
                + " requested="
                + context.decisionRequest().modelName()
                + " returned="
                + context.decisionResponse().modelName()
                + " answers="
                + context.decisionResponse().answers()
                + " usage="
                + context.decisionResponse().tokenUsage(),
            (String) context.attributes().get(parentKey));
      }

      @Override
      public void onError(DecisionModelErrorContext context) {
        write(
            "DECISION_ERROR",
            "observation="
                + context.attributes().get(observationKey)
                + " durationMs="
                + elapsed(context.attributes().get(startedKey))
                + " "
                + context.error().getClass().getSimpleName()
                + "; stopped before Draft; no model fallback",
            (String) context.attributes().get(parentKey));
      }
    };
  }

  private Long elapsed(Object started) {
    return started instanceof Long value ? (nanoTime.getAsLong() - value) / 1_000_000 : null;
  }

  public ChatModelListener modelListener(String role, String model) {
    return new ChatModelListener() {
      private final Object key = new Object();

      @Override
      public void onRequest(ChatModelRequestContext context) {
        context.attributes().put(key, nanoTime.getAsLong());
        event(
            "MODEL_INPUT",
            role + " / " + model + " (Gemini API) " + context.chatRequest().messages());
      }

      @Override
      public void onResponse(ChatModelResponseContext context) {
        var started = context.attributes().get(key);
        var elapsed =
            started instanceof Long value ? (nanoTime.getAsLong() - value) / 1_000_000 : null;
        event(
            "MODEL_OUTPUT",
            role
                + " / "
                + model
                + " durationMs="
                + elapsed
                + " "
                + context.chatResponse().aiMessage()
                + " usage="
                + context.chatResponse().tokenUsage());
      }

      @Override
      public void onError(ChatModelErrorContext context) {
        var started = context.attributes().get(key);
        var elapsed =
            started instanceof Long value ? (nanoTime.getAsLong() - value) / 1_000_000 : null;
        // Error bodies may echo credentials. Expose only the class and a safe remedy.
        event(
            "MODEL_ERROR",
            role
                + " / "
                + model
                + " durationMs="
                + elapsed
                + " "
                + context.error().getClass().getSimpleName()
                + "; check provider access before retrying");
      }
    };
  }
}
