package dev.gamov.jclaw;

import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Each native root needs its own monitor so its topology matches its executions. */
final class WorkflowReports {
  private final AgentMonitor review = new ReportMonitor();
  private final AgentMonitor human = new ReportMonitor();

  AgentMonitor review() {
    return review;
  }

  AgentMonitor human() {
    return human;
  }

  List<Path> write(Path directory) throws IOException {
    Files.createDirectories(directory);
    var reviewFile = directory.resolve("review-loop.html");
    var humanFile = directory.resolve("human-review.html");
    Files.writeString(reviewFile, HtmlReportGenerator.generateReport(review));
    Files.writeString(humanFile, HtmlReportGenerator.generateReport(human));
    return List.of(reviewFile, humanFile);
  }

  /** Provider exception bodies can contain credentials; keep the failure without its raw body. */
  private static final class ReportMonitor extends AgentMonitor {
    @Override
    public void onAgentInvocationError(AgentInvocationError error) {
      super.onAgentInvocationError(
          new AgentInvocationError(
              error.agenticScope(),
              error.agent(),
              error.inputs(),
              new IllegalStateException(
                  error.error().getClass().getSimpleName()
                      + ": agent invocation failed; provider details omitted")));
    }
  }
}
