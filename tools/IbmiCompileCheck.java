import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.CommandCall;
import java.util.List;

public class IbmiCompileCheck {
  public static void main(String[] args) throws Exception {
    String host = requireEnv("IBMI_HOST");
    String user = requireEnv("IBMI_USER");
    String password = requireEnv("IBMI_PASSWORD");

    List<String> compileCommands = List.of(
        "CHGJOB CCSID(37)",
        "RUNSQLSTM SRCFILE(CODELIVER1/QSQLSRC) SRCMBR(CRTTBLS_V2) COMMIT(*NONE) NAMING(*SQL) DECMPT(*PERIOD)",
        "CRTDSPF FILE(CODELIVER1/STATDATAD) SRCFILE(CODELIVER1/QDDSSRC) SRCMBR(STATDATAD) REPLACE(*YES)",
        "CRTSQLRPGI OBJ(CODELIVER1/STATDATA) SRCFILE(CODELIVER1/QRPGLESRC) SRCMBR(STATDATA) OBJTYPE(*PGM) COMMIT(*NONE)",
        "CRTSQLRPGI OBJ(CODELIVER1/UTEST01) SRCFILE(CODELIVER1/QRPGLESRC) SRCMBR(UTEST01) OBJTYPE(*PGM) COMMIT(*NONE)",
        "CRTBNDCL PGM(CODELIVER1/ITEST01A) SRCFILE(CODELIVER1/QCLSRC) SRCMBR(ITEST01A)"
    );

    AS400 system = new AS400(host, user, password);
    system.setGuiAvailable(false);
    CommandCall commandCall = new CommandCall(system);
    boolean anyFailed = false;

    try {
      System.out.println("CONNECTED " + host + " as " + user);
      for (String command : compileCommands) {
        if (!run(commandCall, command, false)) {
          anyFailed = true;
        }
      }
    } finally {
      system.disconnectAllServices();
    }

    if (anyFailed) {
      throw new IllegalStateException("One or more IBM i commands failed.");
    }
  }

  private static boolean run(CommandCall commandCall, String command, boolean tolerateExistingMember) throws Exception {
    System.out.println();
    System.out.println("COMMAND " + command);

    boolean ok = commandCall.run(command);
    AS400Message[] messages = commandCall.getMessageList();
    boolean existingMemberOnly = tolerateExistingMember && isExistingMemberOnly(messages);

    for (AS400Message message : messages) {
      String id = safe(message.getID());
      String text = safe(message.getText());
      String help = safe(message.getHelp());
      System.out.println(id + " " + text);
      if (!help.isBlank()) {
        System.out.println("  " + help.replace('\n', ' ').replace('\r', ' ').trim());
      }
    }

    if (existingMemberOnly) {
      System.out.println("RESULT existing member tolerated");
      return true;
    }

    if (!ok) {
      System.out.println("RESULT failed");
      return false;
    } else {
      System.out.println("RESULT success");
      return true;
    }
  }

  private static boolean isExistingMemberOnly(AS400Message[] messages) {
    if (messages == null || messages.length == 0) {
      return false;
    }

    boolean sawExisting = false;

    for (AS400Message message : messages) {
      String joined = (safe(message.getID()) + " " + safe(message.getText()) + " " + safe(message.getHelp()))
          .toUpperCase();
      if (joined.contains("ALREADY EXISTS")) {
        sawExisting = true;
        continue;
      }

      int type = message.getType();
      if (type == AS400Message.COMPLETION || type == AS400Message.INFORMATIONAL) {
        continue;
      }

      return false;
    }

    return sawExisting;
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
}
