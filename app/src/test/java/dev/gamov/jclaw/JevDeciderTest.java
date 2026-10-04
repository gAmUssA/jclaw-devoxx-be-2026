package dev.gamov.jclaw;

import static dev.gamov.jclaw.Contracts.Scenario;
import static dev.gamov.jclaw.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class JevDeciderTest {
  static final class Endpoint implements DecisionModel {
    final List<DecisionRequest> requests = new ArrayList<>();
    private final DecisionResponse response;
    private final RuntimeException failure;

    Endpoint(DecisionResponse response) {
      this.response = response;
      failure = null;
    }

    Endpoint(RuntimeException failure) {
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

  static ChoiceAnswer answer(
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

  static DecisionResponse declineResponse(
      DecisionRequest request, double intentConfidence, double eventConfidence, String event) {
    return DecisionResponse.builder()
        .modelName(JevDecider.MODEL)
        .answer("intent", answer(request, "intent", "EXCUSE_REQUEST", intentConfidence))
        .answer("event", answer(request, "event", event, eventConfidence))
        .build();
  }

  static List<Contracts.CalendarEvent> calendar() {
    try (var mcp = new McpTools(ROOT, "success", text -> {})) {
      return mcp.events();
    }
  }

  @Test
  void all48RecordedAdmittedDecisionsPreserveSharedFactsQuestionsAndPolicy() throws Exception {
    int replayed = 0;
    for (var file : List.of("development-02.json", "holdout-01.json")) {
      var report =
          Json.tree(Files.readString(ROOT.resolve(".shared/validation/jev/results/" + file)));
      for (var record : report.required("results")) {
        var payload = record.required("request");
        var state = payload.required("state");
        var conversation = new ArrayList<TurnDecider.Message>();
        state
            .required("conversation")
            .forEach(
                message ->
                    conversation.add(
                        new TurnDecider.Message(
                            message.required("role").asText(),
                            message.required("content").asText())));
        var calendar = new ArrayList<Contracts.CalendarEvent>();
        state
            .required("calendar")
            .forEach(
                event ->
                    calendar.add(
                        new Contracts.CalendarEvent(
                            event.required("id").asText(),
                            event.required("title").asText(),
                            event.required("start").asText(),
                            event.required("organizer").asText(),
                            event.required("declined").booleanValue())));
        var request =
            JevDecider.request(state.required("userMessage").asText(), conversation, calendar);
        assertEquals(state, Json.tree(Json.write(request.input())), record.required("id").asText());
        payload
            .required("questions")
            .properties()
            .forEach(
                entry -> {
                  var actual = (ChoiceQuestion) request.questions().get(entry.getKey());
                  var instructions = entry.getValue().required("instructions");
                  if (instructions.isTextual())
                    assertEquals(instructions.textValue(), actual.text());
                  else assertEquals(instructions, Json.tree(actual.text()));
                  entry
                      .getValue()
                      .required("criteria")
                      .properties()
                      .forEach(
                          option -> {
                            if (option.getValue().isTextual())
                              assertEquals(
                                  option.getValue().textValue(),
                                  actual.options().get(option.getKey()));
                            else
                              assertEquals(
                                  option.getValue(),
                                  Json.tree(actual.options().get(option.getKey())));
                          });
                });
        var response = recorded(record.required("response"));
        var endpoint = new Endpoint(response);
        var lines = new ArrayList<List<String>>();
        var decision =
            new JevDecider(endpoint, lines::add)
                .decide(state.required("userMessage").asText(), conversation, calendar);
        assertEquals(record.required("expected").required("path").asText(), decision.path().name());
        assertEquals(
            record.required("expected").path("eventId").isNull()
                ? null
                : record.required("expected").path("eventId").textValue(),
            decision.eventId());
        assertEquals(request, endpoint.requests.getFirst());
        assertTrue(lines.getFirst().toString().contains("confidence="));
        replayed++;
      }
    }
    assertEquals(48, replayed);
  }

  private static DecisionResponse recorded(JsonNode raw) {
    var result = DecisionResponse.builder().modelName(raw.required("model").asText());
    raw.required("answers")
        .properties()
        .forEach(
            entry -> {
              var value = entry.getValue();
              var probabilities = new LinkedHashMap<String, Double>();
              value
                  .required("probabilities")
                  .properties()
                  .forEach(
                      option ->
                          probabilities.put(option.getKey(), option.getValue().doubleValue()));
              result.answer(
                  entry.getKey(),
                  ChoiceAnswer.builder()
                      .value(value.required("choice").asText())
                      .probabilities(probabilities)
                      .confidence(value.required("confidence").doubleValue())
                      .build());
            });
    return result.build();
  }

  @Test
  void pinnedConfidenceFloorsAndSentinelsAskWithoutSelectingANearMatch() {
    var request = JevDecider.request("Decline one event", List.of(), calendar());
    assertEquals(
        TurnDecider.Path.ASK,
        JevDecider.evaluate(request, declineResponse(request, .599, 1, Scenario.EVENT_ID)).path());
    assertEquals(
        TurnDecider.Path.ASK,
        JevDecider.evaluate(request, declineResponse(request, 1, .599, Scenario.EVENT_ID)).path());
    assertEquals(
        TurnDecider.Path.DECLINE,
        JevDecider.evaluate(request, declineResponse(request, .6, .6, Scenario.EVENT_ID)).path());
    for (var sentinel : List.of("NO_MATCH", "AMBIGUOUS"))
      assertEquals(
          TurnDecider.Path.ASK,
          JevDecider.evaluate(request, declineResponse(request, 1, 1, sentinel)).path());
  }

  @Test
  void wrongModelMissingAnswerWrongTypeOrIncompleteDistributionCannotRoute() {
    var request = JevDecider.request("Decline", List.of(), calendar());
    var valid = declineResponse(request, 1, 1, Scenario.EVENT_ID);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            JevDecider.evaluate(
                request,
                DecisionResponse.builder()
                    .modelName("jev-latest")
                    .answers(valid.answers())
                    .build()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            JevDecider.evaluate(
                request,
                DecisionResponse.builder()
                    .modelName(JevDecider.MODEL)
                    .answer("intent", valid.choice("intent"))
                    .build()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            JevDecider.evaluate(
                request,
                DecisionResponse.builder()
                    .modelName(JevDecider.MODEL)
                    .answer("intent", YesNoAnswer.builder().probability(.9).build())
                    .build()));
    var incomplete =
        ChoiceAnswer.builder()
            .value(Scenario.EVENT_ID)
            .confidence(.9)
            .probability(Scenario.EVENT_ID, 1.0)
            .build();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            JevDecider.evaluate(
                request,
                DecisionResponse.builder()
                    .modelName(JevDecider.MODEL)
                    .answer("intent", valid.choice("intent"))
                    .answer("event", incomplete)
                    .build()));
  }

  @Test
  void chatIgnoresSpeculativeEventAndHasNoDeclineTarget() {
    var request = JevDecider.request("Rewrite this text", List.of(), calendar());
    var response =
        DecisionResponse.builder()
            .modelName(JevDecider.MODEL)
            .answer("intent", answer(request, "intent", "CHAT", 1))
            .answer("event", answer(request, "event", Scenario.EVENT_ID, 1))
            .build();
    var lines = new ArrayList<List<String>>();
    assertEquals(
        new TurnDecider.Decision(TurnDecider.Path.CHAT, null),
        new JevDecider(new Endpoint(response), lines::add)
            .decide("Rewrite this text", List.of(), calendar()));
    assertTrue(lines.getFirst().toString().contains("unused for CHAT"));
  }

  @Test
  void duplicateOrReservedCalendarIdsFailBeforeNetwork() {
    var event = calendar().getFirst();
    assertThrows(
        IllegalArgumentException.class,
        () -> JevDecider.request("decline", List.of(), List.of(event, event)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            JevDecider.request(
                "decline",
                List.of(),
                List.of(
                    new Contracts.CalendarEvent(
                        "NO_MATCH", event.title(), event.start(), event.organizer(), false))));
  }
}
