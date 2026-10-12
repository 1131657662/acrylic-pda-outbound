package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** FORMAL evidence stays tied to the original stopped package, approval and applied submission. */
@Component
final class FormalQuarantineOrigin {
    private final JdbcTemplate jdbc;
    private final FormalQuarantineProof proofs;
    FormalQuarantineOrigin(JdbcTemplate jdbc,FormalQuarantineProof proofs) { this.jdbc=jdbc; this.proofs=proofs; }
    ObjectNode read(UUID bale,UUID caseId) {
        String filter=caseId==null ? "d.id IN (SELECT disposition_evidence_id FROM bale_commitment WHERE bale_id=?)"
            : "d.id IN (SELECT formal_disposition_id FROM quarantine_origin WHERE case_id=? AND bale_id=?)";
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('sourceKind','FORMAL','dispositionId',BIN_TO_UUID(d.id),'baleId',BIN_TO_UUID(d.bale_id),
              'warehouseId',BIN_TO_UUID(o.warehouse_id),'orderId',BIN_TO_UUID(d.order_id),'scopeId',BIN_TO_UUID(s.scope_id),
              'submissionId',BIN_TO_UUID(s.id),'workRound',CAST(s.work_round AS CHAR),'cycle',CAST(d.expected_cycle AS CHAR),
              'action',d.action,'closeMode',true,'sourceClosed',s.status IN ('SUCCEEDED','ABORTED'),
              'closeReason',d.physical_evidence,'recoveryPackageId',BIN_TO_UUID(p.id),'requestId',BIN_TO_UUID(r.id),
              'sourceAssignmentId',BIN_TO_UUID(r.assignment_id),'appliedAssignmentId',BIN_TO_UUID(s.assignment_id),
              'continuationId',BIN_TO_UUID(c.id),'captureId',BIN_TO_UUID(d.origin_scan_id),
              'recordSha256',LOWER(HEX(d.record_sha256)),'recoveryEpoch',CAST(d.recovery_epoch AS CHAR),
              'approvalBatchId',BIN_TO_UUID(d.approval_batch_id),'approvedBy',BIN_TO_UUID(d.approved_by),
              'approvedAt',DATE_FORMAT(d.approved_at,'%Y-%m-%dT%H:%i:%s.%fZ')) data
            FROM physical_disposition_evidence d JOIN recovery_package p ON p.id=d.recovery_package_id
              JOIN order_change_request r ON r.id=p.request_id AND r.order_id=d.order_id
              JOIN outbound_order o ON o.id=d.order_id JOIN submission s ON s.id=d.applied_submission_id
              JOIN order_continuation c ON c.new_assignment_id=s.assignment_id AND c.recovery_package_id=p.id AND c.order_id=d.order_id
            WHERE
            """+filter,caseId==null ? new Object[]{Ids.bytes(bale)} : new Object[]{Ids.bytes(caseId),Ids.bytes(bale)});
        if(rows.isEmpty()) return null;
        if(rows.size()!=1) throw CorrectionCommand.corrupt();
        var origin=(ObjectNode)SubmissionJson.parse(rows.getFirst().get("data").toString());
        if(!bale.toString().equals(origin.path("baleId").asText())) throw CorrectionCommand.corrupt();
        var proof=proofs.read(origin); origin.set("originalReceipt",proof); origin.put("originalReceiptSha256",SubmissionJson.sha(proof));
        if(caseId!=null && !origin.path("originalReceiptSha256").asText().equals(jdbc.queryForObject(
            "SELECT formal_evidence_sha256 FROM quarantine_origin WHERE case_id=?",String.class,Ids.bytes(caseId)))) throw CorrectionCommand.corrupt();
        return origin;
    }
}
