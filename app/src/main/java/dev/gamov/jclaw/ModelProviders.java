package dev.gamov.jclaw;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Native provider clients; transport selection is fixed by the workflow role. */
final class ModelProviders {
  record Models(ChatModel chat, ChatModel draft, ChatModel review) {}

  /** Local HTTP boundary fixtures only; production uses the clients' native API URLs. */
  record Endpoints(String anthropic, String openai) {}

  static final class MissingSetting extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    MissingSetting(String name) {
      super("Set " + name + " in .env; ./jclaw preview needs no provider credentials.");
    }
  }

  private ModelProviders() {}

  static Models create(
      DemoMode mode, ModelLineup lineup, Map<String, String> environment, TraceEvidence evidence) {
    return create(mode, lineup, environment, evidence, null);
  }

  static Models create(
      DemoMode mode,
      ModelLineup lineup,
      Map<String, String> environment,
      TraceEvidence evidence,
      Endpoints endpoints) {
    var googleKey = googleKey(environment);
    String anthropicKey = null;
    String openaiKey = null;
    if (mode.round() >= 5) {
      anthropicKey = require(environment, "ANTHROPIC_API_KEY");
      openaiKey = require(environment, "OPENAI_API_KEY");
    }
    var chat = gemini("chat", lineup.chat(), googleKey, evidence);
    // Before workflow is introduced these arguments are unused by DemoSession.
    if (mode.round() < 5) return new Models(chat, chat, chat);

    evidence.event("PROVIDER", "draft/refine=" + lineup.draft() + " (Anthropic API)");
    var draft =
        AnthropicChatModel.builder()
            .apiKey(anthropicKey)
            .modelName(lineup.draft())
            .maxTokens(8192)
            .returnThinking(false)
            .maxRetries(0)
            .timeout(Duration.ofMinutes(3))
            .listeners(
                List.of(evidence.modelListener("draft/refine", "Anthropic API", lineup.draft())));
    evidence.event("PROVIDER", "review=" + lineup.review() + " (OpenAI API)");
    var review =
        OpenAiChatModel.builder()
            .apiKey(openaiKey)
            .modelName(lineup.review())
            .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
            .strictJsonSchema(true)
            .store(false)
            .maxRetries(0)
            .timeout(Duration.ofMinutes(3))
            .listeners(List.of(evidence.modelListener("review", "OpenAI API", lineup.review())));
    if (endpoints != null) {
      draft.baseUrl(endpoints.anthropic());
      review.baseUrl(endpoints.openai());
    }
    return new Models(chat, draft.build(), review.build());
  }

  static GoogleGenAiChatModel gemini(String role, String name, String key, TraceEvidence evidence) {
    return gemini(role, name, key, evidence, null);
  }

  /** A local HTTP endpoint is supplied only by network-boundary tests. */
  static GoogleGenAiChatModel gemini(
      String role, String name, String key, TraceEvidence evidence, String endpoint) {
    evidence.event("PROVIDER", role + "=" + name + " (Gemini API)");
    var builder =
        GoogleGenAiChatModel.builder()
            .apiKey(key)
            .modelName(name)
            .returnThinking(false)
            .sendThinking(false)
            .maxRetries(0)
            .timeout(Duration.ofSeconds(90))
            .generateContentConfigCustomizer(
                config ->
                    config.httpOptions(
                        HttpOptions.builder()
                            .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                            .build()))
            .listeners(List.of(evidence.modelListener(role, "Gemini API", name)));
    if (endpoint != null) builder.apiEndpoint(endpoint);
    return builder.build();
  }

  static String googleKey(Map<String, String> environment) {
    var key = environment.get("GOOGLE_API_KEY");
    if (key == null || key.isBlank()) key = environment.get("GOOGLE_AI_API_KEY");
    if (key == null || key.isBlank())
      throw new MissingSetting("GOOGLE_API_KEY or GOOGLE_AI_API_KEY");
    return key;
  }

  private static String require(Map<String, String> environment, String name) {
    var value = environment.get(name);
    if (value == null || value.isBlank()) throw new MissingSetting(name);
    return value;
  }
}
