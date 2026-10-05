package dev.gamov.jclaw.tools;

import static dev.gamov.jclaw.domain.Contracts.*;
import static dev.gamov.jclaw.testing.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.gamov.jclaw.memory.SentHistory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeToolsTest {
  @TempDir Path root;

  @Test
  void metadataDiscoveryAndScopedReadKeepBodyApplicationExplicit() throws IOException {
    var skill = Files.createDirectory(root.resolve("corporate-speak"));
    Files.writeString(
        skill.resolve("SKILL.md"),
        "---\nname: corporate-speak\ndescription: Rewrite precisely\n---\nDefaults to eleven");
    var trace = new ArrayList<String>();
    var catalog = new SkillCatalog(root, trace::add);
    assertEquals("Rewrite precisely", catalog.discover().getFirst().description());
    assertTrue(trace.isEmpty());
    assertTrue(catalog.readSkill("corporate-speak").contains("eleven"));
    assertEquals(List.of("readSkill(corporate-speak)"), trace);
    assertThrows(IllegalArgumentException.class, () -> catalog.readSkill("../outside"));
    assertThrows(
        IllegalArgumentException.class, () -> catalog.readSkill(root.toAbsolutePath().toString()));
  }

  @Test
  void symlinkCannotEscapeSkillRoot() throws IOException {
    var outside = Files.createDirectory(root.resolve("outside"));
    Files.writeString(outside.resolve("SKILL.md"), "---\ndescription: Outside\n---\nSecret");
    var skills = Files.createDirectory(root.resolve("skills"));
    Files.createSymbolicLink(skills.resolve("linked"), outside);
    assertThrows(
        IllegalArgumentException.class,
        () -> new SkillCatalog(skills, text -> {}).readSkill("linked"));
  }

  @Test
  void committedFixturesSupplyReasonsAbsentFromCalendar() {
    var memory =
        new MemoryTools(
            ROOT.resolve(".shared/memory/documents"),
            new SentHistory(root.resolve("history.json")),
            text -> {});
    assertEquals(
        new HashSet<>(Scenario.BURNED), new HashSet<>(memory.usedFlavors(Scenario.ORGANIZER)));
    assertTrue(
        memory.recallSentHistory(Scenario.ORGANIZER).contains("promised to catch the recording"));
    assertTrue(memory.usedFlavors("Different organizer").isEmpty());
  }
}
