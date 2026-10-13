package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Verifies an immutable physical application original without requiring final dispatch. */
@Component
final class FormalQuarantineAppliedReceipt {
    private final JdbcTemplate jdbc;
    private final SubmissionArchiveProofs signatures;
    FormalQuarantineAppliedReceipt(JdbcTemplate jdbc,SubmissionArchiveProofs signatures) { this.jdbc=jdbc;this.signatures=signatures; }
    JsonNode read(JsonNode origin,JsonNode body,String payloadHash) {
        var rows=jdbc.queryForList("SELECT *,DATE_FORMAT(applied_at,'%Y-%m-%dT%H:%i:%s.%fZ') applied_at_utc FROM formal_disposition_application WHERE submission_id=UUID_TO_BIN(?)",origin.path("submissionId").asText());
        if(rows.isEmpty()) return null;
        if(rows.size()!=1) throw CorrectionCommand.corrupt();var row=rows.getFirst();var bytes=(byte[])row.get("receipt_bytes");
        if(!CorrectionCommand.sha(bytes).equals(row.get("receipt_sha256")) || !payloadHash.equals(row.get("payload_sha256"))) throw CorrectionCommand.corrupt();
        var receipt=SubmissionJson.parse(bytes);signatures.verify(receipt,"signedApplication","acrylic-pda-disposition-application","application");
        if(receipt.path("applicationVersion").asInt()!=1 || !"DISPOSITIONS_APPLIED".equals(receipt.path("status").asText())
            || !payloadHash.equals(receipt.path("payloadSha256").asText())
            || !receipt.path("localDeletionAllowed").isBoolean() || receipt.path("localDeletionAllowed").asBoolean()
            || !receipt.path("mayResumeScanning").isBoolean() || receipt.path("mayResumeScanning").asBoolean()
            || !receipt.path("stockReleased").isBoolean() || receipt.path("stockReleased").asBoolean()
            || !SubmissionJson.sha(body.path("dispositions")).equals(receipt.path("dispositionsSha256").asText())
            || !receipt.path("dispositionsSha256").asText().equals(row.get("dispositions_sha256"))
            || !java.time.Instant.parse(receipt.path("appliedAt").asText()).equals(java.time.Instant.parse(row.get("applied_at_utc").toString()))) throw CorrectionCommand.corrupt();
        for(String field:java.util.List.of("submissionId","assignmentId","freezeDeviceId","continuationId","workRound"))
            if(!receipt.path(field).equals(body.path(field))) throw CorrectionCommand.corrupt();
        if(!receipt.path("warehouseId").equals(origin.path("warehouseId")) || !receipt.path("scopeId").equals(origin.path("scopeId"))
            || !receipt.path("orderId").equals(origin.path("orderId"))
            || !receipt.path("issuedRecoveryEpoch").equals(origin.path("recoveryEpoch"))) throw CorrectionCommand.corrupt();
        evidence(receipt,origin);return receipt;
    }
    private void evidence(JsonNode receipt,JsonNode origin) {
        var expected=SubmissionJson.object().putArray("ids");
        jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM physical_disposition_evidence WHERE applied_submission_id=UUID_TO_BIN(?) ORDER BY id",
            String.class,origin.path("submissionId").asText()).forEach(expected::add);
        if(!expected.equals(receipt.path("appliedEvidenceIds")) || java.util.stream.StreamSupport.stream(expected.spliterator(),false)
            .noneMatch(id->id.equals(origin.path("dispositionId")))) throw CorrectionCommand.corrupt();
    }
}
