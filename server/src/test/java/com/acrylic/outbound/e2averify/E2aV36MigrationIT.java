package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "E2A_V36_VERIFY", matches = "true")
class E2aV36MigrationIT {
    private static final Path ROOT = Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");
    private static final Path ATTEMPT = ROOT.resolve(".local/e2a-v36-attempt4");
    private static final String DATABASE = "acrylic_outbound_e2a_v36_20260924d";
    private static final String USER = "e2a_v36_20260924d";
    private static final String URL = "jdbc:mysql://127.0.0.1:19330/" + DATABASE
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    @Test
    void migrateNonEmptyFixtureAndExerciseStageConstraints() throws Exception {
        requireProfile();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(URL, USER, System.getenv("E2A_V36_PASSWORD")));
        requireIdentity(jdbc);
        int tables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()", Integer.class);
        if (tables != 0) throw new IllegalStateException("V36 target schema is not empty; no reseed");
        Files.createFile(ATTEMPT.resolve("test-started.marker"));
        migrate(jdbc, "34");
        Map<String, String> seed = E2aV34Seed.seed(jdbc, ATTEMPT.resolve("storage"));
        Path originalFile = Path.of(seed.get("mappedFile"));
        byte[] original = digest(originalFile);
        int oldObjects = count(jdbc, "source_evidence_object");
        int oldItems = count(jdbc, "source_evidence_item");
        migrate(jdbc, "35");
        migrate(jdbc, "36");
        E2aV36StageChecks.verify(jdbc, seed);
        E2aV36PageWriterChecks.verify(jdbc, seed, ATTEMPT.resolve("storage"));
        if (count(jdbc, "source_evidence_object") < oldObjects
                || count(jdbc, "source_evidence_item") != oldItems
                || !Arrays.equals(original, digest(originalFile))) {
            throw new IllegalStateException("V34 rows or file changed during V36 upgrade");
        }
        Files.writeString(ATTEMPT.resolve("test-completed.marker"), "V34 fixture -> V35 -> V36; stage constraints and real-file writer verified\n");
    }

    private static void requireProfile() throws Exception {
        if (!ROOT.resolve("server").toRealPath().equals(Path.of("").toAbsolutePath().toRealPath())
                || !URL.equals(System.getenv("E2A_V36_URL"))
                || !USER.equals(System.getenv("E2A_V36_USER"))
                || System.getenv("E2A_V36_PASSWORD") == null
                || System.getenv("E2A_V36_PASSWORD").length() < 32
                || !Files.isDirectory(ATTEMPT.resolve("data"))
                || Files.exists(ATTEMPT.resolve("test-started.marker"))) {
            throw new IllegalStateException("V36 one-shot profile mismatch");
        }
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
                || !(USER + "@127.0.0.1").equals(jdbc.queryForObject("SELECT CURRENT_USER()", String.class))) {
            throw new IllegalStateException("Connected MySQL is not the isolated V36 target");
        }
    }

    private static void migrate(JdbcTemplate jdbc, String target) {
        Flyway.configure().dataSource(jdbc.getDataSource()).target(target).load().migrate();
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static byte[] digest(Path file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }
}
