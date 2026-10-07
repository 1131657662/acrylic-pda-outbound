package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundStopProof;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Binds a complete E2A device STOP to an already aborted TEMP round; it never releases stock. */
@Component
final class HandoverTemporaryStopEvidence {
    private final SubmissionStore submissions;
    private final TemporaryRoundStopProof proof;
    private final JdbcTemplate jdbc;

    HandoverTemporaryStopEvidence(SubmissionStore submissions, TemporaryRoundStopProof proof, JdbcTemplate jdbc) {
        this.submissions = submissions; this.proof = proof; this.jdbc = jdbc;
    }

    JsonNode verify(HandoverStopCommand command, HandoverStopStore.Origin origin) {
        var head = submissions.required(origin.submission());
        if (!"ABORTED".equals(head.status())) throw new BusinessException(409, "HANDOVER_ORIGINAL_RESULT_OPEN",
            "原临时批次须先停止并取得中止回执，才能登记交接停止证据");
        var request = object().put("verificationId", command.temporary().verification().toString())
            .put("sourceEvidenceSetId", command.temporary().evidenceSet().toString()).put("setSha256", command.payloadHash());
        proof.require(head, request);
        var rows = jdbc.queryForList("""
            SELECT capture_high_watermark,order_scan_high_watermark,audit_through_seq FROM source_evidence_set
            WHERE id=? AND submission_id=? AND scope_id=? AND work_round=? AND warehouse_id=?
            """, Ids.bytes(command.temporary().evidenceSet()), Ids.bytes(origin.submission()),
            Ids.bytes(origin.scope()), origin.round(), Ids.bytes(origin.warehouse()));
        if (rows.size() != 1 || ((Number) rows.getFirst().get("capture_high_watermark")).longValue() != command.captureHigh()
                || ((Number) rows.getFirst().get("order_scan_high_watermark")).longValue() != command.inputHigh()
                || ((Number) rows.getFirst().get("audit_through_seq")).longValue() != command.auditHigh())
            throw new BusinessException(409, "HANDOVER_STOP_CONTEXT_CHANGED", "TEMP 停止证据高水位或来源不符");
        return descriptor(command, origin);
    }

    static JsonNode descriptor(HandoverStopCommand command, HandoverStopStore.Origin origin) {
        HandoverCommand.corruptUnless(command.temporary() != null && origin.temporary() != null);
        var result = object().put("sourceKind", "TEMP").put("status", "VERIFIED_COMPLETE_AT_STOP")
            .put("sourceEvidenceSetId", command.temporary().evidenceSet().toString())
            .put("verificationId", command.temporary().verification().toString()).put("setSha256", command.payloadHash())
            .put("submissionId", origin.submission().toString()).put("workRound", Long.toString(origin.round()))
            .put("authorizationId", origin.temporary().authorization().toString())
            .put("captureHighWatermark", Long.toString(command.captureHigh())).put("auditThroughSeq", Long.toString(command.auditHigh()))
            .put("mayResumeScanning", false).put("stockReleased", false);
        if (command.temporary().orderHigh() != null) result.put("orderScanHighWatermark", Long.toString(command.inputHigh()));
        return result;
    }
}
