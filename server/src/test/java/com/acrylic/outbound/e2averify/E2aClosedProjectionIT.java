package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryExportChecks;
import com.acrylic.outbound.recovery.RecoveryNormalChecks;
import com.acrylic.outbound.recovery.RecoveryProjectionChecks;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aClosedProjectionIT {
    @Test void preservesNonemptyOriginalsWhileProjectingOnlyClosedControl() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker")); profile.migrate(jdbc,"34");
        E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage")); profile.migrate(jdbc,"38");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        var snapshots = new E2aMigrationSnapshot(jdbc, profile.attempt, profile.attempt.resolve("storage"));
        var before = snapshots.capture(); save(profile, "projection-before-migration.json", before);
        profile.migrate(jdbc,"39"); snapshots.assertPreserved(before);
        profile.migrate(jdbc,"40"); snapshots.assertPreserved(before);
        for (String table : java.util.List.of("recovery_installation_binding", "recovery_control_epoch", "recovery_control_projection"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_normal_admission",Integer.class)).isZero();
        com.acrylic.outbound.recovery.RecoveryTransactionChecks.verify(jdbc, profile.attempt);
        existingHttp(profile);
        var original = snapshots.capture(); save(profile, "projection-before-control.json", original);
        var control = new com.acrylic.outbound.recovery.RecoveryTransactionFixture(profile.attempt.resolve("projection"));
        long projected = jdbc.queryForObject("SELECT recovery_epoch FROM server_state", Long.class);
        RecoveryProjectionChecks.verify(jdbc, control, profile.attempt,
            (template, fixture) -> RecoveryNormalChecks.afterReleaseReady(template, fixture, profile.attempt));
        // the overflow drill in the fault checks leaves a synthetic Long.MAX_VALUE epoch behind
        RecoveryNormalChecks.restoreEpoch(jdbc, projected);        var after = snapshots.capture(); save(profile, "projection-after-control.json", after);
        original.path("tables").fields().forEachRemaining(entry -> {
            if (!entry.getKey().equals("server_state") && !entry.getKey().startsWith("recovery_control_")
                    && !entry.getKey().equals("recovery_installation_binding")
                    && !entry.getKey().equals("recovery_normal_admission"))
                assertThat(after.path("tables").path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        });
        assertThat(after.path("files")).isEqualTo(original.path("files"));
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "V34->V40 nonempty migration; durable close admission and transaction drain; closed projection; initial NORMAL admission; existing HTTP compatibility only\n");
    }

    private void existingHttp(E2aCrossSourceProfile profile) throws Exception {
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        Path renderer=Path.of("../report-rendering/build/install/acrylic-report-rendering").toAbsolutePath().normalize();
        try(var context=E2aCrossSourceHttpIT.start(profile,"--outbound.exports.sample-data=true","--server.servlet.session.cookie.secure=false",
                "--outbound.exports.renderer-home="+renderer,
                "--outbound.exports.browsers=D:/acrylic-pda-worktrees/r08-export/report-rendering/build/browsers")) {
            new TemporaryExportChecks((WebApplicationContext)context).verify(profile.attempt.resolve("exports-verified"));
            E2aTemporaryExportBrowser.verify(profile.attempt);
        }
    }

    private void save(E2aCrossSourceProfile profile, String name, com.fasterxml.jackson.databind.JsonNode value) throws Exception {
        Files.writeString(profile.attempt.resolve(name), new com.fasterxml.jackson.databind.ObjectMapper()
            .writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardOpenOption.CREATE_NEW);
    }
}
