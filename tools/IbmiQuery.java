import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

public class IbmiQuery {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Pass one SQL query string.");
    }

    String host = requireEnv("IBMI_HOST");
    String user = requireEnv("IBMI_USER");
    String password = requireEnv("IBMI_PASSWORD");

    try (Connection connection =
             DriverManager.getConnection(
                 "jdbc:as400://" + host + "/QSYS2;naming=sql;errors=full",
                 user,
                 password
             );
         Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery(args[0])) {
      ResultSetMetaData meta = rs.getMetaData();
      int cols = meta.getColumnCount();
      while (rs.next()) {
        StringBuilder line = new StringBuilder();
        for (int i = 1; i <= cols; i++) {
          if (i > 1) {
            line.append(" | ");
          }
          line.append(meta.getColumnLabel(i)).append("=").append(rs.getString(i));
        }
        System.out.println(line);
      }
    }
  }

  private static String requireEnv(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing environment variable: " + key);
    }
    return value;
  }
}
