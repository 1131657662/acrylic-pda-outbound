package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.JsonNode;

/** Opt-in target-34/35 execution; never runs in the default test task. */
@EnabledIfEnvironmentVariable(named = "E2A_MIGRATION_RUN", matches = "true")
class E2aMigrationExecutionIT {
    private static final Path WORKSPACE = Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");
    private static final Path MARKER = WORKSPACE.resolve(".local/e2a-target35.marker");

    @Test
    void seedTarget34MigrateTarget35AndCapturePreservation() throws Exception {
        JdbcTemplate jdbc = E2aMigrationEnvironment.connect(System.getenv(), portFree(), WORKSPACE);
        createNewMarker();
        requireEmptySchema(jdbc);
        Flyway.configure().dataSource(jdbc.getDataSource()).target("34").load().migrate();
        E2aV34Seed.seed(jdbc, WORKSPACE.resolve(E2aMigrationEnvironment.STORAGE_DIR));
        var snapshot = new E2aMigrationSnapshot(jdbc, WORKSPACE, WORKSPACE.resolve(E2aMigrationEnvironment.STORAGE_DIR));
        JsonNode before = snapshot.capture();
        snapshot.saveBefore("before-v35.json");
        Flyway.configure().dataSource(jdbc.getDataSource()).target("35").load().migrate();
        E2aV35NegativeChecks.verify(jdbc);
        snapshot.assertPreserved(before);
        snapshot.saveAfter("after-v35.json");
        Files.writeString(MARKER, "target35-complete\n");
    }

    private static boolean portFree() { return "true".equals(System.getenv("E2A_MIGRATION_PORT_FREE")); }

    private static void createNewMarker() throws Exception {
        Files.createDirectories(MARKER.getParent());
        try {
            Files.createFile(MARKER);
        } catch (java.nio.file.FileAlreadyExistsException error) {
            throw new IllegalStateException("Existing E2a marker; refusing reset or reseed", error);
        }
    }

    private static void requireEmptySchema(JdbcTemplate jdbc) {
        Integer tables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()", Integer.class);
        if (tables == null || tables != 0) throw new IllegalStateException("E2a schema is not empty; refusing reset");
    }

}
