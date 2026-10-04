package dev.gamov.jclaw;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelLineupTest {
  @Test
  void defaultAndBlankOverridesKeepTheProvisionalGeminiLineup() {
    var lineup =
        ModelLineup.fromEnvironment(
            Map.of("JCLAW_GEMINI_MODEL", "", "JCLAW_IDENTIFY_MODEL", " ", "JCLAW_DRAFT_MODEL", ""));
    assertEquals(
        new ModelLineup(
            "gemini-3.7-flash", "gemini-3.7-flash", "gemini-3.7-flash", "gemini-3.7-flash", "jev"),
        lineup);
  }

  @Test
  void eachRoleOverrideIsIndependentAndShownInTheProviderLegend() {
    var lineup =
        ModelLineup.fromEnvironment(
            Map.of(
                "JCLAW_GEMINI_MODEL", "shared-gemini-fixture",
                "JCLAW_IDENTIFY_MODEL", "identify-gemini-fixture",
                "JCLAW_DRAFT_MODEL", "draft-gemini-fixture",
                "JCLAW_REVIEW_MODEL", "review-gemini-fixture"));
    assertEquals("identify-gemini-fixture", lineup.identify());
    assertEquals("draft-gemini-fixture", lineup.draft());
    assertEquals("review-gemini-fixture", lineup.review());
    assertEquals(
        "identify: jev-1.13.0 Jev native | chat: shared-gemini-fixture API → draft/refine: draft-gemini-fixture API → review: review-gemini-fixture API | organizer: mock",
        lineup.providerLegend());
  }

  @Test
  void baseModelOnlyFillsRolesWithoutTheirOwnOverride() {
    var lineup =
        ModelLineup.fromEnvironment(
            Map.of(
                "JCLAW_GEMINI_MODEL",
                "shared-gemini-fixture",
                "JCLAW_REVIEW_MODEL",
                "review-gemini-fixture"));
    assertEquals("shared-gemini-fixture", lineup.identify());
    assertEquals("shared-gemini-fixture", lineup.draft());
    assertEquals("review-gemini-fixture", lineup.review());
  }
}
