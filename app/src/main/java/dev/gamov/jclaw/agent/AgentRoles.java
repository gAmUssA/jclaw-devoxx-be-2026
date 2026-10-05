package dev.gamov.jclaw.agent;

import static dev.gamov.jclaw.domain.Contracts.*;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/** Native LangChain4j typed agents. The application owns external-action capabilities. */
public final class AgentRoles {
  private AgentRoles() {}

  public interface Drafter {
    @Agent("Draft or refine a typed decline proposal without external actions")
    @SystemMessage("You draft only. You have no tools. Never send or create calendar events.")
    @UserMessage(
        """
        Draft the best available decline for this current request: {{request}}
        Viktor builds AI agents for a living and presents a public conference talk
        about building agents on the afternoon of the fictional training.
        These background facts are context, not required wording or additional excuses.
        Use only supported facts relevant to the chosen reason. Do not invent a
        scheduling overlap, reserved work slot, or absolute claim about availability.
        Preserve the exact organizer identity. Never reuse the substance of
        recentlyUsedFlavors or previouslyProposedFlavors, even under another label.
        Honor userInstruction and knownAttendees. Apply review feedback to both scripts;
        remove rejected claims rather than reintroducing them in different wording.
        fakeCalendarEventId must be null. Do not claim any external action.
        messageToOrganizer and hallwayScript are literal outward wording.
        Do not include analysis or lists of avoided excuses in either field;
        the application reports avoided reasons separately to the user.
        Previous plan: {{previous}}
        Review feedback: {{feedback}}
        Return the complete revised plan, including a hallway script.
        """)
    DeclineDeployment draft(
        @V("request") DeclineRequest request,
        @V("previous") String previous,
        @V("feedback") String feedback);
  }

  public interface Critic {
    @Agent("Independently judge the exact typed candidate against the current request")
    @SystemMessage(
        """
        You are an independent reviewer of the typed decline candidate. You have no action tools.
        The application separately reports the avoided reasons to the user.
        Do not require that report in the candidate, or reject its absence.
        The candidate contains a flavor, optional supporting-event ID, and two outward scripts.
        Assess their facts, constraints and plausibility; decide approval independently.
        """)
    @UserMessage(
        """
        Assess the quality and plausibility of this exact decline plan.
        Typed review containing the current request and exact candidate: {{review}}
        Viktor builds AI agents for a living and presents a public conference talk
        about building agents on the afternoon of the fictional training.
        These background facts are context, not required wording or additional excuses.
        Check all user constraints, facts, known attendees, target identity,
        recentlyUsedFlavors and previouslyProposedFlavors, including substantive
        reuse concealed under a different flavor label. Do not infer scheduling
        overlap or reserved work time unless the supplied facts establish it.
        No calendar event was
        created by drafting. Never approve an earlier plan in place of this plan.
        Both messageToOrganizer and hallwayScript must be literal outward wording,
        with no analysis or lists of avoided excuses embedded in them.
        Decide approved and tier yourself. Explain real reasoning in feedback.
        An approval permits consideration by the human; it does not send anything.
        """)
    DeclineCritique review(@V("review") DeclineReview review);
  }

  public enum Intent {
    DECLINE,
    CHAT,
    STYLE
  }

  public record Route(Intent intent, String eventId) {}

  public interface Router {
    @Agent("Classify the user turn and identify the selected obligation")
    @UserMessage(
        """
        Classify this user instruction as DECLINE (a new or revised decline request),
        STYLE (only rewriting language without changing facts or commitments), or CHAT.
        Select eventId only from the supplied calendar for DECLINE. Otherwise use an empty eventId.
        Instruction: {{instruction}}
        Current candidate context: {{context}}
        Calendar: {{calendar}}
        """)
    Route route(
        @V("instruction") String instruction,
        @V("context") String context,
        @V("calendar") String calendar);
  }

  public interface Chat {
    @UserMessage("{{instruction}}")
    String reply(@V("instruction") String instruction);
  }

  public static Drafter drafter(ChatModel model) {
    return AgenticServices.agentBuilder(Drafter.class).chatModel(model).build();
  }

  public static Critic critic(ChatModel model) {
    return AgenticServices.agentBuilder(Critic.class).chatModel(model).build();
  }
}
