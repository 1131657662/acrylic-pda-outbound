package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Run-owned synthetic P5 supply; control advancement stays in the separate IT JVM. */
public final class RecoveryRestoredP5StaleAuthority {
    private final Path home;
    private final RecoveryControlFixture control;
    private final RecoveryInitialAuthorityProvision.Provisioned pin;

    public RecoveryRestoredP5StaleAuthority(Path attempt) throws Exception {
        home = Files.createDirectory(attempt.resolve("private/p5-stale-authority"));
        control = RecoveryControlFixture.retainedAt(home.resolve("control"), attempt.resolve("data"));
        pin = RecoveryInitialAuthorityProvision.provision(control.root, home.resolve("p5"), home.resolve("closed"));
        protocol();
        RecoveryRetirementProcess.config(home, control, pin);
        save("control-before.json", control.latest);
    }

    private void protocol() throws Exception {
        Files.writeString(control.root.resolve("writers.lock"), "0", StandardOpenOption.CREATE_NEW);
        Files.createDirectory(control.root.resolve("intents"));
        RecoveryControlFixture.write(control.root.resolve("admission.json"), RecoveryControlFixture.JSON.createObjectNode()
            .put("formatVersion", 1).put("installationId", control.installation.toString())
            .put("targetRevision", "1").put("intentSha256", RecoveryControlFormat.ZERO_HASH));
        var descriptor = RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 2)
            .put("installationId", control.installation.toString()).put("identitySha256", pin.identitySha256())
            .put("fileIdentity", RecoveryWriterProtocol.fileIdentity(control.root.resolve("writers.lock")));
        RecoveryControlFixture.write(control.root.resolve("writers.json"), descriptor);
    }

    public String[] options() {
        return new String[]{"--outbound.recovery.admission.enabled=true",
            "--outbound.recovery.root=" + control.root, "--outbound.recovery.installation-id=" + control.installation,
            "--outbound.recovery.restore-roots[0]=" + control.restoreRoot,
            "--outbound.recovery.checkpoint=" + control.checkpoint,
            "--outbound.recovery.lease.enabled=true", "--outbound.recovery.lease.root=" + home.resolve("p5"),
            "--outbound.recovery.lease.closed-authority=" + home.resolve("closed"),
            "--outbound.recovery.lease.identity-sha256=" + pin.identitySha256(),
            "--outbound.recovery.lease.marker-sha256=" + pin.markerSha256(),
            "--outbound.recovery.effect-gate.enabled=true", "--outbound.recovery.effect-gate.cross-process-enabled=true",
            "--outbound.recovery.effect-gate.anchor=" + RecoveryControlFormat.hex(pin.anchor())};
    }

    public void advance() throws Exception {
        var receipt = RecoveryControlLock.lockedWaiting(control.root, 10000, () -> {
            try (var writer = RecoveryWriterLease.acquireClosed(control.root)) {
                control.append(control.next("RECOVERING"));
                return control.latest.deepCopy().put("testProducerPid", ProcessHandle.current().pid())
                    .put("exclusiveWriterHeld", writer.confirmed()).put("coordinatorHeld", true);
            }
        });
        assertThat(receipt.path("revision").asText()).isEqualTo("2");
        assertThat(control.read().ordinaryWritesAllowed()).isFalse();
        save("advance.json", receipt);
    }

    public ObjectNode snapshot(String label) throws Exception {
        var snapshot = RecoveryControlFixture.JSON.createObjectNode();
        try (var files = Files.walk(home.resolve("p5"))) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                var fact = snapshot.putObject(home.resolve("p5").relativize(path).toString().replace('\\', '/'));
                fact.put("bytes", Files.size(path)).put("sha256", RecoveryControlFormat.sha256(Files.readAllBytes(path)));
            }
        }
        var index = RecoveryControlHistory.read(home.resolve("p5/leases/index.json"));
        assertThat(index.path("entries")).isEmpty();
        try (var files = Files.list(home.resolve("p5/leases"))) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("index.json");
        }
        save(label + "-p5.json", snapshot); return snapshot;
    }

    private void save(String name, com.fasterxml.jackson.databind.JsonNode value) throws Exception {
        Files.writeString(home.resolve(name), value.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
