package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Strict external byte inventory; its pin must be supplied independently of the restore set. */
final class RecoverySourceManifest {
    static final List<String> OBJECTS = List.of("origin", "payload", "result", "occupancy", "permission");
    static final long MAX_OBJECT_BYTES = 64L * 1024 * 1024;
    private RecoverySourceManifest() { }

    static void requireBinding(JsonNode value, RecoveryControlSnapshot control, RecoverySourceIdentity expected) {
        fields(value, "formatVersion installationId recoverySessionId epochId snapshotId backupManifestSha256 "
            + "possibleLossFrom possibleLossThrough source objects");
        version(value);
        require(uuid(value, "installationId").equals(control.installationId())
            && uuid(value, "recoverySessionId").equals(control.recoverySessionId())
            && uuid(value, "epochId").equals(control.epochId())
            && text(value, "snapshotId").equals(control.snapshotId())
            && text(value, "backupManifestSha256").equals(control.manifestSha256())
            && instant(value, "possibleLossFrom").equals(control.possibleLossFrom())
            && instant(value, "possibleLossThrough").equals(control.possibleLossThrough()), "RECOVERY_SOURCE_CONTROL_MISMATCH");
        require(expected.equals(RecoverySourceIdentity.read(value.path("source"))), "RECOVERY_SOURCE_IDENTITY_MISMATCH");
        require(control.affectedWarehouseIds().contains(expected.warehouseId().toString()), "RECOVERY_SOURCE_WAREHOUSE_UNKNOWN");
        fields(value.path("objects"), String.join(" ", OBJECTS));
        for (String kind : OBJECTS) descriptor(value.path("objects").path(kind));
    }

    private static void descriptor(JsonNode value) {
        fields(value, "sha256 byteCount"); hashField(value, "sha256");
        require(!ZERO_HASH.equals(text(value, "sha256")), "RECOVERY_SOURCE_OBJECT_HASH");
        long bytes = revision(value, "byteCount");
        require(bytes > 0 && bytes <= MAX_OBJECT_BYTES, "RECOVERY_SOURCE_OBJECT_SIZE");
    }
}
