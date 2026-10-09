package com.acrylic.outbound.recovery;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.pairing.PairingCommands;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Synthetic observation kept outside a synthetic restore set, with in-memory development device keys. */
final class RecoveryDeviceRegistrationFixture {
    final RecoveryNativeTemporaryFixture nativeSource = new RecoveryNativeTemporaryFixture();
    final RecoverySourceFixture source = nativeSource.initial.state.authority.source;
    final UUID mysqlServer = UUID.randomUUID();
    final Path materials = Files.createDirectory(source.control.root.getParent().resolve("original-device-registration"));
    final ObjectNode value = SubmissionJson.object().put("formatVersion", 1).put("observationKind", "ORIGINAL_DEVICE_REGISTRATION")
        .put("mysqlServerUuid", mysqlServer.toString()).put("databaseSchema", "acrylic_original")
        .put("sourceRecoveryEpoch", "1").put("observedAt", "2026-09-19T00:00:00Z").putNull("pairing");
    String pin;

    RecoveryDeviceRegistrationFixture() throws Exception {
        var id = source.identity; String key = Base64.getEncoder().encodeToString(nativeSource.evidence.key.getPublic().getEncoded());
        value.putObject("device").put("warehouseId", id.warehouseId().toString()).put("deviceId", id.deviceId().toString())
            .put("publicKey", key).put("keyTextSha256", DeviceCrypto.sha(key)).put("registeredAt", "2026-09-18T00:00:00Z")
            .put("deviceVersion", "7").put("status", "ACTIVE").put("temporaryOrderEnabled", true);
        publish();
    }

    void paired() throws Exception {
        var id = source.identity; var app = UUID.randomUUID(); var device = value.path("device");
        var approval = new PairingCommands.Approval(app, id.warehouseId(), "原仓", "DEV-1", "开发合成设备",
            device.path("keyTextSha256").asText(), "1");
        var row = value.putObject("pairing").put("applicationId", app.toString()).put("status", "COMPLETED")
            .put("deviceId", id.deviceId().toString()).put("publicKey", device.path("publicKey").asText())
            .put("keyTextSha256", approval.keySha256()).put("approvedBy", UUID.randomUUID().toString()).put("approvedAuthVersion", "3")
            .put("approvalSha256", DeviceCrypto.sha(approval.binding()));
        row.set("approval", RecoveryControlFixture.JSON.valueToTree(approval)); publish();
    }

    void publish() throws Exception {
        byte[] bytes = RecoveryControlJson.encode(value); pin = RecoveryControlFormat.sha256(bytes);
        Files.write(materials.resolve(pin + ".json"), bytes);
    }

    RecoveryDeviceRegistration.Expected expected() {
        var id = source.identity;
        return new RecoveryDeviceRegistration.Expected(mysqlServer, "acrylic_original", "1", id.warehouseId(), id.deviceId(),
            Instant.parse("2026-09-20T00:00:00Z"));
    }
    RecoveryDeviceRegistration.Request request() { return new RecoveryDeviceRegistration.Request(materials, pin, expected()); }
    RecoveryDeviceRegistration reader() { return new RecoveryDeviceRegistration(source.control.location(), source.control.checkpoint); }
    RecoveryDeviceRegistration.Proof read() { return reader().read(request()); }
    ObjectNode device() { return (ObjectNode)value.path("device"); }
    ObjectNode pairing() { return (ObjectNode)value.path("pairing"); }
}
