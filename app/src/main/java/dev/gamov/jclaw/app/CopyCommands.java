package dev.gamov.jclaw.app;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Copies original application text, independently of terminal layout and wrapping. */
final class CopyCommands {
  private record Content(String label, String text) {}

  private final AtomicReference<Content> reply = new AtomicReference<>();
  private final AtomicReference<Content> candidate = new AtomicReference<>();
  private final AtomicReference<Content> latest = new AtomicReference<>();
  private final Supplier<Clipboard> clipboard;

  CopyCommands() {
    this(() -> Toolkit.getDefaultToolkit().getSystemClipboard());
  }

  CopyCommands(Supplier<Clipboard> clipboard) {
    this.clipboard = clipboard;
  }

  void reply(String text) {
    var content = new Content("assistant reply", text);
    reply.set(content);
    latest.set(content);
  }

  void candidate(String text) {
    var content = new Content("candidate message", text);
    candidate.set(content);
    latest.set(content);
  }

  void clearCandidate() {
    var previous = candidate.getAndSet(null);
    if (previous != null) latest.compareAndSet(previous, reply.get());
  }

  boolean handle(String input, Consumer<String> status) {
    var command = input.trim();
    if (!command.equals("/copy") && !command.startsWith("/copy ")) return false;
    if (!command.equals("/copy")
        && !command.equals("/copy reply")
        && !command.equals("/copy candidate")) {
      status.accept("Usage: /copy, /copy reply, or /copy candidate");
      return true;
    }
    var content =
        switch (command) {
          case "/copy reply" -> reply.get();
          case "/copy candidate" -> candidate.get();
          default -> latest.get();
        };
    if (content == null) {
      status.accept("Nothing to copy yet for " + command);
      return true;
    }
    try {
      clipboard.get().setContents(new StringSelection(content.text()), null);
      status.accept("Copied full " + content.label() + " to clipboard.");
    } catch (HeadlessException unavailable) {
      status.accept("Clipboard requires a desktop session. Use plain output on this host.");
    } catch (IllegalStateException busy) {
      status.accept("Clipboard is busy. Retry " + command + ".");
    } catch (SecurityException denied) {
      status.accept("Clipboard access denied. Check desktop clipboard permissions.");
    }
    return true;
  }
}
