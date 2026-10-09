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
        this(kind, round, RecoveryControlFixture.create(), true);
    }

    private RecoverySourceFixture(String kind, long round, RecoveryControlFixture supplied, boolean advance) throws Exception {
        control = supplied;
        UUID device = UUID.randomUUID();
        identity = new RecoverySourceIdentity(kind, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            device, UUID.randomUUID(), UUID.randomUUID(), kind.equals("FORMAL") ? UUID.randomUUID() : null,
            kind.equals("TEMP") ? (round == 1 ? device : UUID.randomUUID()) : null,
            kind.equals("TEMP") ? UUID.randomUUID() : null, "1", round, 7, 12, 9);
        bindWarehouse(); if (advance) control.append(control.next("RECOVERING"));
        materials = Files.createDirectory(control.root.getParent().resolve("external-materials"));
        manifest = manifest(); publish();
    }

    static RecoverySourceFixture create() throws Exception { return new RecoverySourceFixture("FORMAL"); }
    static RecoverySourceFixture temporary() throws Exception { return new RecoverySourceFixture("TEMP"); }
    static RecoverySourceFixture initial(String kind) throws Exception { return new RecoverySourceFixture(kind, 1); }
    static RecoverySourceFixture original(RecoverySourceIdentity identity) throws Exception {
        var fixture = new RecoverySourceFixture(RecoveryControlFixture.create(), identity);
        fixture.bindWarehouse(); fixture.advance(); return fixture;
    }
    static RecoverySourceFixture forRestore(Path restored) throws Exception {
        return new RecoverySourceFixture("FORMAL", 1, RecoveryControlFixture.forRestore(restored), false);
    }

    void advance() throws Exception {
        control.append(control.next("RECOVERING")); manifest.put("epochId", control.read().epochId().toString()); publish();
    }

    static RecoverySourceFixture related(RecoverySourceFixture current, String kind) throws Exception {
        var own = current.identity;
        var identity = new RecoverySourceIdentity(kind, own.warehouseId(), UUID.randomUUID(), UUID.randomUUID(),
            own.deviceId(), UUID.randomUUID(), UUID.randomUUID(), kind.equals("FORMAL") ? UUID.randomUUID() : null,
            kind.equals("TEMP") ? own.deviceId() : null, kind.equals("TEMP") ? UUID.randomUUID() : null,
            own.sourceRecoveryEpoch(), 1, 7, 12, 9);
        return new RecoverySourceFixture(current.control, identity);
    }

    static RecoverySourceFixture credentialCopy(RecoverySourceFixture current) throws Exception {
        return new RecoverySourceFixture(current.control, current.identity);
    }

    private RecoverySourceFixture(RecoveryControlFixture control, RecoverySourceIdentity identity) throws Exception {
        this.control = control; this.identity = identity;
        materials = Files.createDirectory(control.root.getParent().resolve("external-materials-" + UUID.randomUUID()));
        manifest = manifest(); publish();
    }

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
