package dev.gamov.jclaw;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Native beta31 DecisionModel adapter for the pinned shared Jev policy/questions. */
public final class JevDecider implements TurnDecider {
  private static final com.fasterxml.jackson.databind.JsonNode POLICY =
      SharedPolicy.resource("jev/policy.json");
  public static final String MODEL = POLICY.required("model").textValue();
  private static final Set<String> SENTINELS = Set.of("NO_MATCH", "AMBIGUOUS");
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("EEEE MMMM dd yyyy HH:mm", Locale.ENGLISH);
  private final UntypedAgent identify;
  private final Consumer<List<String>> evidence;

  public JevDecider(DecisionModel model, Consumer<List<String>> evidence) {
    this(model, evidence, event -> {});
  }

  public JevDecider(
      DecisionModel model, Consumer<List<String>> evidence, Consumer<Workflow.Event> graph) {
    var evaluate =
        AgenticServices.nonAiAgentBuilder(
                scope -> model.decide((DecisionRequest) scope.readState("decisionRequest")))
            .name("jevDecision")
            .inputKey(DecisionRequest.class, "decisionRequest")
            .outputKey("decisionResponse")
            .outputType(DecisionResponse.class)
            .build();
    identify =
        AgenticServices.sequenceBuilder()
            .name("routeAndIdentify")
            .subAgents(evaluate)
            .outputKey("decisionResponse")
            .listener(new GraphEvents(graph))
            .build();
    this.evidence = evidence;
  }

  public static DecisionRequest request(
      String input, List<Message> conversation, List<Contracts.CalendarEvent> calendar) {
    Contracts.requireText(input, "decision input");
    if (calendar.stream().map(Contracts.CalendarEvent::id).distinct().count() != calendar.size())
      throw new IllegalArgumentException("Duplicate calendar IDs");
    var records = new ArrayList<Map<String, Object>>();
    var options = new LinkedHashMap<String, String>();
    for (var event : calendar) {
      Contracts.requireText(event.id(), "calendar ID");
      Contracts.requireText(event.organizer(), "canonical organizer");
      if (SENTINELS.contains(event.id()))
        throw new IllegalArgumentException("Reserved calendar ID");
      var record = new LinkedHashMap<String, Object>();
      record.put("title", event.title());
      record.put("start", event.start());
      record.put("organizer", event.organizer());
      record.put("declined", event.declined());
      record.put("dateLabel", OffsetDateTime.parse(event.start()).format(DATE));
      options.put(event.id(), Json.write(record));
      record.put("id", event.id());
      records.add(record);
    }
    options.put(
        "NO_MATCH",
        "ZERO records match ALL specified details. This includes an explicit organizer or date/weekday absent from matching records, no identified obligation, or no request for a decline plan. A matching title does not override a conflicting organizer or day.");
    options.put(
        "AMBIGUOUS",
        "Two or more records match ALL specified details equally; insufficient evidence to choose one; or multiple targets are requested.");
    var questions = SharedPolicy.resource("jev/questions.json");
    var intent =
        ChoiceQuestion.builder()
            .text(questions.required("intent").required("instructions").textValue());
    questions
        .required("intent")
        .required("criteria")
        .properties()
        .forEach(entry -> intent.option(entry.getKey(), entry.getValue().toString()));
    var event =
        ChoiceQuestion.builder()
            .text(questions.required("event").required("instructions").toString());
    options.forEach(event::option);
    return DecisionRequest.builder()
        .parameters(DecisionRequestParameters.builder().modelName(MODEL).build())
        .input(
            Map.of(
                "userMessage",
                input,
                "conversation",
                conversation.stream()
                    .map(message -> Map.of("role", message.role(), "content", message.content()))
                    .toList(),
                "calendar",
                records,
                "scenario",
                "Fictional rehearsal. Today is Friday October 2, 2026; the next Tuesday is October 6, 2026."))
        .question("intent", intent.build())
        .question("event", event.build())
        .build();
  }

  public static Decision evaluate(DecisionRequest request, DecisionResponse response) {
    if (!MODEL.equals(response.modelName()))
      throw new IllegalArgumentException("Jev returned an unvalidated model version");
    var intent = validate(request, response, "intent");
    if (intent.confidence() < POLICY.required("intentConfidenceMin").doubleValue())
      return new Decision(Path.ASK, null);
    if (intent.value().equals("CHAT")) return new Decision(Path.CHAT, null);
    var event = validate(request, response, "event");
    if (SENTINELS.contains(event.value())
        || event.confidence() < POLICY.required("eventConfidenceMin").doubleValue())
      return new Decision(Path.ASK, null);
    return new Decision(Path.DECLINE, event.value());
  }

  private static ChoiceAnswer validate(
      DecisionRequest request, DecisionResponse response, String name) {
    var answer = response.choice(name);
    var options = ((ChoiceQuestion) request.questions().get(name)).options().keySet();
    var probabilities = answer.probabilities();
    if (!options.contains(answer.value())
        || !probabilities.keySet().equals(options)
        || probabilities.values().stream()
            .anyMatch(value -> value == null || !Double.isFinite(value) || value < 0 || value > 1)
        || Math.abs(probabilities.values().stream().mapToDouble(Double::doubleValue).sum() - 1)
            > 0.02
        || answer.confidence() == null
        || !Double.isFinite(answer.confidence())
        || answer.confidence() < 0
        || answer.confidence() > 1
        || probabilities.get(answer.value()) + 0.001
            < probabilities.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow())
      throw new IllegalArgumentException("Invalid Jev " + name + " distribution/confidence");
    return answer;
  }

  @Override
  public Decision decide(
      String input, List<Message> conversation, List<Contracts.CalendarEvent> calendar) {
    var request = request(input, conversation, calendar);
    var response = (DecisionResponse) identify.invoke(Map.of("decisionRequest", request));
    var decision = evaluate(request, response);
    var lines = new ArrayList<String>();
    lines.add(
        response.modelName()
            + " / native DecisionModel / route="
            + decision.path()
            + " event="
            + decision.eventId());
    response
        .answers()
        .forEach(
            (name, value) -> {
              if (value instanceof ChoiceAnswer answer) {
                lines.add(
                    name
                        + ": "
                        + answer.value()
                        + " confidence="
                        + answer.confidence()
                        + " margin="
                        + (answer.probabilities().isEmpty() ? "absent" : answer.margin())
                        + (name.equals("event") && decision.path() == Path.CHAT
                            ? " / unused for CHAT"
                            : ""));
                answer
                    .probabilities()
                    .forEach((option, probability) -> lines.add("  " + option + "=" + probability));
              }
            });
    lines.add("Reported API usage=" + response.tokenUsage());
    evidence.accept(List.copyOf(lines));
    return decision;
  }
}
