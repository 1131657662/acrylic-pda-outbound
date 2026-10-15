package com.acrylic.outbound.e2averify;

import java.nio.file.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import static org.assertj.core.api.Assertions.*;

/** Synthetic nonempty predecessor, additive upgrade, then actual pre-upgrade restore without overwriting newer facts. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aUpgradeRecoveryIT {
    @Test void restoresPreUpgradeCheckpointWhileRetainingThePostCheckpointSource() throws Exception {
        var profile = new E2aCrossSourceProfile();
        profile.connect();
        try (var sourcePool = pool(profile)) {
            var jdbc = new JdbcTemplate(sourcePool);
            Files.createFile(profile.attempt.resolve("test-started.marker"));
            seedPredecessor(profile, jdbc);
            try (var checkpoint = new E2aUpgradeCheckpoint(profile, jdbc)) {
                var before = new E2aMigrationSnapshot(jdbc, profile.attempt, profile.attempt.resolve("storage")).capture();
                checkpoint.save(jdbc);
                upgrade(profile, jdbc, before);
                verifyRestoredCheckpoint(profile, jdbc, checkpoint);
                record(profile, before, checkpoint.capture(jdbc, profile.attempt.resolve("storage")));
            }
        }
    }

    private void verifyRestoredCheckpoint(E2aCrossSourceProfile profile, JdbcTemplate jdbc,
            E2aUpgradeCheckpoint checkpoint) throws Exception {
        var baseline = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
            profile.attempt.resolve("upgrade-checkpoint/facts.json").toFile());
        var probe = UUID.randomUUID();
        jdbc.update("INSERT INTO product(id,model,name) VALUES(UUID_TO_BIN(?),?,?)", probe.toString(),
            "UPGRADE-PROBE-" + probe, "Synthetic post-checkpoint probe; not an outbound fact");
        var upgraded = checkpoint.capture(jdbc, profile.attempt.resolve("storage"));
        var restored = checkpoint.restore();
        assertThat(checkpoint.capture(jdbc, profile.attempt.resolve("storage"))).isEqualTo(upgraded);
        assertThat(checkpoint.capture(restored, profile.attempt.resolve("upgrade-restored/storage"))).isEqualTo(baseline);
        assertThat(restored.queryForObject("SELECT COUNT(*) FROM product WHERE id=UUID_TO_BIN(?)",
            Integer.class, probe.toString())).isZero();
        assertThatThrownBy(checkpoint::restore).isInstanceOf(AssertionError.class).hasMessageContaining("restore target must be empty");
        assertThat(checkpoint.capture(restored, profile.attempt.resolve("upgrade-restored/storage")))
            .isEqualTo(baseline);
        assertThat(checkpoint.capture(jdbc, profile.attempt.resolve("storage"))).isEqualTo(upgraded);
    }

    private HikariDataSource pool(E2aCrossSourceProfile profile) {
        var settings = new HikariConfig();
        settings.setJdbcUrl(profile.url);
        settings.setUsername(profile.user);
        settings.setPassword(System.getenv("E2A_CROSS_PASSWORD"));
        settings.setMaximumPoolSize(2);
        settings.setMinimumIdle(0);
        settings.setConnectionTimeout(30_000);
        return new HikariDataSource(settings);
    }

    private void seedPredecessor(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "98");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        Flyway.configure().dataSource(jdbc.getDataSource()).target("98").load().validate();
    }

    private void upgrade(E2aCrossSourceProfile profile, JdbcTemplate jdbc,
            com.fasterxml.jackson.databind.JsonNode before) throws Exception {
        var result = Flyway.configure().dataSource(jdbc.getDataSource()).target("99").load().migrate();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        new E2aMigrationSnapshot(jdbc, profile.attempt, profile.attempt.resolve("storage")).assertPreserved(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM independent_association_member", Integer.class)).isZero();
        Flyway.configure().dataSource(jdbc.getDataSource()).target("99").load().validate();
    }

    private void record(E2aCrossSourceProfile profile, com.fasterxml.jackson.databind.JsonNode before,
            com.fasterxml.jackson.databind.JsonNode upgraded) throws Exception {
        Files.writeString(profile.attempt.resolve("upgrade-before.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(profile.attempt.resolve("upgrade-after.json"), upgraded.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Nonempty additive upgrade preserved legacy rows/schema/history/storage; pre-upgrade SQL and original signing bytes restored "
            + "into empty companion after upgrade; nonempty restore refused; post-checkpoint source retained. "
            + "Synthetic technical probe only; no old binary/client/production or full disaster reconstruction claim.\n",
            StandardOpenOption.CREATE_NEW);
    }
}
