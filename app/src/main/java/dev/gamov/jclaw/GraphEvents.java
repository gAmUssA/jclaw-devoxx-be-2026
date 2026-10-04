package dev.gamov.jclaw;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import java.util.function.Consumer;

/** Actual native invocation callbacks; no predicted or synthetic graph edges. */
final class GraphEvents implements AgentListener {
  private final Consumer<Workflow.Event> events;

  GraphEvents(Consumer<Workflow.Event> events) {
    this.events = events;
  }

  @Override
  public boolean inheritedBySubagents() {
    return true;
  }

  @Override
  public void beforeAgentInvocation(AgentRequest request) {
    events.accept(
        new Workflow.Graph(request.agentName(), request.agentId(), Workflow.Phase.STARTED));
  }

  @Override
  public void afterAgentInvocation(AgentResponse response) {
    events.accept(
        new Workflow.Graph(response.agentName(), response.agentId(), Workflow.Phase.COMPLETED));
  }

  @Override
  public void onAgentInvocationError(AgentInvocationError error) {
    events.accept(new Workflow.Graph(error.agentName(), error.agentId(), Workflow.Phase.FAILED));
  }
}
