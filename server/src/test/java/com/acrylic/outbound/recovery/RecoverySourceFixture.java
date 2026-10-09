package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Retained synthetic control and original files; no database or originating-device proof. */
final class RecoverySourceFixture {
    final RecoveryControlFixture control;
    final RecoverySourceIdentity identity;
    final Path materials;
    final ObjectNode manifest;
    String pin;

    private RecoverySourceFixture(String kind) throws Exception { this(kind, 2); }

    private RecoverySourceFixture(String kind, long round) throws Exception {
        control = RecoveryControlFixture.create();
        identity = new RecoverySourceIdentity(kind, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), kind.equals("FORMAL") ? UUID.randomUUID() : null,
            kind.equals("TEMP") ? UUID.randomUUID() : null, kind.equals("TEMP") ? UUID.randomUUID() : null, "1", round, 7, 12, 9);
        bindWarehouse(); control.append(control.next("RECOVERING"));
        materials = Files.createDirectory(control.root.getParent().resolve("external-materials"));
        manifest = manifest(); publish();
    }

    static RecoverySourceFixture create() throws Exception { return new RecoverySourceFixture("FORMAL"); }
    static RecoverySourceFixture temporary() throws Exception { return new RecoverySourceFixture("TEMP"); }
    static RecoverySourceFixture initial(String kind) throws Exception { return new RecoverySourceFixture(kind, 1); }

    private void bindWarehouse() throws Exception {
        control.command.putArray("affectedWarehouseIds").add(identity.warehouseId().toString());
        control.latest.set("command", control.command.deepCopy());
        control.latest.put("commandSha256", hash(control.command));
        control.latest.remove("recordSha256"); control.latest.put("recordSha256", hash(control.latest));
        var metadata = (ObjectNode)RecoveryControlHistory.read(control.root.resolve("identity.json"));
        metadata.put("initialCommandSha256", hash(control.command));
        RecoveryControlFixture.write(control.root.resolve("identity.json"), metadata);
        RecoveryControlFixture.write(control.root.resolve("history/000000000000000001.json"), control.latest);
        RecoveryControlFixture.write(control.root.resolve("current.json"), RecoveryControlFixture.pointer(control.latest));
        RecoveryControlFixture.write(control.checkpoint, RecoveryControlFixture.pointer(control.latest));
    }

    private ObjectNode manifest() throws Exception {
        var snapshot = control.read();
        var value = RecoveryControlFixture.JSON.createObjectNode().put("formatVersion", 1)
            .put("installationId", snapshot.installationId().toString()).put("recoverySessionId", snapshot.recoverySessionId().toString())
            .put("epochId", snapshot.epochId().toString()).put("snapshotId", snapshot.snapshotId())
            .put("backupManifestSha256", snapshot.manifestSha256()).put("possibleLossFrom", snapshot.possibleLossFrom().toString())
            .put("possibleLossThrough", snapshot.possibleLossThrough().toString());
        value.set("source", source()); var objects = value.putObject("objects");
        for (String kind : RecoverySourceManifest.OBJECTS) {
            byte[] bytes = ("synthetic original " + kind + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String hash = sha256(bytes); Files.write(materials.resolve(hash + ".bin"), bytes);
            objects.putObject(kind).put("sha256", hash).put("byteCount", Integer.toString(bytes.length));
        }
        return value;
    }

    private ObjectNode source() {
        var value = RecoveryControlFixture.JSON.createObjectNode().put("sourceKind", identity.sourceKind())
            .put("warehouseId", identity.warehouseId().toString()).put("scopeId", identity.scopeId().toString())
            .put("submissionId", identity.submissionId().toString()).put("deviceId", identity.deviceId().toString())
            .put("operatorId", identity.operatorId().toString()).put("qualificationId", identity.qualificationId().toString())
            .put("sourceRecoveryEpoch", identity.sourceRecoveryEpoch()).put("workRound", Long.toString(identity.workRound()))
            .put("captureHighWatermark", Long.toString(identity.captureHighWatermark()))
            .put("auditThroughSeq", Long.toString(identity.auditThroughSeq()))
            .put("orderScanHighWatermark", Long.toString(identity.orderScanHighWatermark()));
        if (identity.canonicalOrderId() == null) value.putNull("canonicalOrderId");
        else value.put("canonicalOrderId", identity.canonicalOrderId().toString());
        if (identity.creatorDeviceId() == null) value.putNull("creatorDeviceId");
        else value.put("creatorDeviceId", identity.creatorDeviceId().toString());
        if (identity.clientOrderId() == null) value.putNull("clientOrderId");
        else value.put("clientOrderId", identity.clientOrderId().toString());
        return value;
    }

    void publish() throws Exception {
        pin = hash(manifest); RecoveryControlFixture.write(materials.resolve(pin + ".json"), manifest);
    }

    Path object(String kind) { return materials.resolve(manifest.at("/objects/" + kind + "/sha256").asText() + ".bin"); }
    RecoverySourceReader reader() { return new RecoverySourceReader(control.location(), control.checkpoint); }
    RecoveryPinnedSource read() { return reader().read(materials, pin, identity); }
}
