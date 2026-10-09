package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Expected original identity supplied by a trusted caller, never inferred from restored bytes. */
public record RecoverySourceIdentity(String sourceKind, UUID warehouseId, UUID scopeId, UUID submissionId,
        UUID deviceId, UUID operatorId, UUID qualificationId, UUID canonicalOrderId, UUID creatorDeviceId,
        UUID clientOrderId, String sourceRecoveryEpoch,
        long workRound, long captureHighWatermark, long auditThroughSeq, long orderScanHighWatermark) {
    public RecoverySourceIdentity {
        require("FORMAL".equals(sourceKind) || "TEMP".equals(sourceKind), "RECOVERY_SOURCE_KIND");
        for (UUID id : new UUID[]{warehouseId, scopeId, submissionId, deviceId, operatorId, qualificationId})
            require(id != null && !id.equals(new UUID(0, 0)), "RECOVERY_SOURCE_IDENTITY");
        require(canonicalOrderId == null ? "TEMP".equals(sourceKind) : !canonicalOrderId.equals(new UUID(0, 0)),
            "RECOVERY_SOURCE_ORDER");
        if ("TEMP".equals(sourceKind)) {
            for (UUID id : new UUID[]{creatorDeviceId, clientOrderId})
                require(id != null && !id.equals(new UUID(0, 0)), "RECOVERY_SOURCE_TEMP_IDENTITY");
        } else require(creatorDeviceId == null && clientOrderId == null, "RECOVERY_SOURCE_TEMP_IDENTITY");
        require(sourceRecoveryEpoch != null && sourceRecoveryEpoch.matches("0|[1-9][0-9]{0,17}"), "RECOVERY_SOURCE_EPOCH");
        require(workRound > 0 && captureHighWatermark >= 0 && auditThroughSeq > 0 && orderScanHighWatermark >= 0,
            "RECOVERY_SOURCE_WATERMARK");
    }

    static RecoverySourceIdentity read(JsonNode value) {
        fields(value, "sourceKind warehouseId scopeId submissionId deviceId operatorId qualificationId canonicalOrderId creatorDeviceId clientOrderId "
            + "sourceRecoveryEpoch workRound captureHighWatermark auditThroughSeq orderScanHighWatermark");
        return new RecoverySourceIdentity(text(value, "sourceKind"), uuid(value, "warehouseId"), uuid(value, "scopeId"),
            uuid(value, "submissionId"), uuid(value, "deviceId"), uuid(value, "operatorId"), uuid(value, "qualificationId"),
            nullable(value, "canonicalOrderId"), nullable(value, "creatorDeviceId"), nullable(value, "clientOrderId"), text(value, "sourceRecoveryEpoch"),
            revision(value, "workRound"), revision(value, "captureHighWatermark"), revision(value, "auditThroughSeq"),
            revision(value, "orderScanHighWatermark"));
    }

    private static UUID nullable(JsonNode value, String key) { return value.path(key).isNull() ? null : uuid(value, key); }
}
