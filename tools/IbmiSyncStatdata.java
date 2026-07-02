import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.access.IFSFile;
import com.ibm.as400.access.IFSFileOutputStream;
import com.ibm.as400.access.Job;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

public class IbmiSyncStatdata {
  private record UploadSpec(Path localPath, String remotePath, String memberPath) {}

  public static void main(String[] args) throws Exception {
    String host = requireEnv("IBMI_HOST");
    String user = requireEnv("IBMI_USER");
    String password = requireEnv("IBMI_PASSWORD");

    List<UploadSpec> uploads = List.of(
        new UploadSpec(
            Path.of("src/main/dds/STATDATAD.dspf"),
            "/home/CODELIVER/STATDATAD.dspf",
            "/QSYS.LIB/CODELIVER1.LIB/QDDSSRC.FILE/STATDATAD.MBR"
        ),
        new UploadSpec(
            Path.of("src/main/rpgle/STATDATA.rpgle"),
            "/home/CODELIVER/STATDATA.rpgle",
            "/QSYS.LIB/CODELIVER1.LIB/QRPGLESRC.FILE/STATDATA.MBR"
        )
    );

    List<String> commands = List.of(
        "CHGJOB CCSID(37) LANGID(ENU) CNTRYID(US)",
        "CPYFRMSTMF FROMSTMF('/home/CODELIVER/STATDATAD.dspf') TOMBR('/QSYS.LIB/CODELIVER1.LIB/QDDSSRC.FILE/STATDATAD.MBR') MBROPT(*REPLACE) STMFCCSID(*STMF) DBFCCSID(*FILE)",
        "CPYFRMSTMF FROMSTMF('/home/CODELIVER/STATDATA.rpgle') TOMBR('/QSYS.LIB/CODELIVER1.LIB/QRPGLESRC.FILE/STATDATA.MBR') MBROPT(*REPLACE) STMFCCSID(*STMF) DBFCCSID(*FILE)",
        "CRTDSPF FILE(CODELIVER1/STATDATAD) SRCFILE(CODELIVER1/QDDSSRC) SRCMBR(STATDATAD) REPLACE(*YES)",
        "CRTSQLRPGI OBJ(CODELIVER1/STATDATA) SRCFILE(CODELIVER1/QRPGLESRC) SRCMBR(STATDATA) OBJTYPE(*PGM) COMMIT(*NONE)"
    );

    AS400 system = new AS400(host, user, password);
    system.setGuiAvailable(false);
    CommandCall commandCall = new CommandCall(system);
    boolean anyFailed = false;

    try {
      System.out.println("CONNECTED " + host + " as " + user);

      for (UploadSpec upload : uploads) {
        uploadFile(system, upload.localPath(), upload.remotePath());
      }

      for (String command : commands) {
        if (!run(commandCall, command, host, user, password)) {
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

  private static void uploadFile(AS400 system, Path localPath, String remotePath) throws Exception {
    Path absoluteLocalPath = Path.of(System.getProperty("user.dir")).resolve(localPath).normalize();
    byte[] content = Files.readAllBytes(absoluteLocalPath);

    IFSFile remoteFile = new IFSFile(system, remotePath);
    if (remoteFile.exists()) {
      remoteFile.delete();
    }

    remoteFile.createNewFile();
    remoteFile.setCCSID(1208);

    try (IFSFileOutputStream outputStream = new IFSFileOutputStream(remoteFile)) {
      outputStream.write(content);
    }

    System.out.println("UPLOADED " + absoluteLocalPath + " -> " + remotePath);
  }

  private static boolean run(
      CommandCall commandCall,
      String command,
      String host,
      String user,
      String password
  ) throws Exception {
    System.out.println();
    System.out.println("COMMAND " + command);

    boolean ok = commandCall.run(command);
    AS400Message[] messages = commandCall.getMessageList();

    for (AS400Message message : messages) {
      String id = safe(message.getID());
      String text = safe(message.getText());
      String help = safe(message.getHelp());
      System.out.println(id + " " + text);
      if (!help.isBlank()) {
        System.out.println("  " + help.replace('\n', ' ').replace('\r', ' ').trim());
      }
    }

    if (!ok) {
      Job job = commandCall.getServerJob();
      dumpJoblog(host, user, password, job);
    }

    System.out.println(ok ? "RESULT success" : "RESULT failed");
    return ok;
  }

  private static void dumpJoblog(String host, String user, String password, Job job) {
    String qualifiedJobName = job.getNumber() + "/" + job.getUser() + "/" + job.getName();
    String sql =
        "SELECT MESSAGE_ID, MESSAGE_TEXT, COALESCE(MESSAGE_SECOND_LEVEL_TEXT, '') AS LVL2 " +
        "FROM TABLE(QSYS2.JOBLOG_INFO('" + qualifiedJobName + "')) " +
        "WHERE MESSAGE_TYPE IN ('DIAGNOSTIC', 'ESCAPE', 'NOTIFY') " +
        "ORDER BY ORDINAL_POSITION DESC FETCH FIRST 12 ROWS ONLY";

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
        String id = safe(rs.getString("MESSAGE_ID"));
        String text = safe(rs.getString("MESSAGE_TEXT"));
        String lvl2 = safe(rs.getString("LVL2"));
        System.out.println("JOBLOG " + id + " " + text);
        if (!lvl2.isBlank()) {
          System.out.println("  " + lvl2.replace('\n', ' ').replace('\r', ' ').trim());
        }
      }
    } catch (Exception e) {
      System.out.println("JOBLOG lookup failed: " + e.getMessage());
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
}
