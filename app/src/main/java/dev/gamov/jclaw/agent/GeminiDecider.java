package dev.gamov.jclaw.agent;

import dev.gamov.jclaw.domain.Contracts;
import dev.gamov.jclaw.serialization.Json;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;

/** Explicit comparison mode only. Never used as a fallback from Jev failure. */
public final class GeminiDecider implements TurnDecider {
  private final AgentRoles.Router router;

  public GeminiDecider(ChatModel model) {
    router = AgenticServices.agentBuilder(AgentRoles.Router.class).chatModel(model).build();
  }

  @Override
  public Decision decide(
      String input, List<Message> conversation, List<Contracts.CalendarEvent> calendar) {
    var route = router.route(input, Json.write(conversation), Json.write(calendar));
    if (route.intent() == null)
      throw new IllegalArgumentException("Invalid Gemini comparison route");
    return new Decision(
        route.intent() == AgentRoles.Intent.DECLINE ? Path.DECLINE : Path.CHAT, route.eventId());
  }
}
