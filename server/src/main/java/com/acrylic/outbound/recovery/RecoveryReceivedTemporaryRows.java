package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Prerequisites for restoring a received first-round TEMP verification, never a work permit. */
final class RecoveryReceivedTemporaryRows {
    private final JdbcTemplate jdbc;
    RecoveryReceivedTemporaryRows(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    ObjectNode original(RecoverySourceIdentity id, byte[] payload) { return read(id,payload,""); }
    ObjectNode locked(RecoverySourceIdentity id, byte[] payload) { return read(id,payload," FOR UPDATE"); }

    private ObjectNode read(RecoverySourceIdentity id, byte[] bytes, String lock) {
        require("TEMP".equals(id.sourceKind()) && id.workRound()==1 && id.canonicalOrderId()!=null,"RECOVERY_RECEIVED_SOURCE");
        var payload=SubmissionJson.parse(bytes); RecoveryOriginalOccupancyFacts.payload(payload,id);
        var head=head(id,lock); terminal(head,id,payload);
        var result=SubmissionJson.object(); result.set("head",head);
        var rows=new RecoveryTemporaryEvidenceRows(jdbc);
        result.set("evidence",lock.isEmpty()?rows.readOriginal(id):rows.read(id));
        var owners=new RecoveryOriginalOccupancyRows(jdbc);
        result.set("owners",lock.isEmpty()?owners.read(id,payload):owners.readLocked(id,payload));
        for(var owner:result.path("owners")) require(uuid(owner,"ownerScopeId").equals(id.scopeId())
            && uuid(owner,"ownerWarehouseId").equals(id.warehouseId()) && uuid(owner,"ownerOrderId").equals(id.canonicalOrderId()),
            "RECOVERY_RECEIVED_OWNER_SOURCE");
        result.set("history",new RecoveryReceivedTemporaryHistoryRows(jdbc).read(id,lock));
        var sets=SubmissionJson.array(result.path("evidence"),"source_evidence_set");
        require(sets.size()==1,"RECOVERY_RECEIVED_SET"); var set=sets.get(0);
        require(revision(set,"capture_high_watermark")==id.captureHighWatermark()
            && revision(set,"audit_through_seq")==id.auditThroughSeq()
            && revision(set,"order_scan_high_watermark")==id.orderScanHighWatermark(),"RECOVERY_RECEIVED_SET");
        return result;
    }

    private ObjectNode head(RecoverySourceIdentity id, String lock) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(s.scope_id) scopeId,BIN_TO_UUID(c.warehouse_id) warehouseId,
             BIN_TO_UUID(s.freeze_device_id) deviceId,BIN_TO_UUID(s.operator_id) operatorId,
             BIN_TO_UUID(s.temporary_authorization_id) qualificationId,BIN_TO_UUID(c.canonical_order_id) canonicalOrderId,
             BIN_TO_UUID(c.creator_device_id) creatorDeviceId,BIN_TO_UUID(c.client_order_id) clientOrderId,
             CAST(s.work_round AS CHAR) workRound,CAST(s.source_recovery_epoch AS CHAR) sourceRecoveryEpoch,
             s.status,s.receipt_text,p.payload_sha256,p.file_key,p.byte_count,p.capture_high_watermark,
             BIN_TO_UUID(c.current_submission_id) currentSubmissionId,c.work_round scopeRound,
             s.assignment_id,BIN_TO_UUID(s.continuation_id) continuationId,BIN_TO_UUID(s.superseded_by) supersededBy,c.source_order_id,
             o.status orderStatus,o.source orderSource,CAST(o.row_version AS CHAR) orderVersion,
             BIN_TO_UUID(k.current_submission_id) slotSubmission,k.work_round slotRound,k.phase slotPhase,
             CAST(k.slot_version AS CHAR) slotVersion,BIN_TO_UUID(m.id) mappingId,CAST(m.mapping_version AS CHAR) mappingVersion
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
             JOIN submission_payload p ON p.submission_id=s.id
             JOIN outbound_order o ON o.id=c.canonical_order_id AND o.warehouse_id=c.warehouse_id
             JOIN order_submission_slot k ON k.order_id=o.id AND k.scope_id=c.id
             JOIN temporary_source_mapping m ON m.scope_id=c.id AND m.mapping_version=c.mapping_version AND m.canonical_order_id=o.id
            WHERE s.id=?
            """+lock,(rs,n) -> {
                var value=SubmissionJson.object();
                for(int column=1;column<=rs.getMetaData().getColumnCount();column++) value.put(rs.getMetaData().getColumnLabel(column),rs.getString(column));
                return value;
            },Ids.bytes(id.submissionId()));
        require(rows.size()==1,"RECOVERY_RECEIVED_HEAD"); return rows.getFirst();
    }

    private static void terminal(JsonNode head, RecoverySourceIdentity id, JsonNode payload) {
        RecoveryOriginalOccupancyFacts.head(head,id,SubmissionJson.sha(payload));
        var receipt=SubmissionJson.parse(text(head,"receipt_text"));
        require(receipt.path("payloadPresent").isBoolean() && receipt.path("payloadPresent").booleanValue()
            && text(receipt,"payloadSha256").equals(SubmissionJson.sha(payload)) && text(receipt,"status").equals("ABORTED")
            && uuid(receipt,"submissionId").equals(id.submissionId()) && uuid(receipt,"freezeDeviceId").equals(id.deviceId())
            && revision(receipt,"workRound")==1 && text(receipt,"issuedRecoveryEpoch").equals(id.sourceRecoveryEpoch())
            && receipt.path("source").equals(payload.path("source")),"RECOVERY_RECEIVED_TERMINAL");
        for(String field:List.of("mayResumeScanning","stockReleased"))
            require(receipt.path(field).isBoolean() && !receipt.path(field).booleanValue(),"RECOVERY_RECEIVED_TERMINAL");
        if(receipt.has("localDeletionAllowed")) require(receipt.path("localDeletionAllowed").isBoolean()
            && !receipt.path("localDeletionAllowed").booleanValue(),"RECOVERY_RECEIVED_TERMINAL");
        require(uuid(head,"slotSubmission").equals(id.submissionId()) && revision(head,"slotRound")==1
            && text(head,"slotPhase").equals("FROZEN") && text(head,"orderStatus").equals("REVIEW_REQUIRED")
            && text(head,"orderSource").equals("PDA_TEMP") && head.path("assignment_id").isNull()
            && head.path("source_order_id").isNull(),"RECOVERY_RECEIVED_HEAD_CHAIN");
        require(revision(head,"capture_high_watermark")==id.captureHighWatermark(),"RECOVERY_RECEIVED_PAYLOAD");
    }
}
