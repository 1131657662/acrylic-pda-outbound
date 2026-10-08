package com.acrylic.outbound.handover;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** References existing origin evidence. This value is never an originating device command or acknowledgement. */
record HandoverTemporaryArchive(UUID evidenceSet, UUID verification, String payloadHash,
        long captureHigh, long auditHigh, long inputHigh) {
    static HandoverTemporaryArchive fromDevice(HandoverStopCommand command) {
        return new HandoverTemporaryArchive(command.temporary().evidenceSet(), command.temporary().verification(),
            command.payloadHash(), command.captureHigh(), command.auditHigh(), command.inputHigh());
    }

    static HandoverTemporaryArchive read(JsonNode reference) {
        return new HandoverTemporaryArchive(uuid(reference, "sourceEvidenceSetId"), uuid(reference, "verificationId"),
            text(reference, "setSha256"), number(reference, "captureHighWatermark"), number(reference, "auditThroughSeq"),
            number(reference, "orderScanHighWatermark"));
    }

    JsonNode descriptor(HandoverStopStore.Origin origin) {
        return object().put("sourceKind", "TEMP").put("status", "VERIFIED_COMPLETE_AT_STOP")
            .put("sourceEvidenceSetId", evidenceSet.toString()).put("verificationId", verification.toString())
            .put("setSha256", payloadHash).put("submissionId", origin.submission().toString())
            .put("workRound", Long.toString(origin.round())).put("authorizationId", origin.temporary().authorization().toString())
            .put("captureHighWatermark", Long.toString(captureHigh)).put("auditThroughSeq", Long.toString(auditHigh))
            .put("orderScanHighWatermark", Long.toString(inputHigh)).put("mayResumeScanning", false).put("stockReleased", false);
    }
}
