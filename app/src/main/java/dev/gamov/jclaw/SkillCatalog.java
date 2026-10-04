package dev.gamov.jclaw;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class SkillCatalog {
  public record Metadata(String name, String description) {}

  private final Path root;
  private final Consumer<String> trace;

  public SkillCatalog(Path root, Consumer<String> trace) {
    this.root = root;
    this.trace = trace;
  }

  public List<Metadata> discover() {
    if (!Files.isDirectory(root)) return List.of();
    try (var paths = Files.list(root)) {
      return paths
          .sorted()
          .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
          .filter(path -> Files.isRegularFile(path.resolve("SKILL.md"), LinkOption.NOFOLLOW_LINKS))
          .map(
              path -> {
                var text = read(path.resolve("SKILL.md"));
                var parts = text.split("---", 3);
                var description =
                    parts.length >= 3
                        ? parts[1]
                            .lines()
                            .filter(line -> line.startsWith("description:"))
                            .findFirst()
                            .map(line -> line.substring("description:".length()).trim())
                            .orElse("")
                        : "";
                return new Metadata(path.getFileName().toString(), description);
              })
          .toList();
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot discover skills; check the configured root", error);
    }
  }

  @Tool(
      "Read a relevant runtime skill before applying it. Read-only; never sends or changes files.")
  public String readSkill(@P(name = "name", description = "Catalog skill name") String name) {
    if (discover().stream().noneMatch(skill -> skill.name().equals(name)))
      throw new IllegalArgumentException("Unknown skill; use a startup catalog name");
    try {
      var resolvedRoot = root.toRealPath();
      var file = resolvedRoot.resolve(name).resolve("SKILL.md").toRealPath();
      if (!file.startsWith(resolvedRoot))
        throw new IllegalArgumentException("Skill is outside the configured read-only root");
      trace.accept("readSkill(" + name + ")");
      return read(file);
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot read skill; inspect the configured skill path", error);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot read " + file, error);
    }
  }
}
