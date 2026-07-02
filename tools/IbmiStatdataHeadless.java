import org.tn5250j.Session5250;
import org.tn5250j.TN5250jConstants;
import org.tn5250j.framework.common.SessionManager;
import org.tn5250j.framework.tn5250.Screen5250;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Properties;

public class IbmiStatdataHeadless {
  public static void main(String[] args) throws Exception {
    String host = requireEnv("IBMI_HOST");
    String user = requireEnv("IBMI_USER");
    String password = requireEnv("IBMI_PASSWORD");
    String outputPath = System.getenv().getOrDefault("STATDATA_IMAGE", "/private/tmp/statdata-headless.png");

    Properties props = new Properties();
    props.setProperty(TN5250jConstants.SESSION_HOST, host);
    props.setProperty(TN5250jConstants.SESSION_HOST_PORT, "23");
    props.setProperty(TN5250jConstants.SESSION_TN_ENHANCED, "1");
    props.setProperty(TN5250jConstants.SESSION_SCREEN_SIZE, "1");
    props.setProperty("SESSION_CONNECT_USER", user);
    props.setProperty("SESSION_CONNECT_PASSWORD", password);
    props.setProperty("SESSION_CONNECT_PROGRAM", "STATDATA");
    props.setProperty("SESSION_CONNECT_LIBRARY", "CODELIVER1");

    Session5250 session = SessionManager.instance().openSession(props, "", "HEADLESSSTAT");

    try {
      session.connect();
      Screen5250 screen = session.getScreen();

      String[] lines = waitForScreen(screen, 20_000);

      for (int i = 0; i < lines.length; i++) {
        System.out.printf("%02d|%s%n", i + 1, rtrim(lines[i]));
      }

      renderImage(lines, Path.of(outputPath).toFile());
      System.out.println("IMAGE " + outputPath);
    } finally {
      try {
        session.disconnect();
      } catch (Exception ignored) {
      }
    }
  }

  private static String[] waitForScreen(Screen5250 screen, long timeoutMs) throws Exception {
    long deadline = System.currentTimeMillis() + timeoutMs;
    String[] best = null;

    while (System.currentTimeMillis() < deadline) {
      String[] lines = getLines(screen);
      best = lines;

      boolean hasRows = contains(lines, "CU0001") && contains(lines, "CU0004");
      if (hasRows) {
        return lines;
      }

      Thread.sleep(500);
    }

    if (best == null) {
      throw new IllegalStateException("No screen data captured.");
    }

    return best;
  }

  private static String[] getLines(Screen5250 screen) {
    char[] chars = screen.getScreenAsChars();
    int rows = screen.getRows();
    int cols = screen.getColumns();
    String[] lines = new String[rows];

    for (int row = 0; row < rows; row++) {
      lines[row] = new String(chars, row * cols, cols);
    }

    return lines;
  }

  private static boolean contains(String[] lines, String needle) {
    for (String line : lines) {
      if (line.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  private static void renderImage(String[] lines, File output) throws Exception {
    Font font = new Font("Menlo", Font.PLAIN, 28);
    BufferedImage scratch = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    Graphics2D g0 = scratch.createGraphics();
    g0.setFont(font);
    FontMetrics metrics = g0.getFontMetrics();
    int charWidth = metrics.charWidth('W');
    int lineHeight = metrics.getHeight() + 4;
    g0.dispose();

    int width = 80 * charWidth + 40;
    int height = lines.length * lineHeight + 40;
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(Color.BLACK);
    g.fillRect(0, 0, width, height);
    g.setColor(new Color(110, 255, 80));
    g.setFont(font);

    int y = 30 + metrics.getAscent();
    for (String line : lines) {
      g.drawString(rtrim(line), 20, y);
      y += lineHeight;
    }

    g.dispose();
    output.getParentFile().mkdirs();
    ImageIO.write(image, "png", output);
  }

  private static String requireEnv(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing environment variable: " + key);
    }
    return value;
  }

  private static String rtrim(String value) {
    int end = value.length();
    while (end > 0 && value.charAt(end - 1) == ' ') {
      end--;
    }
    return value.substring(0, end);
  }
}
