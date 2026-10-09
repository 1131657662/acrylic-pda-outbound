package com.acrylic.outbound.recovery;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.pairing.PairingCommands;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.PublicKey;
import java.time.Instant;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Strict original registration observation; neither a permission window nor a current identity. */
final class RecoveryDeviceRegistrationFacts {
    private RecoveryDeviceRegistrationFacts() { }

    static PublicKey verify(JsonNode value, RecoveryDeviceRegistration.Expected expected) {
        fields(value, "formatVersion observationKind mysqlServerUuid databaseSchema sourceRecoveryEpoch observedAt device pairing");
        version(value);
        require("ORIGINAL_DEVICE_REGISTRATION".equals(text(value, "observationKind")), "RECOVERY_DEVICE_REGISTRATION_KIND");
        require(uuid(value, "mysqlServerUuid").equals(expected.mysqlServerUuid())
            && text(value, "databaseSchema").equals(expected.databaseSchema())
            && text(value, "sourceRecoveryEpoch").equals(expected.sourceRecoveryEpoch()), "RECOVERY_DEVICE_REGISTRATION_DATABASE");
        Instant observed = instant(value, "observedAt");
        require(!observed.isAfter(expected.notAfter()), "RECOVERY_DEVICE_REGISTRATION_TOO_LATE");
        var device = value.path("device");
        fields(device, "warehouseId deviceId publicKey keyTextSha256 registeredAt deviceVersion status temporaryOrderEnabled");
        require(uuid(device, "warehouseId").equals(expected.warehouseId()) && uuid(device, "deviceId").equals(expected.deviceId()),
            "RECOVERY_DEVICE_REGISTRATION_IDENTITY");
        require(!instant(device, "registeredAt").isAfter(observed) && revision(device, "deviceVersion") > 0
            && java.util.Set.of("ACTIVE", "DISABLED").contains(text(device, "status"))
            && device.path("temporaryOrderEnabled").isBoolean(), "RECOVERY_DEVICE_REGISTRATION_STATE");
        String encoded = text(device, "publicKey");
        require(DeviceCrypto.sha(encoded).equals(text(device, "keyTextSha256")), "RECOVERY_DEVICE_REGISTRATION_HASH");
        if (!value.path("pairing").isNull()) pairing(value.path("pairing"), expected, encoded);
        try { return DeviceCrypto.publicKey(encoded); }
        catch (RuntimeException malformed) { throw new RecoveryControlException("RECOVERY_DEVICE_REGISTRATION_KEY", malformed); }
    }

    private static void pairing(JsonNode row, RecoveryDeviceRegistration.Expected expected, String encoded) {
        fields(row, "applicationId status deviceId publicKey keyTextSha256 approvedBy approvedAuthVersion approval approvalSha256");
        require("COMPLETED".equals(text(row, "status")) && uuid(row, "deviceId").equals(expected.deviceId())
            && text(row, "publicKey").equals(encoded) && DeviceCrypto.sha(encoded).equals(text(row, "keyTextSha256")),
            "RECOVERY_DEVICE_REGISTRATION_PAIRING");
        uuid(row, "approvedBy");
        require(revision(row, "approvedAuthVersion") > 0, "RECOVERY_DEVICE_REGISTRATION_PAIRING");
        var value = row.path("approval");
        fields(value, "applicationId warehouseId warehouseName deviceCode name keySha256 serverRecoveryEpoch");
        var approval = new PairingCommands.Approval(uuid(value, "applicationId"), uuid(value, "warehouseId"),
            text(value, "warehouseName"), text(value, "deviceCode"), text(value, "name"), text(value, "keySha256"),
            text(value, "serverRecoveryEpoch"));
        require(approval.applicationId().equals(uuid(row, "applicationId")) && approval.warehouseId().equals(expected.warehouseId())
            && approval.keySha256().equals(DeviceCrypto.sha(encoded)) && revision(value, "serverRecoveryEpoch") > 0
            && revision(value, "serverRecoveryEpoch") <= Long.parseLong(expected.sourceRecoveryEpoch())
            && DeviceCrypto.sha(approval.binding()).equals(text(row, "approvalSha256")), "RECOVERY_DEVICE_REGISTRATION_APPROVAL");
    }
}
