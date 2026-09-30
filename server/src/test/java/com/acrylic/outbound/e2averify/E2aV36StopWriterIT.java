package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "E2A_STOP_VERIFY", matches = "true")
class E2aV36StopWriterIT {
    private static final Path ROOT = E2aWorktree.ROOT;
    private static final Path ATTEMPT = E2aV36Attempts.stopWriter();
    private static final String DATABASE = "acrylic_outbound_e2a_stop_20260924a";
    private static final String USER = "e2a_stop_20260924a";
    private static final String URL = "jdbc:mysql://127.0.0.1:19330/" + DATABASE
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    @Test void stopWriterPersistsOriginalBytesAndRollsBackFailedReference() throws Exception {
        requireProfile();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(URL, USER, System.getenv("E2A_STOP_PASSWORD")));
        requireIdentity(jdbc);
        int tables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()", Integer.class);
        if (tables != 0) throw new IllegalStateException("STOP target schema is not empty; no reseed");
        Files.createFile(ATTEMPT.resolve("test-started.marker"));
        migrate(jdbc, "34");
        Map<String, String> seed = E2aV34Seed.seed(jdbc, ATTEMPT.resolve("storage"));
        migrate(jdbc, "35");
        migrate(jdbc, "36");
        E2aV36StopWriterChecks.verify(jdbc, seed, ATTEMPT.resolve("storage"));
        Files.writeString(ATTEMPT.resolve("test-completed.marker"),
            "V34 nonempty -> V35/V36 -> real STOP file/object/original response and rollback\n");
    }

    private static void requireProfile() throws Exception {
        if (!ROOT.resolve("server").toRealPath().equals(Path.of("").toAbsolutePath().toRealPath())
            || !URL.equals(System.getenv("E2A_STOP_URL")) || !USER.equals(System.getenv("E2A_STOP_USER"))
            || System.getenv("E2A_STOP_PASSWORD") == null
            || System.getenv("E2A_STOP_PASSWORD").length() < 32
            || !Files.isDirectory(ATTEMPT.resolve("data"))
            || Files.exists(ATTEMPT.resolve("test-started.marker")))
            throw new IllegalStateException("STOP one-shot profile mismatch");
        for (String forbidden : new String[] {"OUTBOUND_DB_RESET", "OUTBOUND_DB_REPAIR", "E2A_MIGRATION_RESET"}) {
            if ("true".equals(System.getenv(forbidden))) throw new IllegalStateException("Reset/repair forbidden");
        }
    }

    private static void requireIdentity(JdbcTemplate jdbc) {
        String expected = ATTEMPT.resolve("data").toAbsolutePath().normalize().toString().replace('\\', '/') + "/";
        String actual = jdbc.queryForObject("SELECT @@datadir", String.class).replace('\\', '/');
        if (!expected.equalsIgnoreCase(actual)
            || !DATABASE.equals(jdbc.queryForObject("SELECT DATABASE()", String.class))
            || jdbc.queryForObject("SELECT @@port", Integer.class) != 19330
            || !(USER + "@127.0.0.1").equals(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)))
            throw new IllegalStateException("Connected MySQL is not the isolated STOP target");
    }

    private static void migrate(JdbcTemplate jdbc, String target) {
        Flyway.configure().dataSource(jdbc.getDataSource()).target(target).load().migrate();
    }
}
