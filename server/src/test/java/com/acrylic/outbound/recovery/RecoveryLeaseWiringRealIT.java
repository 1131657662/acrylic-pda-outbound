package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rows 8–9 on a real isolated installation: the application takes the explicitly supplied P5 root
 * (identity verification, fence channel, process record and lifecycle lock), publishes a lease
 * through the real authority, and refuses to start again while that lease is an orphan.
 */
class RecoveryLeaseWiringRealIT {
    private static final int APP_PORT = Integer.getInteger("s3.http.port",
        Integer.parseInt(System.getenv().getOrDefault("S3_HTTP_PORT", "19458")));

    @Test void theRealApplicationTakesTheP5RootAndRefusesAnOrphanLease() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        migrate();
        Path evidence = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"));
        Path storage = Files.createDirectories(evidence.resolve("storage"));
        var transcript = new StringBuilder();

        // Control authority and checkpoint come from the same synthetic originals the admission
        // slices use; its identity is extended to the v2 initial-authority shape the P5 binding
        // demands (the independent supplier that writes that shape is still pending, see the record).
        var control = RecoveryWriterLeaseTest.protocol();
        Path base = control.location().root().getParent();
        Path lease = base.resolve("p5");
        Path closed = base.resolve("closed");
        byte[] anchor = new byte[32];
        new java.security.SecureRandom().nextBytes(anchor);
        Path identityPath = control.location().root().resolve("identity.json");
        ObjectNode identity = (ObjectNode) RecoveryControlHistory.read(identityPath);
        identity.put("formatVersion", 2).put("authorityKind", "INITIAL_NORMAL_INSTALL")
            .put("closedAuthority", closed.toString()).put("leaseRoot", lease.toString());
        RecoveryControlFixture.write(identityPath, identity);
        String identityHash = RecoveryControlFormat.hash(identity);
        var binding = new RecoveryLeaseRoots.Binding(control.location(), lease, closed, identityHash);
        String marker = RecoveryLeaseRoots.provision(binding, anchor);
        transcript.append("provision: leaseRoot=").append(lease).append(" closedAuthority=").append(closed)
            .append(" identitySha256=").append(identityHash).append(" markerSha256=").append(marker)
            .append('\n');

        String[] options = options(control, storage, lease, closed, identityHash, marker, anchor);
        try (var context = boot(options)) {
            var gate = context.getBean(RecoveryEffectGate.class);
            var authority = context.getBean(RecoveryFenceAuthority.class);
            assertThat(context.getBean(RecoveryLeaseLifecycle.class).authority()).isSameAs(authority);
            assertThat(gate.enabled()).as("the real authority is attached to the effect gate").isTrue();
            gate.check(); // real fence probe + orphan scan against the supplied tree
            transcript.append("boot1: started, gate enabled, checkEffect=ok, tree=")
                .append(tree(lease)).append('\n');

            var operation = authority.registerOperation();
            transcript.append("publish: operation=").append(operation).append(" leases=").append(tree(lease))
                .append('\n');
            authority.retire();
            transcript.append("retire: lifecycle lock released, lease left without a live process\n");
        }

        // The published lease outlives the process that owned it: the next start must refuse instead
        // of adopting it.
        String refusal = bootFailure(options);
        assertThat(refusal).as("an orphan lease refuses the next start")
            .contains("RECOVERY_LEASE_OUTSTANDING");
        transcript.append("boot2: refused with ").append(refusal).append('\n');

        Files.writeString(evidence.resolve("lease-wiring.txt"), transcript.toString());
        EffectRealEnvironment.record("S9|REAL|lease-wiring|" + transcript.toString().replace('\n', '|'));
    }

    private static String[] options(RecoveryControlFixture control, Path storage, Path lease, Path closed,
            String identityHash, String marker, byte[] anchor) {
        return new ArrayList<>(List.of(
            "--server.address=127.0.0.1", "--server.port=" + APP_PORT,
            "--spring.flyway.enabled=false", "--spring.session.jdbc.cleanup-cron=-",
            "--outbound.worker.enabled=false", "--outbound.bootstrap.enabled=false",
            "--outbound.storage=" + storage,
            "--outbound.recovery.root=" + control.location().root(),
            "--outbound.recovery.installation-id=" + control.location().installation(),
            "--outbound.recovery.restore-roots[0]=" + control.location().restoreRoots().get(0),
            "--outbound.recovery.checkpoint=" + control.checkpoint,
            "--outbound.recovery.lease.enabled=true",
            "--outbound.recovery.lease.root=" + lease,
            "--outbound.recovery.lease.closed-authority=" + closed,
            "--outbound.recovery.lease.identity-sha256=" + identityHash,
            "--outbound.recovery.lease.marker-sha256=" + marker,
            "--outbound.recovery.effect-gate.enabled=true",
            "--outbound.recovery.effect-gate.cross-process-enabled=true",
            "--outbound.recovery.effect-gate.anchor=" + RecoveryControlFormat.hex(anchor)))
            .toArray(String[]::new);
    }

    private static ConfigurableApplicationContext boot(String[] options) {
        return new SpringApplication(com.acrylic.outbound.OutboundApplication.class).run(options);
    }

    private static String bootFailure(String[] options) {
        try (var ignored = boot(options)) {
            throw new AssertionError("the start should have been refused");
        } catch (AssertionError wrong) {
            throw wrong;
        } catch (RuntimeException refused) {
            StringBuilder message = new StringBuilder();
            for (Throwable current = refused; current != null; current = current.getCause()) {
                message.append(current.getMessage()).append(" | ");
            }
            return message.toString();
        }
    }

    private static String tree(Path lease) throws Exception {
        var parts = new ArrayList<String>();
        try (var paths = Files.walk(lease)) {
            paths.filter(Files::isRegularFile).sorted().forEach(path ->
                parts.add(lease.relativize(path).toString().replace('\\', '/')));
        }
        return parts.toString();
    }

    private static void migrate() {
        Flyway.configure().dataSource(System.getenv("OUTBOUND_DB_URL"), System.getenv("OUTBOUND_DB_USER"),
            System.getenv("OUTBOUND_DB_PASSWORD")).load().migrate();
    }
}
