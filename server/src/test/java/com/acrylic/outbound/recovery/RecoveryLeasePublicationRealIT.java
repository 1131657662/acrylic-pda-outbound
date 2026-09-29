package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Design rows 10 and 12 in the production shape on a real isolated installation: every admitted root
 * publishes a P5 lease record, and once the process that owned them is gone the records are orphans
 * — the next process refuses them, retires them, and only then may a further process start.
 */
class RecoveryLeasePublicationRealIT {
    private static final int APP_PORT = Integer.getInteger("s3.http.port",
        Integer.parseInt(System.getenv().getOrDefault("S3_HTTP_PORT", "19458")));

    @Test void anAdmittedRootPublishesALeaseRecordAndTheNextProcessRetiresItsOrphan() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        migrate();
        Path evidence = Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR"));
        Path storage = Files.createDirectories(evidence.resolve("storage"));
        var transcript = new StringBuilder();

        var lease = new Path[1];
        var closed = new Path[1];
        var provisioned = new RecoveryInitialAuthorityProvision.Provisioned[1];
        var control = RecoveryWriterLeaseTest.protocol(fixture -> {
            lease[0] = fixture.root.getParent().resolve("p5");
            closed[0] = fixture.root.getParent().resolve("closed");
            provisioned[0] = RecoveryInitialAuthorityProvision.provision(fixture.root, lease[0], closed[0]);
            return RecoveryControlHistory.read(fixture.root.resolve("identity.json"));
        });
        String[] options = options(control, storage, lease[0], closed[0], provisioned[0]);

        UUID published;
        try (var context = boot(options)) {
            var registry = context.getBean(RecoveryOperationRegistry.class);
            var root = registry.admit(new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
                registry.startup().installationId(), registry.startup().recordSha256(), null));
            published = root.leaseId();
            assertThat(published).as("the admitted root published a P5 lease record").isNotNull();
            assertThat(leaseRecords(lease[0])).as("booting plus admitting left records behind")
                .contains(published + ".json");
            assertThat(indexed(lease[0])).contains(published.toString());
            transcript.append("boot1: records=").append(leaseRecords(lease[0])).append(" index=")
                .append(indexed(lease[0])).append('\n');
            root.unbindAll();
            root.close();
        }

        // The owning process retired its lifecycle lock when the context closed, so every record it
        // published is an orphan now: the next process must refuse, retire and only then be followed
        // by a process that starts.
        String refused = child(control, lease[0], closed[0], provisioned[0], true);
        assertThat(refused).as("the next process refuses the orphan").contains("RECOVERY_LEASE_OUTSTANDING");
        transcript.append("child1: refused=").append(refused).append('\n');
        transcript.append("afterRetirement: records=").append(leaseRecords(lease[0])).append(" index=")
            .append(indexed(lease[0])).append('\n');
        assertThat(leaseRecords(lease[0])).as("the orphan was retired").doesNotContain(published + ".json");
        assertThat(indexed(lease[0])).doesNotContain(published.toString());

        String started = child(control, lease[0], closed[0], provisioned[0], false);
        assertThat(started).as("once the orphan is retired a further process starts").contains("STARTED");
        transcript.append("child2: ").append(started).append('\n');

        Files.writeString(evidence.resolve("lease-publication.txt"), transcript.toString());
        EffectRealEnvironment.record("S12|REAL|lease-publication|" + transcript.toString().replace('\n', '|'));
    }

    private static String child(RecoveryControlFixture control, Path lease, Path closed,
            RecoveryInitialAuthorityProvision.Provisioned provisioned, boolean expectFailure) throws Exception {
        var executable = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        var command = new ArrayList<String>(List.of(executable.toString(), "-cp", System.getProperty("java.class.path"),
            RecoveryLeaseStartupCheck.class.getName(), control.location().root().toString(), lease.toString(),
            closed.toString(), provisioned.identitySha256(), provisioned.markerSha256(),
            RecoveryControlFormat.hex(provisioned.anchor()),
            control.location().installation().toString()));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (!expectFailure && exit != 0) throw new AssertionError("child failed: " + exit + " " + output);
        return output.strip();
    }

    private static List<String> leaseRecords(Path lease) throws Exception {
        try (var entries = Files.list(lease.resolve("leases"))) {
            return entries.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".json")
                && !name.equals("index.json")).sorted().toList();
        }
    }

    private static List<String> indexed(Path lease) throws Exception {
        var value = RecoveryControlHistory.read(lease.resolve("leases").resolve("index.json"));
        var entries = new ArrayList<String>();
        value.path("entries").forEach(entry -> entries.add(entry.asText()));
        return entries;
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
