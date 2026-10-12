package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandReceiptIntegrity;
import com.acrylic.outbound.temporary.command.CommandStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Closed TEMP origins are proven by the original command, not by the current commitment alone. */
@Component
final class QuarantineOrigin {
    private final JdbcTemplate jdbc;
    private final CommandStore commands;
    private final CommandReceiptIntegrity integrity;
    QuarantineOrigin(JdbcTemplate jdbc, CommandStore commands, CommandReceiptIntegrity integrity) {
        this.jdbc=jdbc; this.commands=commands; this.integrity=integrity;
    }

    ObjectNode read(UUID bale, UUID caseId) {
        var rows = jdbc.queryForList(caseId==null ? """
            SELECT JSON_OBJECT('dispositionId',BIN_TO_UUID(d.id),'baleId',BIN_TO_UUID(i.bale_id),
              'warehouseId',BIN_TO_UUID(d.warehouse_id),'scopeId',BIN_TO_UUID(d.scope_id),
              'submissionId',BIN_TO_UUID(d.submission_id),'workRound',CAST(d.work_round AS CHAR),
              'evidenceSetId',BIN_TO_UUID(d.evidence_set_id),'verificationId',BIN_TO_UUID(d.evidence_verification_id),
              'cycle',CAST(i.dispatch_cycle AS CHAR),'action',i.action,'manifestSha256',d.manifest_sha256,
              'manifestVersion',CAST(d.manifest_version AS CHAR),'closeMode',d.close_mode,'closeReason',d.close_reason,
              'orderId',BIN_TO_UUID(s.canonical_order_id),'sourceClosed',s.current_submission_id IS NULL) data
            FROM bale_commitment k JOIN temporary_physical_disposition d ON d.id=k.disposition_id
              JOIN temporary_physical_disposition_item i ON i.disposition_id=d.id AND i.bale_id=k.bale_id
              JOIN submission_scope s ON s.id=d.scope_id
            WHERE k.bale_id=? AND k.state='QUARANTINED'
            """ : """
            SELECT JSON_OBJECT('dispositionId',BIN_TO_UUID(d.id),'baleId',BIN_TO_UUID(i.bale_id),
              'warehouseId',BIN_TO_UUID(d.warehouse_id),'scopeId',BIN_TO_UUID(d.scope_id),
              'submissionId',BIN_TO_UUID(d.submission_id),'workRound',CAST(d.work_round AS CHAR),
              'evidenceSetId',BIN_TO_UUID(d.evidence_set_id),'verificationId',BIN_TO_UUID(d.evidence_verification_id),
              'cycle',CAST(i.dispatch_cycle AS CHAR),'action',i.action,'manifestSha256',d.manifest_sha256,
              'manifestVersion',CAST(d.manifest_version AS CHAR),'closeMode',d.close_mode,'closeReason',d.close_reason,
              'orderId',BIN_TO_UUID(s.canonical_order_id),'sourceClosed',s.current_submission_id IS NULL) data
            FROM quarantine_origin q JOIN temporary_physical_disposition d ON d.id=q.disposition_id
              JOIN temporary_physical_disposition_item i ON i.disposition_id=q.disposition_id AND i.bale_id=q.bale_id
              JOIN submission_scope s ON s.id=d.scope_id
            WHERE q.case_id=? AND q.bale_id=?
            """, caseId==null ? new Object[]{Ids.bytes(bale)} : new Object[]{Ids.bytes(caseId),Ids.bytes(bale)});
        if (rows.size()!=1) throw CorrectionCommand.missing();
        var origin=(ObjectNode)SubmissionJson.parse(rows.getFirst().get("data").toString());
        attachReceipt(origin,caseId); return origin;
    }

    private void attachReceipt(ObjectNode origin, UUID caseId) {
        var rows=jdbc.queryForList("""
            SELECT BIN_TO_UUID(id) id FROM temporary_command_receipt
            WHERE scope_id=UUID_TO_BIN(?) AND submission_id=UUID_TO_BIN(?) AND work_round=?
              AND command_type='CLOSE_DISPOSITION'
            """,origin.path("scopeId").asText(),origin.path("submissionId").asText(),origin.path("workRound").asLong());
        if(rows.size()!=1) throw CorrectionCommand.corrupt();
        UUID receiptId=UUID.fromString(rows.getFirst().get("id").toString());
        var row=commands.byReceipt(receiptId); var receipt=integrity.verify(row); var decision=receipt.path("businessDecision");
        for(String key:java.util.List.of("dispositionId","evidenceSetId","verificationId","manifestSha256","manifestVersion"))
            if(!origin.get(key).equals(decision.get(key))) throw CorrectionCommand.corrupt();
        if(!origin.path("warehouseId").equals(receipt.at("/originalContext/warehouseId"))) throw CorrectionCommand.corrupt();
        requireCaseReceipt(caseId,receiptId);
        var original=SubmissionJson.parse(row.bytes("original_response_bytes"));
        origin.set("originalReceipt",original);
        origin.put("originalReceiptSha256",row.text("original_response_sha256"));
    }

    private void requireCaseReceipt(UUID caseId,UUID receiptId) {
        if(caseId==null) return;
        var recorded=jdbc.queryForList("SELECT BIN_TO_UUID(original_receipt_id) FROM quarantine_origin WHERE case_id=?",String.class,Ids.bytes(caseId));
        if(recorded.size()!=1 || !receiptId.toString().equals(recorded.getFirst())) throw CorrectionCommand.corrupt();
    }
    byte[] original(com.fasterxml.jackson.databind.JsonNode origin) {
        var row=commands.byReceipt(UUID.fromString(origin.at("/originalReceipt/receiptId").asText())); integrity.verify(row);
        if(!origin.path("originalReceiptSha256").asText().equals(row.text("original_response_sha256"))) throw CorrectionCommand.corrupt();
        return row.bytes("original_response_bytes");
    }
}
