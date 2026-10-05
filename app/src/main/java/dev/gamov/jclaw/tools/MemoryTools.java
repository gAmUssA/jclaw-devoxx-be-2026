package dev.gamov.jclaw.tools;

import static dev.gamov.jclaw.domain.Contracts.*;

import dev.gamov.jclaw.memory.SentHistory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MemoryTools {
  private final Path documents;
  private final SentHistory history;
  private final Consumer<String> trace;

  public MemoryTools(Path documents, SentHistory history, Consumer<String> trace) {
    this.documents = documents;
    this.history = history;
    this.trace = trace;
  }

  private List<String> priorFor(String organizerName) {
    return prior().stream()
        .filter(
            text ->
                text.toLowerCase(java.util.Locale.ROOT)
                    .contains(organizerName.toLowerCase(java.util.Locale.ROOT)))
        .toList();
  }

  private List<String> prior() {
    try (var paths = Files.list(documents)) {
      return paths
          .sorted()
          .filter(Files::isRegularFile)
          .map(
              path -> {
                try {
                  return Files.readString(path);
                } catch (IOException error) {
                  throw new UncheckedIOException(error);
                }
              })
          .toList();
    } catch (IOException error) {
      throw new UncheckedIOException(
          "Cannot read prior-decline fixtures; run scripts/shared.sh", error);
    }
  }

  @Tool("Read durable literal sent history and committed prior-decline documents for an organizer")
  public String recallSentHistory(
      @P(name = "organizerName", description = "Organizer name from the calendar")
          String organizerName) {
    requireText(organizerName, "organizerName");
    trace.accept("MEMORY_READ organizer=" + organizerName);
    var stories = new ArrayList<>(priorFor(organizerName));
    history.read().stream()
        .filter(record -> record.organizerName().equals(organizerName))
        .forEach(
            record ->
                stories.add(
                    record.deliveredAt()
                        + ": Declined "
                        + record.eventId()
                        + ", run by "
                        + record.organizerName()
                        + ". Excuse flavor used: "
                        + record.flavor()
                        + ". Literal sent message: "
                        + record.message()));
    return String.join("\n", stories);
  }

  public List<ExcuseFlavor> usedFlavors(String organizerName) {
    requireText(organizerName, "organizerName");
    trace.accept("MEMORY_READ organizer=" + organizerName);
    var stories = String.join("\n", priorFor(organizerName));
    var persisted =
        history.read().stream()
            .filter(record -> record.organizerName().equals(organizerName))
            .map(SentHistory.SentRecord::flavor)
            .toList();
    return java.util.Arrays.stream(ExcuseFlavor.values())
        .filter(
            flavor ->
                persisted.contains(flavor)
                    || stories.contains("Excuse flavor used: " + flavor.name() + "."))
        .toList();
  }
}
