import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.access.Job;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class IbmiRunCmd {
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new IllegalArgumentException("Pass at least one IBM i command.");
    }

    String host = requireEnv("IBMI_HOST");
    String user = requireEnv("IBMI_USER");
    String password = requireEnv("IBMI_PASSWORD");

    AS400 system = new AS400(host, user, password);
    system.setGuiAvailable(false);
    CommandCall commandCall = new CommandCall(system);
    commandCall.setMessageOption(AS400Message.MESSAGE_OPTION_ALL);

    try {
      for (String command : args) {
        System.out.println("COMMAND " + command);
        boolean ok = commandCall.run(command);
        for (AS400Message message : commandCall.getMessageList()) {
          String id = safe(message.getID());
          String text = safe(message.getText());
          System.out.println(id + " " + text);
        }
        if (!ok) {
          dumpJoblog(host, user, password, commandCall.getServerJob());
        }
        System.out.println(ok ? "RESULT success" : "RESULT failed");
      }
    } finally {
      system.disconnectAllServices();
    }
  }

  private static String requireEnv(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing environment variable: " + key);
    }
    return value;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static void dumpJoblog(String host, String user, String password, Job job) {
    String qualifiedJobName = job.getNumber() + "/" + job.getUser() + "/" + job.getName();
    String sql =
        "SELECT ORDINAL_POSITION, MESSAGE_ID, MESSAGE_TYPE, SEVERITY, MESSAGE_TEXT, " +
        "COALESCE(MESSAGE_SECOND_LEVEL_TEXT, '') AS LVL2 " +
        "FROM TABLE(QSYS2.JOBLOG_INFO('" + qualifiedJobName + "')) " +
        "WHERE MESSAGE_TYPE IN ('COMPLETION', 'DIAGNOSTIC', 'ESCAPE', 'INFORMATIONAL', 'NOTIFY') " +
        "ORDER BY ORDINAL_POSITION DESC FETCH FIRST 80 ROWS ONLY";

    System.out.println("JOBLOG " + qualifiedJobName);

    try (Connection connection =
             DriverManager.getConnection(
                 "jdbc:as400://" + host + "/QSYS2;naming=sql;errors=full",
                 user,
                 password
             );
         Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        String ordinal = safe(rs.getString("ORDINAL_POSITION"));
        String id = safe(rs.getString("MESSAGE_ID"));
        String type = safe(rs.getString("MESSAGE_TYPE"));
        String severity = safe(rs.getString("SEVERITY"));
        String text = safe(rs.getString("MESSAGE_TEXT"));
        String lvl2 = safe(rs.getString("LVL2"));
        System.out.println(
            "JOBLOG " + ordinal + " " + id + " " + type + " sev=" + severity + " " + text
        );
        if (!lvl2.isBlank()) {
          System.out.println("  " + lvl2.replace('\n', ' ').replace('\r', ' ').trim());
        }
      }
    } catch (Exception e) {
      System.out.println("JOBLOG lookup failed: " + e.getMessage());
    }
  }
}
