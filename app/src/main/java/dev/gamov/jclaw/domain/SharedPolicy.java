package dev.gamov.jclaw.domain;

import com.fasterxml.jackson.databind.JsonNode;
import dev.gamov.jclaw.serialization.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads the pinned upstream resources unchanged, rather than duplicating comparison policy. */
public final class SharedPolicy {
  private SharedPolicy() {}

  public static JsonNode resource(String name) {
    try (var stream = SharedPolicy.class.getResourceAsStream("/jclaw/" + name)) {
      if (stream == null) throw new IllegalStateException("Missing shared policy: " + name);
      return Json.tree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException error) {
      throw new UncheckedIOException("Cannot read shared policy", error);
    }
  }
}
