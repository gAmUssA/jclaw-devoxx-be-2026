package dev.gamov.jclaw.agent;

import dev.gamov.jclaw.domain.Contracts;
import java.util.List;

/** The decision boundary selects an application route; it has no drafting or action capability. */
public interface TurnDecider {
  enum Path {
    CHAT,
    DECLINE,
    ASK
  }

  record Message(String role, String content) {}

  record Decision(Path path, String eventId) {}

  Decision decide(String input, List<Message> conversation, List<Contracts.CalendarEvent> calendar);
}
