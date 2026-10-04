package dev.gamov.jclaw;

import static dev.gamov.jclaw.Contracts.requireText;

import java.util.Map;

/** Jev decisions, Gemini chat, Claude drafting and an OpenAI model critic. */
public record ModelLineup(
    String identify, String draft, String review, String chat, String decider) {
  public static final String DEFAULT_MODEL = "gemini-3.7-flash";
  public static final String DEFAULT_DRAFT_MODEL = "claude-opus-5-5";
  public static final String DEFAULT_REVIEW_MODEL = "gpt-6-astra";

  public ModelLineup {
    requireText(identify, "identification model");
    requireText(draft, "draft/refinement model");
    requireText(review, "review model");
    requireText(chat, "chat model");
    if (!decider.equals("jev") && !decider.equals("gemini"))
      throw new IllegalArgumentException("JCLAW_DECIDER must be jev or gemini");
  }

  public static ModelLineup fromEnvironment(Map<String, String> environment) {
    var fallback = value(environment, "JCLAW_GEMINI_MODEL", DEFAULT_MODEL);
    return new ModelLineup(
        value(environment, "JCLAW_IDENTIFY_MODEL", fallback),
        value(environment, "JCLAW_DRAFT_MODEL", DEFAULT_DRAFT_MODEL),
        value(environment, "JCLAW_REVIEW_MODEL", DEFAULT_REVIEW_MODEL),
        value(environment, "JCLAW_CHAT_MODEL", fallback),
        value(environment, "JCLAW_DECIDER", "jev"));
  }

  public String providerLegend() {
    return "identify: "
        + (decider.equals("jev")
            ? JevDecider.MODEL + " Jev native"
            : identify + " Gemini comparison")
        + " | chat: "
        + chat
        + " Gemini API → draft/refine: "
        + draft
        + " Anthropic API → review: "
        + review
        + " OpenAI API | organizer: mock";
  }

  private static String value(Map<String, String> environment, String name, String fallback) {
    var value = environment.get(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
