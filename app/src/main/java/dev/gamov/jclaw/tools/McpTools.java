package dev.gamov.jclaw.tools;

import static dev.gamov.jclaw.domain.Contracts.*;

import dev.gamov.jclaw.domain.Delivery;
import dev.gamov.jclaw.serialization.Json;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpApplicationErrorException;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Application-only send capability. Register ReadOnly with agents instead. */
public final class McpTools implements AutoCloseable {
  public static final class ReadOnly {
    private final McpTools mcp;

    public ReadOnly(McpTools mcp) {
      this.mcp = mcp;
    }

    @Tool("Read the calendar. Past declines have no reasons; retrieve memory for those.")
    public String getCalendar() {
      return mcp.calendarJson("model read-only tool");
    }

    @Tool("Read the exact organizer's sensitivity before choosing wording.")
    public String getOrganizerSensitivity(
        @dev.langchain4j.agent.tool.P(name = "name", description = "Exact organizer name")
            String name) {
      return mcp.sensitivity(name, "model read-only tool");
    }
  }

  private DefaultMcpClient calendar;
  private DefaultMcpClient organizer;
  private final Path root;
  private final String deliveryMode;
  private final Consumer<String> trace;

  public McpTools(Path root, String deliveryMode, Consumer<String> trace) {
    this.root = root;
    this.deliveryMode = deliveryMode;
    this.trace = trace;
  }

  private synchronized DefaultMcpClient calendarClient() {
    if (calendar == null) calendar = boot(root, "calendar-mcp", Map.of());
    return calendar;
  }

  private synchronized DefaultMcpClient organizerClient() {
    if (organizer == null)
      organizer = boot(root, "organizer-mcp", Map.of("JCLAW_MOCK_DELIVERY", deliveryMode));
    return organizer;
  }

  private DefaultMcpClient boot(Path root, String server, Map<String, String> environment) {
    var jar = root.resolve(".shared/mocks/build/libs/" + server + ".jar").toAbsolutePath();
    if (!Files.isRegularFile(jar))
      throw new IllegalArgumentException("Missing " + jar + "; run ./gradlew :mocks:mcpJars");
    var transport =
        StdioMcpTransport.builder()
            .command(
                List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-jar",
                    jar.toString()))
            .environment(environment)
            .logEvents(false)
            .build();
    return DefaultMcpClient.builder()
        .key(server)
        .transport(transport)
        .initializationTimeout(Duration.ofSeconds(20))
        .toolExecutionTimeout(Duration.ofSeconds(20))
        .toolResultConverter(
            (content, error) -> {
              boolean unambiguous =
                  content.size() == 1
                      && "text".equals(content.getFirst().get("type"))
                      && content.getFirst().get("text") instanceof String;
              return ToolExecutionResult.builder()
                  .isError(error || !unambiguous)
                  .resultText(unambiguous ? (String) content.getFirst().get("text") : "")
                  .build();
            })
        .build();
  }

  public String calendarJson() {
    return calendarJson("application operation");
  }

  private String calendarJson(String owner) {
    trace.accept("MCP getCalendar INPUT actor=" + owner + " {}");
    var result =
        calendarClient()
            .executeTool(
                ToolExecutionRequest.builder().name("getCalendar").arguments("{}").build());
    if (result.isError())
      throw new IllegalStateException("Calendar tool failed; inspect trace before retrying");
    trace.accept("MCP getCalendar OUTPUT " + result.resultText());
    return result.resultText();
  }

  public List<CalendarEvent> events() {
    return List.copyOf(Arrays.asList(Json.read(calendarJson(), CalendarEvent[].class)));
  }

  public String sensitivity(String name) {
    return sensitivity(name, "application operation");
  }

  private String sensitivity(String name, String owner) {
    requireText(name, "organizer name");
    var arguments = Json.write(Map.of("name", name));
    trace.accept("MCP getOrganizerSensitivity INPUT actor=" + owner + " " + arguments);
    var result =
        organizerClient()
            .executeTool(
                ToolExecutionRequest.builder()
                    .name("getOrganizerSensitivity")
                    .arguments(arguments)
                    .build());
    if (result.isError())
      throw new IllegalStateException("Organizer read failed; no draft may start");
    trace.accept("MCP getOrganizerSensitivity OUTPUT " + result.resultText());
    return result.resultText();
  }

  public DeclineReceipt send(DeclineSend envelope) {
    var arguments =
        Json.write(
            Map.of(
                "eventId",
                envelope.eventId(),
                "organizerName",
                envelope.organizerName(),
                "message",
                envelope.message(),
                "callId",
                envelope.callId(),
                "candidateId",
                envelope.candidateId()));
    trace.accept("MCP sendDecline INPUT actor=application operation " + arguments);
    final ToolExecutionResult result;
    try {
      result =
          organizerClient()
              .executeTool(
                  ToolExecutionRequest.builder().name("sendDecline").arguments(arguments).build());
    } catch (McpApplicationErrorException error) {
      throw new Delivery.Unconfirmed("Organizer tool error; check before retrying");
    } catch (LangChain4jException error) {
      throw new Delivery.Unconfirmed("Organizer transport failed; check before retrying");
    }
    trace.accept("MCP sendDecline OUTPUT error=" + result.isError() + " " + result.resultText());
    return Delivery.confirm(result.resultText(), result.isError(), envelope);
  }

  private static void closeClient(DefaultMcpClient client) {
    client.close();
  }

  @Override
  public synchronized void close() {
    try {
      if (organizer != null) closeClient(organizer);
    } finally {
      if (calendar != null) closeClient(calendar);
    }
  }
}
