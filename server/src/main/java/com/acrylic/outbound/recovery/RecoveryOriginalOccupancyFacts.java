package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original submission columns, excluding inferred history, passwords and present-day permissions. */
final class RecoveryOriginalOccupancyFacts {
    private RecoveryOriginalOccupancyFacts() { }

    static void payload(JsonNode payload, RecoverySourceIdentity id) {
        require(uuid(payload, "submissionId").equals(id.submissionId()) && uuid(payload, "freezeDeviceId").equals(id.deviceId())
            && revision(payload, "workRound") == id.workRound() && text(payload, "sourceRecoveryEpoch").equals(id.sourceRecoveryEpoch()),
            "RECOVERY_OCCUPANCY_CAPTURE_PAYLOAD_IDENTITY");
        var origin = payload.path("source");
        require(text(origin, "kind").equals(id.sourceKind()), "RECOVERY_OCCUPANCY_CAPTURE_PAYLOAD_IDENTITY");
        if (id.sourceKind().equals("FORMAL"))
            require(uuid(origin, "sourceOrderId").equals(id.canonicalOrderId()), "RECOVERY_OCCUPANCY_CAPTURE_PAYLOAD_IDENTITY");
        else require(uuid(origin, "creatorDeviceId").equals(id.creatorDeviceId()) && uuid(origin, "clientOrderId").equals(id.clientOrderId()),
            "RECOVERY_OCCUPANCY_CAPTURE_PAYLOAD_IDENTITY");
    }

    static String head(JsonNode head, RecoverySourceIdentity expected, String payloadHash) {
        var identity = source(expected);
        for (String field : List.of("scopeId", "warehouseId", "deviceId", "operatorId", "qualificationId", "canonicalOrderId",
                "creatorDeviceId", "clientOrderId", "workRound", "sourceRecoveryEpoch"))
            require(head.path(field).equals(identity.path(field)), "RECOVERY_OCCUPANCY_CAPTURE_IDENTITY");
        require(expected.submissionId().equals(uuid(head, "currentSubmissionId")) && revision(head, "scopeRound") == 1
            && head.path("continuationId").isNull() && head.path("supersededBy").isNull(), "RECOVERY_OCCUPANCY_CAPTURE_CHAIN");
        require("ABORTED".equals(text(head, "status")) && head.hasNonNull("receipt_text"), "RECOVERY_OCCUPANCY_CAPTURE_TERMINAL");
        require(payloadHash.equals(text(head, "payload_sha256")), "RECOVERY_OCCUPANCY_CAPTURE_PAYLOAD");
        return text(head, "receipt_text");
    }

    static ObjectNode source(RecoverySourceIdentity id) {
        var value = SubmissionJson.object().put("sourceKind", id.sourceKind()).put("warehouseId", id.warehouseId().toString())
            .put("scopeId", id.scopeId().toString()).put("submissionId", id.submissionId().toString())
            .put("deviceId", id.deviceId().toString()).put("operatorId", id.operatorId().toString())
            .put("qualificationId", id.qualificationId().toString()).put("sourceRecoveryEpoch", id.sourceRecoveryEpoch())
            .put("workRound", Long.toString(id.workRound())).put("captureHighWatermark", Long.toString(id.captureHighWatermark()))
            .put("auditThroughSeq", Long.toString(id.auditThroughSeq())).put("orderScanHighWatermark", Long.toString(id.orderScanHighWatermark()));
        nullable(value, "canonicalOrderId", id.canonicalOrderId()); nullable(value, "creatorDeviceId", id.creatorDeviceId());
        nullable(value, "clientOrderId", id.clientOrderId()); return value;
    }

    private static void nullable(ObjectNode value, String field, UUID id) {
        if (id == null) value.putNull(field); else value.put(field, id.toString());
    }
}
