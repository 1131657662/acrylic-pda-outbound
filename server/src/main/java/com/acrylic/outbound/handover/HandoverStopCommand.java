package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Strict command frozen by the originating PDA; route and recovery epoch participate in its digest. */
record HandoverStopCommand(UUID caseId, String epoch, UUID key, long caseVersion, UUID recallId,
        UUID packageId, String payloadHash, long captureHigh, long auditHigh, String canonical, Temporary temporary) {
    static final int MAX_BYTES = 8192;
    private static final Set<String> FIELDS = Set.of("commandId", "expectedCaseVersion", "changeRequestId",
        "recoveryPackageId", "payloadSha256", "captureHighWatermark", "auditThroughSeq");
    private static final Set<String> TEMP_FIELDS = Set.of("commandId", "expectedCaseVersion", "sourceKind",
        "sourceEvidenceSetId", "verificationId", "setSha256", "captureHighWatermark", "auditThroughSeq");

    static HandoverStopCommand parse(UUID caseId, String epoch, byte[] bytes) {
        try {
            require(caseId != null && epoch != null && epoch.matches("0|[1-9][0-9]{0,17}"));
            require(bytes != null && bytes.length > 0 && bytes.length <= MAX_BYTES);
            var body = SubmissionJson.parse(bytes);
            var fields = new HashSet<String>(); body.fieldNames().forEachRemaining(fields::add);
            boolean temporary = "TEMP".equals(body.path("sourceKind").asText());
            if (temporary && body.has("orderScanHighWatermark")) fields.remove("orderScanHighWatermark");
            require(body.isObject() && fields.equals(temporary ? TEMP_FIELDS : FIELDS));
            long version = SubmissionJson.number(body, "expectedCaseVersion"); require(version > 0);
            String hash = SubmissionJson.text(body, temporary ? "setSha256" : "payloadSha256"); require(hash.matches("[a-f0-9]{64}"));
            var bound = SubmissionJson.object().put("caseId", caseId.toString()).put("transportRecoveryEpoch", epoch);
            bound.set("command", body);
            return new HandoverStopCommand(caseId, epoch, SubmissionJson.uuid(body, "commandId"), version,
                temporary ? null : SubmissionJson.uuid(body, "changeRequestId"),
                temporary ? null : SubmissionJson.uuid(body, "recoveryPackageId"), hash,
                SubmissionJson.number(body, "captureHighWatermark"), SubmissionJson.number(body, "auditThroughSeq"),
                SubmissionJson.canonical(bound), temporary ? new Temporary(SubmissionJson.uuid(body, "sourceEvidenceSetId"),
                    SubmissionJson.uuid(body, "verificationId"), body.has("orderScanHighWatermark")
                        ? SubmissionJson.number(body, "orderScanHighWatermark") : null) : null);
        } catch (IllegalArgumentException | BusinessException invalid) {
            throw new BusinessException(400, "INVALID_HANDOVER_STOP", "交接停止命令格式无效");
        }
    }

    JsonNode bound() { return SubmissionJson.parse(canonical); }
    String digest() { return SubmissionJson.sha(bound()); }
    long inputHigh() { return temporary != null && temporary.orderHigh() != null ? temporary.orderHigh() : captureHigh; }
    ObjectNode acknowledgement() {
        return SubmissionJson.object().put("recoveryPackageId", packageId.toString()).put("payloadSha256", payloadHash)
            .put("captureHighWatermark", Long.toString(captureHigh)).put("auditThroughSeq", Long.toString(auditHigh));
    }
    record Temporary(UUID evidenceSet, UUID verification, Long orderHigh) { }
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException(); }
}
