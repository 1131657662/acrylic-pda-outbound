package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** A pre-existing formal recovery package, never a replacement source-device acknowledgement. */
record HandoverFormalArchive(UUID recall, UUID recovery, String payloadHash, long captureHigh, long auditHigh, long inputHigh) {
    static HandoverFormalArchive read(JsonNode reference) {
        var fields = new HashSet<String>(); reference.fieldNames().forEachRemaining(fields::add);
        if (!reference.isObject() || !fields.equals(Set.of("sourceKind", "changeRequestId", "recoveryPackageId",
                "payloadSha256", "captureHighWatermark", "auditThroughSeq", "orderScanHighWatermark"))) throw changed();
        if (!"FORMAL".equals(text(reference, "sourceKind"))) throw changed();
        var result = new HandoverFormalArchive(uuid(reference, "changeRequestId"), uuid(reference, "recoveryPackageId"),
            text(reference, "payloadSha256"), number(reference, "captureHighWatermark"),
            number(reference, "auditThroughSeq"), number(reference, "orderScanHighWatermark"));
        if (!result.payloadHash().matches("[a-f0-9]{64}") || result.auditHigh() == 0) throw changed();
        return result;
    }

    JsonNode descriptor(HandoverStopStore.Origin origin) {
        if (origin.temporary() != null || origin.assignment() == null) throw changed();
        return object().put("sourceKind", "FORMAL").put("status", "VERIFIED_COMPLETE_AT_STOP")
            .put("changeRequestId", recall.toString()).put("recoveryPackageId", recovery.toString()).put("payloadSha256", payloadHash)
            .put("submissionId", origin.submission().toString()).put("assignmentId", origin.assignment().toString())
            .put("workRound", Long.toString(origin.round())).put("captureHighWatermark", Long.toString(captureHigh))
            .put("auditThroughSeq", Long.toString(auditHigh)).put("orderScanHighWatermark", Long.toString(inputHigh))
            .put("mayResumeScanning", false).put("stockReleased", false);
    }

    static BusinessException changed() {
        return new BusinessException(409, "HANDOVER_FAULT_ARCHIVE_INVALID", "正式来源故障归档不完整或已变化，请保留原资料核对");
    }
}
