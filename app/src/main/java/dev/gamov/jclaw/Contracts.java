package dev.gamov.jclaw;

import java.util.List;
import java.util.Objects;

/** Java wire counterparts of the authoritative shared domain serializers. */
public final class Contracts {
  private Contracts() {}

  public enum ExcuseFlavor {
    CALENDAR_CONFLICT,
    FAMILY_OBLIGATION,
    CUSTOMER_ESCALATION,
    DEADLINE,
    ALREADY_PROFICIENT,
    EXISTENTIAL_CRISIS
  }

  public enum PlausibilityTier {
    AIRTIGHT,
    CREDIBLE,
    THIN,
    HR_WILL_NOTICE
  }

  public record DeclineRequest(
      String eventId,
      List<ExcuseFlavor> recentlyUsedFlavors,
      List<String> knownAttendees,
      String organizerName,
      String userInstruction,
      List<ExcuseFlavor> previouslyProposedFlavors) {
    public DeclineRequest {
      requireText(eventId, "eventId");
      requireText(organizerName, "organizerName");
      recentlyUsedFlavors = List.copyOf(recentlyUsedFlavors);
      knownAttendees = List.copyOf(knownAttendees);
      previouslyProposedFlavors =
          previouslyProposedFlavors == null ? List.of() : List.copyOf(previouslyProposedFlavors);
      userInstruction = Objects.requireNonNullElse(userInstruction, "");
    }
  }

  public record DeclineDeployment(
      ExcuseFlavor flavor,
      String fakeCalendarEventId,
      String messageToOrganizer,
      String hallwayScript) {
    public DeclineDeployment {
      Objects.requireNonNull(flavor, "Missing flavor");
      requireText(messageToOrganizer, "messageToOrganizer");
      requireText(hallwayScript, "hallwayScript");
    }
  }

  public record DeclineReview(DeclineRequest request, DeclineDeployment plan) {}

  public record DeclineCritique(PlausibilityTier tier, Boolean approved, String feedback) {
    public DeclineCritique {
      Objects.requireNonNull(tier, "Missing critique tier");
      Objects.requireNonNull(approved, "Missing approved verdict");
      Objects.requireNonNull(feedback, "Missing critique feedback");
    }
  }

  public record DeclineSend(
      String callId, String candidateId, String eventId, String organizerName, String message) {}

  public record DeclineReceipt(
      Boolean delivered,
      String callId,
      String candidateId,
      String eventId,
      String organizerName,
      String deliveredAt) {
    public DeclineReceipt {
      Objects.requireNonNull(delivered, "Missing delivered verdict");
      requireText(callId, "callId");
      requireText(candidateId, "candidateId");
      requireText(eventId, "eventId");
      requireText(organizerName, "organizerName");
    }
  }

  public record CalendarEvent(
      String id, String title, String start, String organizer, boolean declined) {}

  public static final class Scenario {
    public static final String EVENT_ID = "basic-ai-proficiency-2026";
    public static final String ORGANIZER = "Dana from People Ops";
    public static final List<String> ATTENDEES =
        List.of(ORGANIZER, "your skip-level", "the whole platform team");
    public static final List<ExcuseFlavor> BURNED =
        List.of(
            ExcuseFlavor.CALENDAR_CONFLICT,
            ExcuseFlavor.FAMILY_OBLIGATION,
            ExcuseFlavor.CUSTOMER_ESCALATION);
    public static final String USER_CONTEXT =
        "Baruch builds AI agents for a living. On the afternoon of this training he is "
            + "presenting a conference talk about building AI agents, live, in public.";

    private Scenario() {}
  }

  static void requireText(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + field);
  }
}
