package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY", matches="true")
class E2aCorrectionIdentityMigrationIT {
    @Test void nonemptyMigrationRetainsOriginalIdentitiesAndRejectsPrimaryKeyRewrites() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "52");
        var fixture = new CorrectionIdentityFixture(jdbc);
        var snapshots = new CorrectionAttachmentSqlChecks(jdbc, profile.attempt); var tables = snapshots.tables();
        var before = snapshots.snapshot(tables, "identity-before");
        assertThat(before.get("correction_document")).hasSize(1);
        assertThat(before.get("inventory_reconciliation")).hasSize(1);
        fixture.oldSchemaAllowsIdentityChange();
        assertThat(snapshots.snapshot(tables, "identity-control-rollback")).isEqualTo(before);
        var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        profile.migrate(jdbc, "53");
        assertThat(snapshots.snapshot(tables, "identity-after")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank").subList(0, history.size()))
            .usingRecursiveComparison().isEqualTo(history);
        profile.migrate(jdbc, "53");
        assertThat(snapshots.snapshot(tables, "identity-repeated-migration")).isEqualTo(before);
        var checks = new CorrectionIdentityChecks(fixture, profile.attempt);
        checks.identities(); checks.lifecycle();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Childless identity control rolled back; nonempty migration preserved; exact SQLSTATE/message and lifecycle passed");
    }
}
