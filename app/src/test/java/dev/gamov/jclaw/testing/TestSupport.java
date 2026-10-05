package dev.gamov.jclaw.testing;

import static dev.gamov.jclaw.domain.Contracts.*;

import dev.gamov.jclaw.agent.Workflow;
import dev.gamov.jclaw.app.SessionDisplay;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TestSupport {
  private TestSupport() {}

  public static final Path ROOT = Path.of(System.getProperty("jclaw.root"));
  public static final DeclineRequest REQUEST =
      new DeclineRequest(
          Scenario.EVENT_ID,
          Scenario.BURNED,
          Scenario.ATTENDEES,
          Scenario.ORGANIZER,
          "No fabricated facts",
          List.of());
  public static final DeclineDeployment PLAN =
      new DeclineDeployment(
          ExcuseFlavor.ALREADY_PROFICIENT,
          null,
          "Literal reviewed proficiency message",
          "Explain practical experience");
  public static final DeclineCritique APPROVAL =
      new DeclineCritique(PlausibilityTier.AIRTIGHT, true, "Reviewed this exact candidate");
  public static final String ROUTE =
      "{\"intent\":\"DECLINE\",\"eventId\":\"" + Scenario.EVENT_ID + "\"}";
  public static final String REJECTION =
      "{\"tier\":\"THIN\",\"approved\":false,\"feedback\":\"Be more precise\"}";

  /**
   * Paid network boundary fixture; real AgenticServices, parsing, tools and safety logic execute.
   */
  public static final class ModelEndpoint implements ChatModel {
    public final List<ChatRequest> requests = new ArrayList<>();
    private final ArrayDeque<AiMessage> replies;
    private final RuntimeException failure;
    private List<ChatModelListener> listeners = List.of();

    public ModelEndpoint(String... responses) {
      this(Arrays.stream(responses).map(AiMessage::from).toArray(AiMessage[]::new));
    }

    public ModelEndpoint(AiMessage... responses) {
      replies = new ArrayDeque<>(Arrays.asList(responses));
      failure = null;
    }

    public ModelEndpoint(RuntimeException failure) {
      replies = new ArrayDeque<>();
      this.failure = failure;
    }

    public ModelEndpoint(ChatModelListener listener, String... responses) {
      this(responses);
      listeners = List.of(listener);
    }

    public ModelEndpoint(ChatModelListener listener, RuntimeException failure) {
      this(failure);
      listeners = List.of(listener);
    }

    @Override
    public List<ChatModelListener> listeners() {
      return listeners;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
      requests.add(request);
      if (failure != null) throw failure;
      return ChatResponse.builder().aiMessage(replies.removeFirst()).build();
    }
  }

  public static final class Display implements SessionDisplay {
    public final List<String> states = new ArrayList<>();
    public final List<String> kinds = new ArrayList<>();
    public final List<String> chat = new ArrayList<>();
    public final List<Workflow.Verdict> reviews = new ArrayList<>();
    public final List<Workflow.Stage> stages = new ArrayList<>();

    @Override
    public void chat(String text) {
      chat.add(text);
    }

    @Override
    public void event(String kind, String text) {
      kinds.add(kind);
    }

    @Override
    public void state(String state, String text) {
      states.add(state);
    }

    @Override
    public void workflow(Workflow.Event event) {
      if (event instanceof Workflow.Verdict verdict) reviews.add(verdict);
      if (event instanceof Workflow.Stage stage) stages.add(stage);
    }
  }
}
