package dev.gamov.jclaw.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class Json {
  private static final JsonMapper MAPPER =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

  private Json() {}

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("Cannot serialize contract", error);
    }
  }

  public static <T> T read(String value, Class<T> type) {
    try {
      return MAPPER.readValue(value, type);
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("Invalid " + type.getSimpleName() + " JSON", error);
    }
  }

  public static JsonNode tree(String value) {
    try {
      return MAPPER.readTree(value);
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("Invalid JSON", error);
    }
  }
}
