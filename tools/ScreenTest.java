import org.tn5250j.Session5250;
import org.tn5250j.TN5250jConstants;
import org.tn5250j.framework.common.SessionManager;
import org.tn5250j.framework.tn5250.Screen5250;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// ScreenTest - reusable engine for interactive-screen system tests.
// Usage: java ScreenTest <path-to-spec-file>
public class ScreenTest {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: ScreenTest <path-to-spec-file>");
    }

    String testName = Path.of(args[0]).getFileName().toString().replaceFirst("\\.spec$", "");

    // ── Arrange: read spec + connect ──────────────────────────
    Spec spec = Spec.load(Path.of(args[0]));

    String host = requireEnv("IBMI_HOST");
    String user = requireEnv("IBMI_USER");
    String password = requireEnv("IBMI_PASSWORD");

    Properties props = new Properties();
    props.setProperty(TN5250jConstants.SESSION_HOST, host);
    props.setProperty(TN5250jConstants.SESSION_HOST_PORT, "23");
    props.setProperty(TN5250jConstants.SESSION_TN_ENHANCED, "1");
    props.setProperty(TN5250jConstants.SESSION_SCREEN_SIZE, "1");
    props.setProperty("SESSION_CONNECT_USER", user);
    props.setProperty("SESSION_CONNECT_PASSWORD", password);
    props.setProperty("SESSION_CONNECT_PROGRAM", spec.program);
    props.setProperty("SESSION_CONNECT_LIBRARY", spec.library);

    Session5250 session = SessionManager.instance().openSession(props, "", "SCREENTEST");
    String screenText;

    try {
      session.connect();
      Screen5250 screen = session.getScreen();

      // ── Act: wait for the screen to settle, capture it ──────
      screenText = waitForScreen(screen, spec.expectations, 20_000);
    } finally {
      try {
        session.disconnect();
      } catch (Exception ignored) {
      }
    }

    // ── Assert: every expected string must be on screen ───────
    int passed = 0;
    int failed = 0;

    for (String expectation : spec.expectations) {
      if (screenText.contains(expectation)) {
        System.out.println("PASS: found \"" + expectation + "\"");
        passed++;
      } else {
        System.out.println("FAIL: \"" + expectation + "\" not found on screen");
        failed++;
      }
    }

    System.out.println("SYSTEM SUMMARY (" + testName + "): " + passed + " passed, " + failed + " failed");

    if (failed > 0) {
      System.exit(1);
    }
  }

  private static String waitForScreen(Screen5250 screen, List<String> expectations, long timeoutMs)
      throws Exception {
    long deadline = System.currentTimeMillis() + timeoutMs;
    String best = "";

    while (System.currentTimeMillis() < deadline) {
      best = renderScreen(screen);

      boolean allPresent = true;
      for (String expectation : expectations) {
        if (!best.contains(expectation)) {
          allPresent = false;
          break;
        }
      }
      if (allPresent) {
        return best;
      }

      Thread.sleep(500);
    }

    return best;
  }

  private static String renderScreen(Screen5250 screen) {
    char[] chars = screen.getScreenAsChars();
    int rows = screen.getRows();
    int cols = screen.getColumns();
    StringBuilder sb = new StringBuilder();

    for (int row = 0; row < rows; row++) {
      sb.append(chars, row * cols, cols).append('\n');
    }

    return sb.toString();
  }

  private static String requireEnv(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing environment variable: " + key);
    }
    return value;
  }

  private static final class Spec {
    String program;
    String library;
    List<String> expectations = new ArrayList<>();

    static Spec load(Path path) throws Exception {
      Spec spec = new Spec();
      for (String rawLine : Files.readAllLines(path)) {
        String line = rawLine.strip();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        int colon = line.indexOf(':');
        if (colon < 0) {
          continue;
        }
        String key = line.substring(0, colon).strip();
        String value = line.substring(colon + 1).strip();
        switch (key) {
          case "program" -> spec.program = value;
          case "library" -> spec.library = value;
          case "expect" -> spec.expectations.add(value);
          default -> throw new IllegalArgumentException("Unknown spec directive: " + key);
        }
      }
      if (spec.program == null || spec.library == null) {
        throw new IllegalArgumentException("Spec file must set 'program:' and 'library:'");
      }
      if (spec.expectations.isEmpty()) {
        throw new IllegalArgumentException("Spec file must have at least one 'expect:' line");
      }
      return spec;
    }
  }
}
