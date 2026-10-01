package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aCorrectionAccountingMigrationIT {
    @Test void nonemptyUpgradeRetainsOriginalsAndEnforcesPostingReferences() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "53"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var fixture = new CorrectionAccountingMigrationFixture(profile, jdbc);
        var snapshots = new CorrectionAccountingMigrationOriginals(jdbc, profile.attempt);
        var before = snapshots.snapshot("accounting-upgrade-before");
        assertThat(before.get("`inventory_ledger`")).isNotEmpty();
        assertThat(before.get("`dispatch_record`")).isNotEmpty();
        assertThat(before.get("`order_result_version`")).isNotEmpty();
        assertThat(before.get("`correction_ledger`")).hasSize(2);
        var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
        assertThat(snapshots.snapshot("accounting-upgrade-after")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_ledger WHERE reverses_inventory_entry_id IS NOT NULL"
            + " OR restores_entry_id IS NOT NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank").subList(0, history.size()))
            .usingRecursiveComparison().isEqualTo(history);
        Flyway.configure().dataSource(jdbc.getDataSource()).load().migrate();
        assertThat(snapshots.snapshot("accounting-upgrade-repeated")).isEqualTo(before);
        new CorrectionAccountingMigrationChecks(fixture, profile.attempt).references();
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Nonempty upgrade/repeat retained all original columns; exact UNIQUE/FK/CHECK errors and append-only reference guards passed");
    }
}
