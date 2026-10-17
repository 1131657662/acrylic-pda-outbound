package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** One synthetic candidate: nonempty upgrade, program rollback reads, then pre-upgrade restore reads. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aUpgradeRollbackCombinationIT {
    @Test void preservesNonemptyFactsAcrossUpgradeProgramRollbackAndCheckpointRestore() throws Exception {
        var profile = new E2aCrossSourceProfile(); profile.connect();
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(profile.url); config.setUsername(profile.user);
        config.setPassword(System.getenv("E2A_CROSS_PASSWORD")); config.setMaximumPoolSize(2); config.setMinimumIdle(0);
        try (var pool = new com.zaxxer.hikari.HikariDataSource(config)) { verify(profile, new JdbcTemplate(pool)); }
    }

    private void verify(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        var seed = seed(profile, jdbc);
        var reader = addReader(jdbc, seed.get("warehouseId"));
        Files.writeString(profile.attempt.resolve("reader-input.json"), new ObjectMapper().writeValueAsString(
            Map.of("id", reader.id(), "username", reader.username(), "seed", seed)), StandardOpenOption.CREATE_NEW);
        try (var checkpoint = new E2aUpgradeCheckpoint(profile, jdbc)) {
            checkpoint.save(jdbc);
            Files.writeString(profile.attempt.resolve("checkpoint-full.json"), E2aProgramReadFacts.capture(checkpoint,
                jdbc, profile.attempt.resolve("storage")).toPrettyString(), StandardOpenOption.CREATE_NEW);
            var programs = new E2aUpgradeRollbackProgram(profile, checkpoint, seed, reader);
            var source = new E2aUpgradeRollbackProgram.Target(jdbc, profile.url,
                profile.attempt.resolve("storage"), profile.attempt.resolve("private"));
            programs.read("pre-old", "old", source); programs.read("pre-new", "new", source);
            upgrade(profile, jdbc);
            programs.read("post-new", "new", source); programs.read("post-old", "old", source);
            programs.read("post-new-again", "new", source);
            restore(profile, jdbc, checkpoint, programs);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Synthetic nonempty upgrade + pinned old/new JAR real HTTP reads + original checkpoint restore reads. "
            + "No old write/worker, product UI, production identity, full D15 or final L3 claim.\n", StandardOpenOption.CREATE_NEW);
    }

    private Map<String, String> seed(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        Files.createDirectories(profile.attempt.resolve("tmp"));
        profile.migrate(jdbc, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "98");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        return seed;
    }

    private void upgrade(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        var snapshots = new E2aMigrationSnapshot(jdbc, profile.attempt, profile.attempt.resolve("storage"));
        var before = snapshots.capture();
        Files.writeString(profile.attempt.resolve("upgrade-before.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        var result = Flyway.configure().dataSource(jdbc.getDataSource()).target("99").load().migrate();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        snapshots.assertPreserved(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM independent_association_member", Integer.class)).isZero();
        Flyway.configure().dataSource(jdbc.getDataSource()).target("99").load().validate();
        Files.writeString(profile.attempt.resolve("upgrade-after.json"), snapshots.capture().toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private void restore(E2aCrossSourceProfile profile, JdbcTemplate jdbc, E2aUpgradeCheckpoint checkpoint,
            E2aUpgradeRollbackProgram programs) throws Exception {
        var source = E2aProgramReadFacts.capture(checkpoint, jdbc, profile.attempt.resolve("storage"));
        var restored = checkpoint.restore();
        assertThat(E2aProgramReadFacts.capture(checkpoint, restored, profile.attempt.resolve("upgrade-restored/storage")))
            .isEqualTo(new ObjectMapper().readTree(profile.attempt.resolve("checkpoint-full.json").toFile()));
        var target = new E2aUpgradeRollbackProgram.Target(restored,
            profile.url.replace("/" + profile.database + "?", "/" + profile.database + "_restore?"),
            profile.attempt.resolve("upgrade-restored/storage"), profile.attempt.resolve("upgrade-restored/config"));
        programs.read("restored-old", "old", target); programs.read("restored-new", "new", target);
        var after = E2aProgramReadFacts.capture(checkpoint, restored, target.storage());
        assertThatThrownBy(checkpoint::restore).isInstanceOf(AssertionError.class).hasMessageContaining("restore target must be empty");
        assertThat(E2aProgramReadFacts.capture(checkpoint, restored, target.storage())).isEqualTo(after);
        assertThat(E2aProgramReadFacts.capture(checkpoint, jdbc, profile.attempt.resolve("storage"))).isEqualTo(source);
        Files.writeString(profile.attempt.resolve("restore-refused.marker"), "Nonempty target refused; source and companion unchanged.\n",
            StandardOpenOption.CREATE_NEW);
    }

    private E2aUpgradeRollbackProgram.Reader addReader(JdbcTemplate jdbc, String warehouse) {
        var id = UUID.randomUUID(); var role = UUID.randomUUID();
        String username = "d15-reader-" + id.toString().substring(0, 8);
        String password = UUID.randomUUID() + "Aa1!";
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).executeWithoutResult(status -> {
            assertThat(jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) "
                + "VALUES(UUID_TO_BIN(?),?,?,?,UTC_TIMESTAMP(3))", id.toString(), username, "SYNTHETIC read only",
                new BCryptPasswordEncoder().encode(password))).isEqualTo(1);
            assertThat(jdbc.update("INSERT INTO role VALUES(UUID_TO_BIN(?),?,?)", role.toString(),
                "D15_" + id, "Synthetic isolated order reader")).isEqualTo(1);
            assertThat(jdbc.update("INSERT INTO role_permission SELECT UUID_TO_BIN(?),id FROM permission WHERE code='order:read'",
                role.toString())).isEqualTo(1);
            assertThat(jdbc.update("INSERT INTO user_role VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))", id.toString(), role.toString())).isEqualTo(1);
            assertThat(jdbc.update("INSERT INTO user_warehouse VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))", id.toString(), warehouse)).isEqualTo(1);
        });
        return new E2aUpgradeRollbackProgram.Reader(id, username, password);
    }
}
