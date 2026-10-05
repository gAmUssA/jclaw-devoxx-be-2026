package dev.gamov.jclaw.app;

import dev.gamov.jclaw.agent.Workflow;

public interface SessionDisplay {
  void chat(String text);

  default void reply(String text) {
    chat(text);
  }

  void event(String kind, String text);

  void workflow(Workflow.Event event);

  void state(String state, String text);
}
