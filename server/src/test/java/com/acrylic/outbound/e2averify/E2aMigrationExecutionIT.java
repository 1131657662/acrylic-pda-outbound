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
        E2aMigrationRunner.main(new String[0]);
    }

}
