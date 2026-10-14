import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Read-only snapshot through one JDBC connection; raw query results precede validation. */
public final class JdbcDatabaseCapture {
    private final Connection connection;
    private final Path directory;
    private int number;

    private JdbcDatabaseCapture(Connection connection, Path directory) {
        this.connection = connection;
        this.directory = directory;
    }

    private static String identifier(String value) {
        if (!value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid database identifier");
        }
        return "`" + value + "`";
    }

    private static Properties config(Path path) throws Exception {
        Properties values = new Properties();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String clean = line.strip();
            if (clean.isEmpty() || clean.startsWith("[") || clean.startsWith("#")) continue;
            String[] parts = clean.split("=", 2);
            if (parts.length != 2 || values.put(parts[0].strip(), parts[1].strip()) != null) {
                throw new IllegalArgumentException("Invalid/duplicate client config entry");
            }
        }
        if (!"127.0.0.1".equals(values.getProperty("host"))) {
            throw new IllegalArgumentException("Only isolated loopback capture is allowed");
        }
        return values;
    }

    private byte[] query(String table, String query) throws Exception {
        Path prefix = directory.resolve(String.format("%04d", ++number));
        Files.writeString(Path.of(prefix + ".table"), table);
        Files.writeString(Path.of(prefix + ".sql"), query);
        long started = System.nanoTime();
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(60);
            byte[] raw = rows(statement.executeQuery(query));
            Files.write(Path.of(prefix + ".stdout"), raw);
            Files.write(Path.of(prefix + ".stderr"), new byte[0]);
            Files.writeString(Path.of(prefix + ".status"), "0");
            return raw;
        } catch (SQLException error) {
            Files.write(Path.of(prefix + ".stdout"), new byte[0]);
            Files.writeString(Path.of(prefix + ".stderr"), error.toString());
            Files.writeString(Path.of(prefix + ".status"), String.valueOf(error.getErrorCode()));
            throw error;
        } finally {
            Files.writeString(Path.of(prefix + ".seconds"),
                    String.valueOf((System.nanoTime() - started) / 1_000_000_000.0));
        }
    }

    private static byte[] rows(ResultSet result) throws SQLException {
        try (result) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                for (int column = 1; column <= columns; column++) {
                    if (column > 1) output.write('\t');
                    byte[] value = result.getBytes(column);
                    output.writeBytes(value == null ? "NULL".getBytes(StandardCharsets.US_ASCII) : value);
                }
                output.write('\n');
            }
            return output.toByteArray();
        }
    }

    private void table(String name) throws Exception {
        String quoted = identifier(name);
        byte[] raw = query(name, "SHOW COLUMNS FROM " + quoted);
        List<String> expressions = new ArrayList<>();
        for (String line : new String(raw, StandardCharsets.US_ASCII).split("\n")) {
            if (line.isEmpty()) continue;
            String column = identifier(line.split("\t", 2)[0]);
            expressions.add("IF(" + column + " IS NULL,'N',CONCAT('V',HEX(CAST(" + column + " AS BINARY))))");
        }
        if (expressions.isEmpty()) throw new IllegalStateException("Missing columns for " + name);
        query(name, "SELECT " + String.join(",", expressions) + " FROM " + quoted);
        query(name, "SELECT COUNT(*) FROM " + quoted);
    }

    private void identity(String database, String port, String datadir) throws Exception {
        byte[] raw = query("__identity__", "SELECT DATABASE(),@@port,@@datadir");
        String[] values = new String(raw, StandardCharsets.UTF_8).strip().split("\t");
        if (values.length != 3 || !values[0].equals(database) || !values[1].equals(port)
                || !Path.of(values[2]).toRealPath().equals(Path.of(datadir).toRealPath())) {
            throw new IllegalStateException("Wrong isolated database identity; snapshot refused");
        }
    }

    private void capture() throws Exception {
        byte[] inventory = query("__database__", "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() ORDER BY table_name");
        for (String name : new String(inventory, StandardCharsets.US_ASCII).split("\n")) {
            if (!name.isEmpty()) table(name);
        }
    }

    public static void main(String[] args) throws Exception {
        Properties config = config(Path.of(args[0]));
        identifier(args[1]);
        String port = config.getProperty("port");
        if (port == null || !port.matches("[0-9]+")) throw new IllegalArgumentException("Invalid port");
        String url = "jdbc:mysql://127.0.0.1:" + port + "/" + args[1]
                + "?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=60000";
        DriverManager.setLoginTimeout(10);
        try (Connection connection = DriverManager.getConnection(url, config.getProperty("user"), config.getProperty("password", ""))) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            JdbcDatabaseCapture reader = new JdbcDatabaseCapture(connection, Path.of(args[2]));
            reader.identity(args[1], args[3], args[4]);
            reader.capture();
            connection.rollback();
        }
    }
}
