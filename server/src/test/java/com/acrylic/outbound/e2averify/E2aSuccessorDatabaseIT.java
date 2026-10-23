package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoverySuccessorDatabaseFixture;
import com.acrylic.outbound.recovery.RecoverySuccessorDatabaseChecks;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** A real nonempty dump with old immutable binding/NORMAL, restored into a fresh schema. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aSuccessorDatabaseIT {
    @Test void appendsTwoRoundsWithoutReplacingTheOriginalDatabaseEvidence() throws Exception {
        var profile = new E2aCrossSourceProfile(); var source = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(source, "34"); E2aV34Seed.seed(source, profile.attempt.resolve("storage"));
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        profile.migrate(source, "99");
        var fixture = new RecoverySuccessorDatabaseFixture(source, profile.attempt.resolve("successor-control"));
        var originals = new E2aMigrationSnapshot(source, profile.attempt, profile.attempt.resolve("storage")).capture();
        profile.migrate(source);
        var migrated = new E2aMigrationSnapshot(source, profile.attempt, profile.attempt.resolve("storage")).capture();
        originals.path("tables").fields().forEachRemaining(entry -> {
            if (!entry.getKey().equals("flyway_schema_history")) assertThat(migrated.path("tables").path(entry.getKey())).isEqualTo(entry.getValue());
        });
        for (String table : List.of("recovery_successor_binding", "recovery_successor_normal"))
            assertThat(source.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
        assertThat(source.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        var copier = new E2aArchiveSnapshotRestore(profile, source); var target = copier.restore();
        var sourceBefore = copier.snapshot(source, profile.attempt.resolve("storage"));
        var originalControl = files(profile.attempt.resolve("successor-control"));
        RecoverySuccessorDatabaseChecks.verify(source, target, fixture, profile.attempt);
        var targetAfter = copier.snapshot(target, copier.restored.resolve("storage"));
        sourceBefore.path("tables").fields().forEachRemaining(entry -> {
            if (!List.of("server_state", "recovery_control_projection", "recovery_control_epoch", "recovery_successor_binding",
                    "recovery_successor_normal").contains(entry.getKey()))
                assertThat(targetAfter.path("tables").path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        });
        assertThat(targetAfter.path("files")).isEqualTo(sourceBefore.path("files"));
        assertThat(copier.snapshot(source, profile.attempt.resolve("storage"))).isEqualTo(sourceBefore);
        for (var original : originalControl.entrySet())
            assertThat(files(profile.attempt.resolve("successor-control")).get(original.getKey())).as(original.getKey()).isEqualTo(original.getValue());
        save(profile, "source-before.json", sourceBefore); save(profile, "source-final.json", copier.snapshot(source, profile.attempt.resolve("storage")));
        save(profile, "target-final.json", targetAfter);
        save(profile, "control-originals.json", com.acrylic.outbound.submissions.SubmissionJson.object().set("files",
            new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(originalControl)));
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Synthetic full dump with original binding/NORMAL; two append-only rounds and single-SELECT read-only runtime admission; not API/worker/P5 composition", StandardOpenOption.CREATE_NEW);
    }

    private static Map<String, String> files(Path root) throws Exception {
        var result = new TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                if (name.endsWith("coordinator.lock") || name.endsWith("writers.lock") || name.endsWith("current.json")
                    || name.endsWith("/admission.json") || name.contains("/closed-")) continue;
                result.put(name, E2aPointInTimeCheckpoint.sha(path));
            }
        }
        return result;
    }

    private static void save(E2aCrossSourceProfile profile, String name, com.fasterxml.jackson.databind.JsonNode value) throws Exception {
        Files.writeString(profile.attempt.resolve(name), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
