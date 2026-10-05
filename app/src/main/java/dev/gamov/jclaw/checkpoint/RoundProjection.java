package dev.gamov.jclaw.checkpoint;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/** Java 21 source launcher. Removes application entry paths from a complete, frozen baseline. */
public final class RoundProjection {
  private static final String PACKAGE = "app/src/main/java/dev/gamov/jclaw/app/";
  private static final Pattern BEGIN = Pattern.compile("\\s*// checkpoint:begin (\\w+) ([1-7])");
  private static final Pattern END = Pattern.compile("\\s*// checkpoint:end (\\w+)");
  private static final Pattern MODE = Pattern.compile("\\s*([A-Z]+)\\(([1-7])\\)[,;]");

  private record Region(String name, int minimum, String opening, StringBuilder body) {}

  public static void main(String[] args) throws Exception {
    if (args.length != 3)
      throw new IllegalArgumentException("Usage: RoundProjection.java round root baselineCommit");
    int round = Integer.parseInt(args[0]);
    if (round < 1 || round > 7) throw new IllegalArgumentException("Round must be 1 through 7");
    if (!args[2].matches("[0-9a-f]{40}"))
      throw new IllegalArgumentException("Expected a full baseline commit");
    var root = Path.of(args[1]).toRealPath();
    var files = new LinkedHashMap<Path, String>();
    files.put(
        root.resolve(PACKAGE + "DemoMode.java"),
        modes(Files.readString(root.resolve(PACKAGE + "DemoMode.java")), round));
    files.put(
        root.resolve(PACKAGE + "Main.java"),
        project(Files.readString(root.resolve(PACKAGE + "Main.java")), round, Set.of("decider")));
    files.put(
        root.resolve(PACKAGE + "DemoSession.java"),
        project(
            Files.readString(root.resolve(PACKAGE + "DemoSession.java")),
            round,
            Set.of(
                "tools",
                "memory",
                "skills",
                "conversation",
                "workflow",
                "humanInput",
                "declineTurn",
                "reviewedCandidate",
                "humanPrompt",
                "send")));
    // Validate every input before mutating any file. Runtime state is never read or written.
    for (var file : files.entrySet()) Files.writeString(file.getKey(), file.getValue());
    if (round < 6) {
      Files.delete(root.resolve("app/src/test/java/dev/gamov/jclaw/app/DemoSessionTest.java"));
      Files.delete(root.resolve("app/src/test/java/dev/gamov/jclaw/app/JevSessionTest.java"));
    }
    Files.writeString(
        root.resolve(".round-checkpoint"), "baseline=" + args[2] + "\nround=" + round + "\n");
  }

  private static String modes(String source, int round) {
    String defaultDeclaration = "private static final int DEFAULT_ROUND = 6;";
    if (source.indexOf(defaultDeclaration) < 0
        || source.indexOf(defaultDeclaration) != source.lastIndexOf(defaultDeclaration))
      throw new IllegalArgumentException("Projection requires one complete-build default");
    source =
        source.replace(
            defaultDeclaration, "private static final int DEFAULT_ROUND = " + round + ";");
    var result = new StringBuilder();
    var seen = new HashSet<Integer>();
    for (var line : source.lines().toList()) {
      var match = MODE.matcher(line);
      if (match.matches()) {
        int number = Integer.parseInt(match.group(2));
        if (!seen.add(number)) throw new IllegalArgumentException("Duplicate round mode");
        if (number <= round)
          result
              .append("  ")
              .append(match.group(1))
              .append('(')
              .append(number)
              .append(number == round ? ");\n" : "),\n");
      } else result.append(line).append('\n');
    }
    if (!seen.equals(Set.of(1, 2, 3, 4, 5, 6, 7)))
      throw new IllegalArgumentException("Projection requires the complete seven-round baseline");
    return result.toString();
  }

  private static String project(String source, int round, Set<String> expected) {
    var stack = new ArrayDeque<Region>();
    stack.push(new Region("root", 1, "", new StringBuilder()));
    var seen = new HashSet<String>();
    for (var line : source.lines().toList()) {
      var begin = BEGIN.matcher(line);
      var end = END.matcher(line);
      if (begin.matches()) {
        if (!seen.add(begin.group(1)))
          throw new IllegalArgumentException("Duplicate projection region " + begin.group(1));
        stack.push(
            new Region(
                begin.group(1), Integer.parseInt(begin.group(2)), line, new StringBuilder()));
      } else if (end.matches()) {
        if (stack.size() == 1 || !stack.peek().name().equals(end.group(1)))
          throw new IllegalArgumentException("Unbalanced projection marker " + line);
        var region = stack.pop();
        if (region.minimum() <= round)
          stack
              .peek()
              .body()
              .append(region.opening())
              .append('\n')
              .append(region.body())
              .append(line)
              .append('\n');
        else
          stack
              .peek()
              .body()
              .append(
                  switch (region.name()) {
                    case "workflow" -> "    workflow = null;\n";
                    case "decider" ->
                        "    return (input, conversation, calendar) -> new TurnDecider.Decision(TurnDecider.Path.CHAT, null);\n";
                    default -> "";
                  });
      } else stack.peek().body().append(line).append('\n');
    }
    if (stack.size() != 1 || !seen.equals(expected))
      throw new IllegalArgumentException("Missing or unexpected checkpoint regions: " + seen);
    return stack.pop().body().toString();
  }
}
