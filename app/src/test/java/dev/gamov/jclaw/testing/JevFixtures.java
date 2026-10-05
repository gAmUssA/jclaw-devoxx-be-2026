package dev.gamov.jclaw.testing;

import static dev.gamov.jclaw.testing.TestSupport.*;

import dev.gamov.jclaw.agent.JevDecider;
import dev.gamov.jclaw.domain.Contracts;
import dev.gamov.jclaw.tools.McpTools;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Shared prepared responses at the paid decision-model boundary. */
public final class JevFixtures {
  private JevFixtures() {}

  public static final class Endpoint implements DecisionModel {
    public final List<DecisionRequest> requests = new ArrayList<>();
    private final DecisionResponse response;
    private final RuntimeException failure;

    public Endpoint(DecisionResponse response) {
      this.response = response;
      failure = null;
    }

    public Endpoint(RuntimeException failure) {
      this.failure = failure;
      response = null;
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
      requests.add(request);
      if (failure != null) throw failure;
      return response;
    }
  }

  public static ChoiceAnswer answer(
      DecisionRequest request, String name, String choice, double confidence) {
    var probabilities = new LinkedHashMap<String, Double>();
    ((ChoiceQuestion) request.questions().get(name))
        .options()
        .keySet()
        .forEach(option -> probabilities.put(option, option.equals(choice) ? 1.0 : 0.0));
    return ChoiceAnswer.builder()
        .value(choice)
        .probabilities(probabilities)
        .confidence(confidence)
        .build();
  }

  public static DecisionResponse declineResponse(
      DecisionRequest request, double intentConfidence, double eventConfidence, String event) {
    return DecisionResponse.builder()
        .modelName(JevDecider.MODEL)
        .answer("intent", answer(request, "intent", "EXCUSE_REQUEST", intentConfidence))
        .answer("event", answer(request, "event", event, eventConfidence))
        .build();
  }

  public static List<Contracts.CalendarEvent> calendar() {
    try (var mcp = new McpTools(ROOT, "success", text -> {})) {
      return mcp.events();
    }
  }
}
