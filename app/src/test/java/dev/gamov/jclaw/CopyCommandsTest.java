package dev.gamov.jclaw;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.HeadlessException;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CopyCommandsTest {
  @Test
  void fullReplyPreservesUnicodeParagraphsAndOffscreenContentAcrossRepeatedCopies()
      throws Exception {
    var clipboard = new Clipboard("In-memory desktop boundary");
    var copy = new CopyCommands(() -> clipboard);
    var statuses = new ArrayList<String>();
    var reply = "Hello Viktor — café ☕\n\n" + "A long paragraph. ".repeat(200) + "\nFinal line.";
    copy.reply(reply);

    assertTrue(copy.handle("/copy", statuses::add));
    assertEquals(reply, clipboard.getData(DataFlavor.stringFlavor));
    assertTrue(copy.handle("/copy reply", statuses::add));
    assertEquals(reply, clipboard.getData(DataFlavor.stringFlavor));
    assertEquals(2, statuses.size());
    assertTrue(statuses.stream().allMatch(line -> line.contains("Copied full assistant reply")));
  }

  @Test
  void explicitSelectionKeepsReplyAndCandidateSeparateAndDefaultsToMostRecent() throws Exception {
    var clipboard = new Clipboard("In-memory desktop boundary");
    var copy = new CopyCommands(() -> clipboard);
    copy.reply("Avoided reasons and explanatory response");
    copy.candidate("Dear Dana,\n\nLiteral proposed email.\nViktor");

    assertTrue(copy.handle("/copy", line -> {}));
    assertEquals(
        "Dear Dana,\n\nLiteral proposed email.\nViktor",
        clipboard.getData(DataFlavor.stringFlavor));
    assertTrue(copy.handle("/copy reply", line -> {}));
    assertEquals(
        "Avoided reasons and explanatory response", clipboard.getData(DataFlavor.stringFlavor));
    copy.reply("A later corporate-speak rewrite");
    assertTrue(copy.handle("/copy", line -> {}));
    assertEquals("A later corporate-speak rewrite", clipboard.getData(DataFlavor.stringFlavor));
    assertTrue(copy.handle("/copy candidate", line -> {}));
    assertEquals(
        "Dear Dana,\n\nLiteral proposed email.\nViktor",
        clipboard.getData(DataFlavor.stringFlavor));
  }

  @Test
  void newTurnClearsOldCandidateAndCopyUsesRetainedReply() throws Exception {
    var clipboard = new Clipboard("In-memory desktop boundary");
    var copy = new CopyCommands(() -> clipboard);
    var statuses = new ArrayList<String>();
    copy.reply("Previous chat response");
    copy.candidate("Old candidate");
    copy.clearCandidate();
    assertTrue(copy.handle("/copy candidate", statuses::add));
    assertEquals("Nothing to copy yet for /copy candidate", statuses.getFirst());
    assertTrue(copy.handle("/copy", statuses::add));
    assertEquals("Previous chat response", clipboard.getData(DataFlavor.stringFlavor));
  }

  @Test
  void unavailableTextAndMalformedCopyCommandDoNotChangeClipboardAndChatStillRoutes()
      throws Exception {
    var clipboard = new Clipboard("In-memory desktop boundary");
    clipboard.setContents(new StringSelection("Existing clipboard text"), null);
    var copy = new CopyCommands(() -> clipboard);
    var statuses = new ArrayList<String>();

    assertTrue(copy.handle("/copy", statuses::add));
    assertTrue(copy.handle("/copy unknown", statuses::add));
    assertFalse(copy.handle("Please copy this into the email", statuses::add));
    assertFalse(copy.handle("/copycat", statuses::add));
    assertEquals("Existing clipboard text", clipboard.getData(DataFlavor.stringFlavor));
    assertEquals(2, statuses.size());
    assertTrue(statuses.getFirst().contains("Nothing to copy yet"));
    assertTrue(statuses.getLast().startsWith("Usage:"));
  }

  @Test
  void desktopAvailabilityFailuresAreLocalStatusesAndOrdinaryInputStillRoutes() {
    // This supplier is the external desktop access boundary, not an application collaborator.
    var failures =
        new RuntimeException[] {
          new HeadlessException("No desktop"),
          new IllegalStateException("Clipboard busy"),
          new SecurityException("Desktop access denied")
        };
    for (var failure : failures) {
      var copy =
          new CopyCommands(
              () -> {
                throw failure;
              });
      var statuses = new ArrayList<String>();
      copy.reply("Retained response");
      assertTrue(copy.handle("/copy", statuses::add));
      assertEquals(1, statuses.size());
      assertFalse(statuses.getFirst().startsWith("Copied"));
      assertFalse(copy.handle("Continue the conversation", statuses::add));
    }
  }
}
