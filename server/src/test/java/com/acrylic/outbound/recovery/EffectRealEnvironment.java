package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.sql.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

/**
 * The isolated coordinates are an <b>explicit environment contract</b>, not a literal pin: a batch may
 * provision a fresh database per run. Every rule still fails closed (no skip, no fallback, never 3306).
 */
final class EffectRealEnvironment {
    private static final Pattern URL_PATTERN = Pattern.compile(
        "jdbc:mysql://127\\.0\\.0\\.1:(19[4-9][0-9]{2})/([a-z0-9_]+)\\?([^\\s]+)");
    private static final Pattern EVIDENCE_NAME = Pattern.compile("run[0-9]+-[a-z0-9-]+");
    static String URL;
    static String USER;
    private static Path records;
    private static String password;

    static void requireEnvironment() throws Exception {
        Path candidate = Path.of("").toRealPath();
        while (candidate != null && !Files.isDirectory(candidate.resolve(".local"))) candidate = candidate.getParent();
        assertThat(candidate).as("a repository root containing .local must be reachable from the test directory")
            .isNotNull();
        Path local = candidate.resolve(".local").toRealPath();
        String url = System.getenv("OUTBOUND_DB_URL");
        USER = System.getenv("OUTBOUND_DB_USER");
        password = System.getenv("OUTBOUND_DB_PASSWORD");
        var matched = url == null ? null : URL_PATTERN.matcher(url);
        assertThat(matched != null && matched.matches())
            .as("OUTBOUND_DB_URL must be a loopback isolated URL with an explicit port in 19400-19999")
            .isTrue();
        int port = Integer.parseInt(matched.group(1));
        assertThat(port).as("isolated instances only; 3306 is never acceptable").isNotEqualTo(3306);
        assertThat(matched.group(3)).as("URL parameters are part of the contract")
            .contains("useSSL=false").contains("allowPublicKeyRetrieval=true").contains("serverTimezone=UTC");
        assertThat(USER).as("the schema and the account are the same isolated identity")
            .isEqualTo(matched.group(2));
        assertThat(password).isNotBlank();
        URL = url;
        String evidence = System.getenv("B11_EFFECT_EVIDENCE_DIR");
        assertThat(evidence).isNotBlank();
        Path directory = Path.of(evidence);
        assertThat(directory).isDirectory();
        assertThat(directory.toRealPath()).as("evidence stays inside the repository .local tree").startsWith(local);
        assertThat(directory.getFileName().toString()).as("one attempted run per directory").matches(EVIDENCE_NAME);
        records = directory.resolve("real-records.tsv");
        identity(port, local);
    }

    private static void identity(int port, Path local) throws Exception {
        try (var connection = open(false); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT @@port,@@datadir,@@version,CURRENT_USER(),@@server_uuid")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).as("the server must be the instance the contract names").isEqualTo(port);
            Path datadir = Path.of(rows.getString(2).replace('\\', '/')).toRealPath();
            assertThat(datadir).as("the server datadir must be an isolated .local instance").startsWith(local);
            assertThat(datadir.getParent().getFileName().toString()).as("instance directories carry their port")
                .contains(String.valueOf(port));
            assertThat(rows.getString(4)).startsWith(USER + "@");
            record("REAL|identity|port=" + rows.getInt(1) + "|datadir=" + rows.getString(2)
                + "|server=" + rows.getString(3) + "|user=" + rows.getString(4) + "|uuid=" + rows.getString(5)
                + "|driver=" + connection.getMetaData().getDriverVersion() + "|jvmPid=" + ProcessHandle.current().pid()
                + "|java=" + System.getProperty("java.version"));
            record("MODEL|legacy-admission-fixture|older Effect tests use fixture Authority; Fence tests identify real RecoveryFenceAuthority separately");
        }
    }

    static Connection open(boolean multi) throws SQLException {
        return DriverManager.getConnection(URL + (multi ? "&allowMultiQueries=true" : ""), USER, password);
    }

    static Connection locators() throws SQLException {
        return DriverManager.getConnection(URL + "&emulateLocators=true", USER, password);
    }

    static synchronized void record(String message) throws Exception {
        Files.writeString(records, java.time.Instant.now() + "|" + message + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println(message);
    }
}
