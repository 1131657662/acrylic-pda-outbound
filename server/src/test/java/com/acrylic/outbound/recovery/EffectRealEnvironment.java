package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

/** Exact isolated coordinates, checked before any connection is opened. No skip/fallback. */
final class EffectRealEnvironment {
    static final String URL = "jdbc:mysql://127.0.0.1:19442/b12_adv_20260929"
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    static final String USER = "b12_adv_20260929";
    private static Path records;
    private static String password;

    static void requireEnvironment() throws Exception {
        assertThat(System.getenv("OUTBOUND_DB_URL")).isEqualTo(URL).doesNotContain(":3306");
        assertThat(System.getenv("OUTBOUND_DB_USER")).isEqualTo(USER);
        password = System.getenv("OUTBOUND_DB_PASSWORD");
        assertThat(password).isNotBlank();
        String evidence = System.getenv("B11_EFFECT_EVIDENCE_DIR");
        assertThat(evidence).isNotBlank();
        assertThat(Path.of(evidence)).isDirectory();
        Path expected = Path.of("D:/acrylic-pda-outbound/.local/r09-c2-b1-2-adversarial-20260929-a");
        assertThat(Path.of(evidence).toRealPath().getParent()).isEqualTo(expected.toRealPath());
        assertThat(Path.of(evidence).getFileName().toString()).matches("run[0-9]+-[a-z0-9-]+");
        records = Path.of(evidence).resolve("real-records.tsv");
        identity();
    }

    private static void identity() throws Exception {
        try (var connection = open(false); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT @@port,@@datadir,@@version,CURRENT_USER(),@@server_uuid")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(19442);
            assertThat(rows.getString(2).replace('\\', '/'))
                .isEqualToIgnoringCase("D:/acrylic-pda-outbound/.local/mysql19442-b12-adversarial/data/");
            assertThat(rows.getString(4)).isEqualTo(USER + "@127.0.0.1");
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
