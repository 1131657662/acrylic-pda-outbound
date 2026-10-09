package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit synthetic supplier. Does not represent a retained operator's authority or original device. */
final class RecoveryClosedTestSupply {
    private static final java.util.Map<String, RecoveryClosedTestSupply> SUPPLIES = new java.util.HashMap<>();
    final RecoveryControlLocation location;
    final Path checkpoint, root;
    final ObjectNode identity;
    final RecoveryClosedAuthority.Source source;
    String pin;

    RecoveryClosedTestSupply(RecoveryControlLocation location, Path checkpoint, RecoveryClosedAuthority.Source source)
            throws Exception {
        this.location = location; this.checkpoint = checkpoint; this.source = source;
        root = Files.createDirectory(location.root().getParent().resolve("closed-" + UUID.randomUUID()));
        Files.createDirectory(root.resolve("transactions"));
        byte[] header = new byte[32]; new java.security.SecureRandom().nextBytes(header);
        Path witness = root.resolve("transaction-witness.bin"); Files.write(witness, header);
        var control = new RecoveryControlReader(location).read(checkpoint);
        identity = RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 1)
            .put("authorityKind", "CLOSED_RECOVERY_SESSION").put("authorityRoot", root.toString())
            .put("installationId", control.installationId().toString())
            .put("recoverySessionId", control.recoverySessionId().toString()).put("epochId", control.epochId().toString())
            .put("controlRevision", control.revision()).put("controlRecordSha256", control.recordSha256())
            .put("previousRecordSha256", control.previousRecordSha256()).put("sourcePinSha256", source.manifestSha256())
            .put("witnessFileIdentity", RecoveryWriterProtocol.fileIdentity(witness)).put("witnessHeaderSha256", sha256(header));
        identity.set("source", RecoveryControlFixture.JSON.valueToTree(source.identity()));
        // Record serialization uses numeric watermarks; the strict external protocol uses strings.
        for (String key : new String[]{"workRound", "captureHighWatermark", "auditThroughSeq", "orderScanHighWatermark"})
            ((ObjectNode)identity.path("source")).put(key, identity.path("source").path(key).asText());
        publish();
    }

    static synchronized RecoveryClosedTestSupply of(RecoverySourceFixture source) throws Exception {
        String key = source.control.root + ":" + source.control.read().recordSha256();
        if (!SUPPLIES.containsKey(key)) SUPPLIES.put(key, new RecoveryClosedTestSupply(source.control.location(),
            source.control.checkpoint, new RecoveryClosedAuthority.Source(source.pin, source.identity)));
        return SUPPLIES.get(key);
    }

    static synchronized RecoveryClosedTestSupply control(RecoveryControlLocation location, Path checkpoint) throws Exception {
        String key = location.root() + ":" + new RecoveryControlReader(location).read(checkpoint).recordSha256();
        if (SUPPLIES.containsKey(key)) return SUPPLIES.get(key);
        var source = new RecoverySourceIdentity("FORMAL", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null, "1", 1, 0, 1, 0);
        var supply = new RecoveryClosedTestSupply(location, checkpoint, new RecoveryClosedAuthority.Source("c".repeat(64), source));
        SUPPLIES.put(key, supply); return supply;
    }

    void publish() throws Exception {
        pin = hash(identity); RecoveryControlFixture.write(root.resolve("identity.json"), identity);
        Path sessions = location.root().resolve("closed-sessions"); Files.createDirectories(sessions);
        RecoveryControlFixture.write(sessions.resolve(new RecoveryControlReader(location).read(checkpoint).recordSha256() + ".json"),
            RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 1)
                .put("authorityRoot", root.toString()).put("authoritySha256", pin));
    }

    RecoveryClosedAuthority.Pins pins() { return new RecoveryClosedAuthority.Pins(root, pin, source); }
    RecoveryClosedAuthority authority() { return new RecoveryClosedAuthority(location, checkpoint, pins()); }

    static void writers(RecoveryControlFixture control) throws Exception {
        Files.writeString(control.root.resolve("writers.lock"), "0");
        Files.createDirectory(control.root.resolve("intents"));
        RecoveryControlFixture.write(control.root.resolve("admission.json"), RecoveryControlFixture.JSON.createObjectNode()
            .put("formatVersion", 1).put("installationId", control.installation.toString())
            .put("targetRevision", "1").put("intentSha256", ZERO_HASH));
        RecoveryControlFixture.write(control.root.resolve("writers.json"), RecoveryControlFixture.JSON.createObjectNode()
            .put("formatVersion", 2).put("installationId", control.installation.toString())
            .put("identitySha256", hash(RecoveryControlHistory.read(control.root.resolve("identity.json"))))
            .put("fileIdentity", RecoveryWriterProtocol.fileIdentity(control.root.resolve("writers.lock"))));
        for (int revision = 2; revision <= Integer.parseInt(control.latest.path("revision").asText()); revision++) {
            var prior = RecoveryControlHistory.read(control.root.resolve("history/" + String.format("%018d.json", revision - 1)));
            var current = RecoveryControlHistory.read(control.root.resolve("history/" + String.format("%018d.json", revision)));
            var intent = RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 1)
                .put("installationId", control.installation.toString()).put("targetRevision", Integer.toString(revision))
                .put("previousRecordSha256", text(prior, "recordSha256"))
                .put("commandSha256", text(current, "commandSha256"));
            intent.set("command", current.path("command")); intent.put("intentSha256", hash(intent));
            RecoveryControlFixture.write(control.root.resolve("intents/" + String.format("%018d.json", revision)), intent);
            var header = RecoveryControlFixture.JSON.createObjectNode();
            for (String key : RecoveryControlIntents.HEAD_FIELDS.split(" ")) header.set(key, intent.path(key));
            RecoveryControlFixture.write(control.root.resolve("admission.json"), header);
        }
    }
}
