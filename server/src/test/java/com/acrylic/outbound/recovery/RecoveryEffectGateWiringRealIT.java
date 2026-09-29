package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Design row 11 on a real isolated installation: the application's own primary data source carries
 * the effect gate, so a write, a relation read and a commit are frozen as soon as the attached
 * authority denies, constant reads stay usable, and no refused effect reaches the driver.
 *
 * <p>The authority here is a test authority on purpose: the admission path and the P5 cross-process
 * layer demand mutually exclusive identity field sets today (see
 * {@link RecoveryIdentityFormatConflictTest} and the run record), so the real P5 authority is
 * evidenced separately by {@link RecoveryLeaseWiringRealIT}.
 */
class RecoveryEffectGateWiringRealIT {
    private static final int APP_PORT = Integer.getInteger("s3.http.port",
        Integer.parseInt(System.getenv().getOrDefault("S3_HTTP_PORT", "19458")));

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthoritySource {
        static RecoveryEffectGate.Authority authority;

        @Bean RecoveryEffectGate.Authority recoveryEffectAuthority() { return authority; }
    }

    @Test void theRealEntryFreezesEveryEffectOnceTheAttachedAuthorityDenies() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        migrate();
        Path evidence = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"));
        Path storage = Files.createDirectories(evidence.resolve("storage"));
        var raw = new JdbcTemplate(new DriverManagerDataSource(System.getenv("OUTBOUND_DB_URL"),
            System.getenv("OUTBOUND_DB_USER"), System.getenv("OUTBOUND_DB_PASSWORD")));
        raw.execute("CREATE TABLE IF NOT EXISTS recovery_effect_probe (id INT PRIMARY KEY, counter INT NOT NULL)");
        raw.execute("INSERT INTO recovery_effect_probe VALUES(1,0) ON DUPLICATE KEY UPDATE counter=0");
        var transcript = new StringBuilder();

        var control = RecoveryWriterLeaseTest.protocol();
        var authority = new EffectTestAuthority();
        AuthoritySource.authority = authority;

        try (var context = boot(options(control, storage))) {
            var gate = context.getBean(RecoveryEffectGate.class);
            DataSource admitted = context.getBean(DataSource.class);
            assertThat(gate.enabled()).as("the gate bean is attached to the primary data source").isTrue();
            assertThat(admitted).isInstanceOf(RecoveryRequestAdmittedDataSource.class);

            var registry = context.getBean(RecoveryOperationRegistry.class);
            var root = registry.admit(new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
                registry.startup().installationId(), registry.startup().recordSha256(), null));
            root.bindDispatch(Thread.currentThread());
            try (Connection borrowed = admitted.getConnection()) {
                borrowed.createStatement().execute("SELECT 1");
                borrowed.setAutoCommit(false);
                borrowed.createStatement().execute("UPDATE recovery_effect_probe SET counter=counter+1 WHERE id=1");
                borrowed.commit();
                transcript.append("unfenced: constant read ok, write ok, commit ok, counter=")
                    .append(counter(raw)).append(" checks=").append(authority.checks).append('\n');

                authority.denial = "RECOVERY_LEASE_FENCED";
                assertThatThrownBy(() -> borrowed.createStatement()
                    .execute("UPDATE recovery_effect_probe SET counter=counter+1 WHERE id=1"))
                    .as("a write is frozen by the attached gate").hasMessageContaining("RECOVERY_LEASE_FENCED");
                assertThatThrownBy(() -> borrowed.createStatement()
                    .execute("SELECT counter FROM recovery_effect_probe WHERE id=1"))
                    .as("a relation read is frozen as well (a view can mutate)")
                    .hasMessageContaining("RECOVERY_LEASE_FENCED");
                assertThatThrownBy(borrowed::commit).hasMessageContaining("RECOVERY_LEASE_FENCED");
                borrowed.createStatement().execute("SELECT 1");
                transcript.append("fenced: write refused, relation read refused, commit refused, constant read ok")
                    .append("\ncounterAfterFence=").append(counter(raw))
                    .append(" (unchanged, the driver never saw the refused effects)\n");
            } finally {
                root.unbindAll();
                root.close();
            }
        }

        Files.writeString(evidence.resolve("effect-gate-wiring.txt"), transcript.toString());
        EffectRealEnvironment.record("S10|REAL|effect-gate-wiring|" + transcript.toString().replace('\n', '|'));
    }

    private static int counter(JdbcTemplate raw) {
        return raw.queryForObject("SELECT counter FROM recovery_effect_probe WHERE id=1", Integer.class);
    }

    private static String[] options(RecoveryControlFixture control, Path storage) {
        return new ArrayList<>(List.of(
            "--server.address=127.0.0.1", "--server.port=" + APP_PORT,
            "--spring.flyway.enabled=false", "--spring.session.jdbc.cleanup-cron=-",
            "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false",
            "--outbound.storage=" + storage,
            "--outbound.recovery.admission.enabled=true",
            "--outbound.recovery.root=" + control.location().root(),
            "--outbound.recovery.installation-id=" + control.location().installation(),
            "--outbound.recovery.restore-roots[0]=" + control.location().restoreRoots().get(0),
            "--outbound.recovery.checkpoint=" + control.checkpoint,
            "--outbound.recovery.effect-gate.enabled=true")).toArray(String[]::new);
    }

    private static ConfigurableApplicationContext boot(String[] options) {
        return new SpringApplication(com.acrylic.outbound.OutboundApplication.class, AuthoritySource.class)
            .run(options);
    }

    private static void migrate() {
        Flyway.configure().dataSource(System.getenv("OUTBOUND_DB_URL"), System.getenv("OUTBOUND_DB_USER"),
            System.getenv("OUTBOUND_DB_PASSWORD")).load().migrate();
    }
}
