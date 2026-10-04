package dev.gamov.jclaw;

import java.util.Arrays;

public enum DemoMode {
  CHATBOT(1),
  TOOLS(2),
  MEMORY(3),
  SKILLS(4),
  WORKFLOW(5),
  GUARDRAILS(6),
  OBSERVABILITY(7);
  private final int round;

  DemoMode(int round) {
    this.round = round;
  }

  public int round() {
    return round;
  }

  public static DemoMode parse(String value) {
    return Arrays.stream(values())
        .filter(mode -> mode.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Available modes: " + commands()));
  }

  public static String commands() {
    return Arrays.stream(values())
        .map(mode -> mode.name().toLowerCase(java.util.Locale.ROOT))
        .collect(java.util.stream.Collectors.joining("|"));
  }

  public static DemoMode defaultMode() {
    return Arrays.stream(values())
        .filter(mode -> mode.round == 6)
        .findFirst()
        .orElse(values()[values().length - 1]);
  }
}
