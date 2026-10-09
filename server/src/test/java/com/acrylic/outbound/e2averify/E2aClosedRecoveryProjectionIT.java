package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.recovery.RecoveryProjectionChecks;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Closed projection regression only; no signing, NORMAL registration, export or business recovery. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aClosedRecoveryProjectionIT {
    @Test void projectsEveryClosedPhaseWithPinnedAuthorityAndPreservesNonemptyBusinessFacts() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage")); profile.migrate(jdbc);
        var snapshots = new E2aMigrationSnapshot(jdbc, profile.attempt, profile.attempt.resolve("storage"));
        var before = snapshots.capture();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        RecoveryProjectionChecks.verify(jdbc, profile.attempt);
        var after = snapshots.capture();
        before.path("tables").fields().forEachRemaining(entry -> {
            String table = entry.getKey();
            if (!table.equals("server_state") && !table.equals("recovery_installation_binding")
                    && !table.startsWith("recovery_control_"))
                assertThat(after.path("tables").path(table)).as(table).isEqualTo(entry.getValue());
        });
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        assertThat(jdbc.queryForObject("SELECT service_mode FROM server_state WHERE id=1", String.class))
            .isEqualTo("RECOVERY_PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_normal_admission", Integer.class)).isZero();
        Files.writeString(profile.attempt.resolve("closed-projection-before.json"), before.toPrettyString());
        Files.writeString(profile.attempt.resolve("closed-projection-after.json"), after.toPrettyString());
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Nonempty closed projection regression; independent Python control writer + synthetic closed authority; no signing/reconstruction/release\n");
    }
}
