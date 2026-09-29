package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

/** Exact isolated coordinates, checked before any connection is opened. No skip/fallback. */
final class EffectRealEnvironment {
    static final String URL = "jdbc:mysql://127.0.0.1:19439/b11_effect_20260929"
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    static final String USER = "b11_effect_20260929";
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
        records = Path.of(evidence).resolve("real-records.tsv");
        identity();
    }

    private static void identity() throws Exception {
        try (var connection = open(false); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT @@port,@@datadir,@@version,CURRENT_USER(),@@server_uuid")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(19439);
            assertThat(rows.getString(2).replace('\\', '/'))
                .isEqualToIgnoringCase("D:/acrylic-pda-outbound/.local/mysql19439-b11/data/");
            assertThat(rows.getString(4)).isEqualTo(USER + "@127.0.0.1");
            record("REAL|identity|port=" + rows.getInt(1) + "|datadir=" + rows.getString(2)
                + "|server=" + rows.getString(3) + "|user=" + rows.getString(4) + "|uuid=" + rows.getString(5)
                + "|driver=" + connection.getMetaData().getDriverVersion() + "|jvmPid=" + ProcessHandle.current().pid()
                + "|java=" + System.getProperty("java.version"));
            record("MODEL|authority|explicit test decision input; no P5/fenced.lock evidence");
        }
    }

    static Connection open(boolean multi) throws SQLException {
        return DriverManager.getConnection(URL + (multi ? "&allowMultiQueries=true" : ""), USER, password);
    }

    static synchronized void record(String message) throws Exception {
        Files.writeString(records, java.time.Instant.now() + "|" + message + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println(message);
    }
}
