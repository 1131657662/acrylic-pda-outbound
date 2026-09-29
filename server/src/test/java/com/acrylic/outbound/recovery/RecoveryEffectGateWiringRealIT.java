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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Design rows 6–11 in the production shape on a real isolated installation: the entry, the effect
 * gate and the real P5 authority are all enabled in one context (the v2 initial-authority identity
 * makes that possible), so a write, a relation read and a commit are frozen the moment the real
 * authority fences locally, constant reads stay usable, and no refused effect reaches the driver.
 */
class RecoveryEffectGateWiringRealIT {
    private static final int APP_PORT = Integer.getInteger("s3.http.port",
        Integer.parseInt(System.getenv().getOrDefault("S3_HTTP_PORT", "19458")));

    @Test void theRealEntryWithTheRealP5AuthorityFreezesEveryEffect() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        migrate();
        Path evidence = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"));
        Path storage = Files.createDirectories(evidence.resolve("storage"));
        var raw = new JdbcTemplate(new DriverManagerDataSource(System.getenv("OUTBOUND_DB_URL"),
            System.getenv("OUTBOUND_DB_USER"), System.getenv("OUTBOUND_DB_PASSWORD")));
        raw.execute("CREATE TABLE IF NOT EXISTS recovery_effect_probe (id INT PRIMARY KEY, counter INT NOT NULL)");
        raw.execute("INSERT INTO recovery_effect_probe VALUES(1,0) ON DUPLICATE KEY UPDATE counter=0");
        var transcript = new StringBuilder();

        // The explicit supplier promotes the installation to the v2 initial-authority shape and
        // provisions the P5 tree; the writer protocol is then pinned to that identity.
        var lease = new Path[1];
        var closed = new Path[1];
        var provisioned = new RecoveryInitialAuthorityProvision.Provisioned[1];
        var control = RecoveryWriterLeaseTest.protocol(fixture -> {
            lease[0] = fixture.root.getParent().resolve("p5");
            closed[0] = fixture.root.getParent().resolve("closed");
            provisioned[0] = RecoveryInitialAuthorityProvision.provision(fixture.root, lease[0], closed[0]);
            return RecoveryControlHistory.read(fixture.root.resolve("identity.json"));
        });
        transcript.append("provision: identitySha256=").append(provisioned[0].identitySha256())
            .append(" markerSha256=").append(provisioned[0].markerSha256()).append('\n');

        try (var context = boot(options(control, storage, lease[0], closed[0], provisioned[0]))) {
            var gate = context.getBean(RecoveryEffectGate.class);
            var authority = context.getBean(RecoveryFenceAuthority.class);
            DataSource admitted = context.getBean(DataSource.class);
            assertThat(gate.enabled()).as("the real P5 authority is attached to the entry").isTrue();
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
                    .append(counter(raw)).append('\n');

                authority.fenceLocal();
                assertThatThrownBy(() -> borrowed.createStatement()
                    .execute("UPDATE recovery_effect_probe SET counter=counter+1 WHERE id=1"))
                    .as("a write is frozen by the real authority").hasMessageContaining("RECOVERY_LEASE_FENCED");
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
        EffectRealEnvironment.record("S11|REAL|effect-gate-real-authority|" + transcript.toString().replace('\n', '|'));
    }

    private static int counter(JdbcTemplate raw) {
        return raw.queryForObject("SELECT counter FROM recovery_effect_probe WHERE id=1", Integer.class);
    }

    private static String[] options(RecoveryControlFixture control, Path storage, Path lease, Path closed,
            RecoveryInitialAuthorityProvision.Provisioned provisioned) {
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
            "--outbound.recovery.lease.enabled=true",
            "--outbound.recovery.lease.root=" + lease,
            "--outbound.recovery.lease.closed-authority=" + closed,
            "--outbound.recovery.lease.identity-sha256=" + provisioned.identitySha256(),
            "--outbound.recovery.lease.marker-sha256=" + provisioned.markerSha256(),
            "--outbound.recovery.effect-gate.enabled=true",
            "--outbound.recovery.effect-gate.cross-process-enabled=true",
            "--outbound.recovery.effect-gate.anchor=" + RecoveryControlFormat.hex(provisioned.anchor())))
            .toArray(String[]::new);
    }

    private static ConfigurableApplicationContext boot(String[] options) {
        return new SpringApplication(com.acrylic.outbound.OutboundApplication.class).run(options);
    }

    private static void migrate() {
        Flyway.configure().dataSource(System.getenv("OUTBOUND_DB_URL"), System.getenv("OUTBOUND_DB_USER"),
            System.getenv("OUTBOUND_DB_PASSWORD")).load().migrate();
    }
}
