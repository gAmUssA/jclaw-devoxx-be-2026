package dev.gamov.jclaw.memory;

import static dev.gamov.jclaw.domain.Contracts.*;

import dev.gamov.jclaw.domain.Delivery;
import dev.gamov.jclaw.serialization.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Owns the sent-history schema; proposals and conversation never enter the file. */
public final class SentHistory {
  public record SentRecord(
      String eventId,
      String organizerName,
      String message,
      ExcuseFlavor flavor,
      String deliveredAt,
      String candidateId,
      String callId) {}

  public record Store(int schema_version, List<SentRecord> sends) {}

  private final Path path;

  public SentHistory(Path path) {
    this.path = path;
  }

  public List<SentRecord> read() {
    if (!Files.exists(path)) return List.of();
    try {
      var store = Json.read(Files.readString(path), Store.class);
      if (store.schema_version() != 1)
        throw new IllegalStateException(
            "Unsupported history schema; preserve the file and use a compatible build");
      return List.copyOf(store.sends());
    } catch (IOException error) {
      throw new UncheckedIOException(
          "Cannot read sent history; check state directory permissions", error);
    }
  }

  public synchronized void record(DeclineSend send, DeclineReceipt receipt, ExcuseFlavor flavor) {
    Delivery.confirm(Json.write(receipt), false, send);
    var existing = read();
    if (existing.stream().anyMatch(record -> record.callId().equals(receipt.callId()))) return;
    var records = new ArrayList<>(existing);
    records.add(
        new SentRecord(
            send.eventId(),
            send.organizerName(),
            send.message(),
            flavor,
            receipt.deliveredAt(),
            send.candidateId(),
            send.callId()));
    var absolute = path.toAbsolutePath();
    try {
      Files.createDirectories(absolute.getParent());
      var temporary = Files.createTempFile(absolute.getParent(), ".sent-", ".json");
      try {
        Files.writeString(temporary, Json.write(new Store(1, records)));
        Files.move(
            temporary,
            absolute,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException error) {
      throw new UncheckedIOException(
          "Delivered, but sent history could not be saved; preserve the receipt and repair state permissions",
          error);
    }
  }
}
