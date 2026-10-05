package dev.gamov.jclaw.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelLineupTest {
  @Test
  void defaultAndBlankOverridesKeepTheAgreedProviderLineup() {
    var lineup =
        ModelLineup.fromEnvironment(
            Map.of("JCLAW_GEMINI_MODEL", "", "JCLAW_IDENTIFY_MODEL", " ", "JCLAW_DRAFT_MODEL", ""));
    assertEquals(
        new ModelLineup(
            "gemini-3.7-flash", "claude-opus-5-5", "gpt-6-astra", "gemini-3.7-flash", "jev"),
        lineup);
  }

  @Test
  void eachRoleOverrideIsIndependentAndShownInTheProviderLegend() {
    var lineup =
        ModelLineup.fromEnvironment(
            Map.of(
                "JCLAW_GEMINI_MODEL", "shared-gemini-fixture",
                "JCLAW_IDENTIFY_MODEL", "identify-gemini-fixture",
                "JCLAW_DRAFT_MODEL", "draft-claude-fixture",
                "JCLAW_REVIEW_MODEL", "review-openai-fixture"));
    assertEquals("identify-gemini-fixture", lineup.identify());
    assertEquals("draft-claude-fixture", lineup.draft());
    assertEquals("review-openai-fixture", lineup.review());
    assertEquals(
        "identify: jev-1.13.0 Jev native | chat: shared-gemini-fixture Gemini API → draft/refine: draft-claude-fixture Anthropic API → review: review-openai-fixture OpenAI API | organizer: mock",
        lineup.providerLegend());
  }

  @Test
  void geminiBaseModelDoesNotOverrideClaudeAndOpenAiDefaults() {
    var lineup =
        ModelLineup.fromEnvironment(
            Map.of(
                "JCLAW_GEMINI_MODEL",
                "shared-gemini-fixture",
                "JCLAW_REVIEW_MODEL",
                "review-openai-fixture"));
    assertEquals("shared-gemini-fixture", lineup.identify());
    assertEquals("shared-gemini-fixture", lineup.chat());
    assertEquals("claude-opus-5-5", lineup.draft());
    assertEquals("review-openai-fixture", lineup.review());
  }
}
