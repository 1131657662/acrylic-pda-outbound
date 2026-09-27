package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * All persistence for the handover chain. The receipt table is immutable, so the original-key reservation uses the
 * shared {@code api_idempotency_request} row (same pattern as the R06 command chain) and the final receipt is
 * inserted once, after the effects succeeded.
 */
@Repository
public class HandoverStore {
    private final JdbcTemplate jdbc;
    public HandoverStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    /** Source facts for REQUEST: scope, canonical order, mapping version and the original work round. */
    public JsonNode scopeFacts(UUID scopeId) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(c.id),'warehouseId',BIN_TO_UUID(c.warehouse_id),
              'canonicalOrderId',BIN_TO_UUID(IFNULL(c.canonical_order_id,c.source_order_id)),'mappingVersion',CAST(c.mapping_version AS CHAR),
              'fromWorkRound',CAST(c.work_round AS CHAR),'currentSubmissionId',BIN_TO_UUID(c.current_submission_id),
              'submissionStatus',s.status,
              'sourceKind',IF(c.source_order_id IS NULL,'TEMP','FORMAL'))
            FROM submission_scope c LEFT JOIN submission s ON s.id=c.current_submission_id
            -- A FORMAL scope must leave canonical_order_id NULL (ck_submission_scope_source), so its canonical order IS
            -- the source order; a TEMP scope takes the canonical order written by the controlled link.
            WHERE c.id=? AND (c.canonical_order_id IS NOT NULL OR c.source_order_id IS NOT NULL)
              AND (c.source_order_id IS NULL OR c.canonical_order_id IS NULL OR c.source_order_id=c.canonical_order_id)
            """,String.class,Ids.bytes(scopeId));
        if (rows.size()!=1) throw HandoverCommand.missing();
        return SubmissionJson.parse(rows.getFirst());
    }

    /** Case facts as the expected-context anchor and the four-dimension state. */
    public JsonNode caseFacts(UUID caseId) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('caseId',BIN_TO_UUID(id),'caseVersion',CAST(row_version AS CHAR),'status',status,
              'warehouseId',BIN_TO_UUID(warehouse_id),'scopeId',BIN_TO_UUID(scope_id),'submissionId',BIN_TO_UUID(submission_id),
              'canonicalOrderId',BIN_TO_UUID(canonical_order_id),'sourceKind',source_kind,
              'fromWorkRound',CAST(from_work_round AS CHAR),'toWorkRound',CAST(to_work_round AS CHAR),
              'stopState',stop_state,'integrityState',integrity_state,'originalResultState',original_result_state,
              'physicalState',physical_state,'inputHighWatermark',IFNULL(CAST(input_high_watermark AS CHAR),'null'),
              'targetDeviceId',IFNULL(BIN_TO_UUID(target_device_id),'null'),'targetUserId',IFNULL(BIN_TO_UUID(target_user_id),'null'),
              'preparedTicketSha256',IFNULL(LOWER(HEX(prepared_ticket_sha256)),'null'),'activationSha256',IFNULL(LOWER(HEX(activation_sha256)),'null'),
              'approvedBy',IFNULL(BIN_TO_UUID(approved_by),'null'),'requestedBy',BIN_TO_UUID(requested_by))
            FROM handover_case WHERE id=?
            """,String.class,Ids.bytes(caseId));
        if (rows.size()!=1) throw HandoverCommand.missing();
        return SubmissionJson.parse(rows.getFirst());
    }

    public Map<String,Object> receipt(String originalKey) {
        var rows=jdbc.queryForList("SELECT * FROM handover_command_receipt WHERE original_key=?",Ids.bytes(UUID.fromString(originalKey)));
        return rows.isEmpty()?null:rows.getFirst();
    }

    /** Reserve the original key before doing any work; the unique row is what makes retries replay. */
    public void reserve(UUID requestId,UUID actor,String key,String commandSha256,UUID receiptId,Instant recordedAt) {
        jdbc.update("""
            INSERT INTO api_idempotency_request(id,principal_id,route_scope,idempotency_key,request_sha256,resource_id,created_at)
            VALUES(?,?,?,?,?,?,?)
            """,Ids.bytes(requestId),Ids.bytes(actor),HandoverCommand.ROUTE_SCOPE,key,commandSha256,Ids.bytes(receiptId),Timestamp.from(recordedAt));
    }

    public void receipt(HandoverCommand.Write write,String expectedJson,String[] response) {
        jdbc.update("""
            INSERT INTO handover_command_receipt(id,case_id,original_key,command_type,command_sha256,canonical_command_bytes,
              response_bytes,response_sha256,actor_id,expected_json,status,event_id,recorded_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,'RECORDED',?,?)
            """,Ids.bytes(write.receiptId()),Ids.bytes(write.caseId()),Ids.bytes(UUID.fromString(write.command().key())),
            write.command().kind(),unhex(write.command().digest()),write.command().bytes(),
            response[0].getBytes(java.nio.charset.StandardCharsets.UTF_8),unhex(response[1]),
            Ids.bytes(UUID.fromString(write.actor().path("principalId").asText())),expectedJson,Ids.bytes(write.eventId()),
            Timestamp.from(write.recordedAt()));
    }

    /** Case creation for REQUEST; the unique (scope_id, open_flag) row is the single-open-case guarantee. */
    public void insertCase(UUID id,UUID warehouse,UUID scope,UUID submission,UUID canonicalOrder,String sourceKind,
        int fromRound,int toRound,String status,String stopState,String integrityState,String originalResultState,
        String physicalState,Long highWatermark,UUID requestedBy,Instant now) {
        jdbc.update("""
            INSERT INTO handover_case(id,warehouse_id,scope_id,submission_id,canonical_order_id,source_kind,from_work_round,
              to_work_round,status,stop_state,integrity_state,original_result_state,physical_state,open_flag,input_high_watermark,
              requested_by,requested_at,row_version,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?,?,1,?,?)
            """,Ids.bytes(id),Ids.bytes(warehouse),Ids.bytes(scope),Ids.bytes(submission),Ids.bytes(canonicalOrder),sourceKind,
            fromRound,toRound,status,stopState,integrityState,originalResultState,physicalState,highWatermark,Ids.bytes(requestedBy),
            Timestamp.from(now),Timestamp.from(now),Timestamp.from(now));
    }

    /** Optimistic, evidenced transition: it fails loudly when the page the user saw is already gone. */
    public void advanceCase(UUID id,long expectedVersion,String status,UUID approvedBy,Long highWatermark,String stopState,
        String integrityState,String originalResultState,String physicalState,UUID targetDevice,UUID targetUser,
        byte[] preparedTicket,byte[] activationReceipt,UUID actor,Instant now) {
        String activationHex=activationReceipt==null?null:HandoverCommand.sha(activationReceipt);
        int changed=jdbc.update("""
            UPDATE handover_case SET status=?,
              stop_state=COALESCE(?,stop_state),integrity_state=COALESCE(?,integrity_state),
              original_result_state=COALESCE(?,original_result_state),physical_state=COALESCE(?,physical_state),
              input_high_watermark=COALESCE(?,input_high_watermark),
              approved_by=COALESCE(?,approved_by),
              approved_at=COALESCE(?,approved_at),
              target_device_id=COALESCE(?,target_device_id),target_user_id=COALESCE(?,target_user_id),
              prepared_ticket_sha256=COALESCE(?,prepared_ticket_sha256),prepared_at=COALESCE(?,prepared_at),
              activation_receipt=COALESCE(?,activation_receipt),
              activation_sha256=COALESCE(?,activation_sha256),
              activated_at=COALESCE(?,activated_at),
              row_version=row_version+1,updated_at=?
            WHERE id=? AND row_version=?
            """,status,stopState,integrityState,originalResultState,physicalState,highWatermark,
            approvedBy==null?null:Ids.bytes(approvedBy),approvedBy==null?null:Timestamp.from(now),
            targetDevice==null?null:Ids.bytes(targetDevice),targetUser==null?null:Ids.bytes(targetUser),
            preparedTicket,preparedTicket==null?null:Timestamp.from(now),
            activationReceipt,activationHex==null?null:unhex(activationHex),
            activationReceipt==null?null:Timestamp.from(now),Timestamp.from(now),
            Ids.bytes(id),expectedVersion);
        if (changed!=1) throw HandoverCommand.stale();
    }

    public void insertEvidence(UUID id,UUID caseId,String kind,UUID device,String payloadSha256,Long highWatermark,
        byte[] receiptBytes,Instant now) {
        jdbc.update("""
            INSERT INTO handover_evidence(id,case_id,kind,device_id,payload_sha256,high_watermark,receipt_bytes,recorded_at)
            VALUES(?,?,?,?,?,?,?,?)
            """,Ids.bytes(id),Ids.bytes(caseId),kind,Ids.bytes(device),unhex(payloadSha256),highWatermark,receiptBytes,Timestamp.from(now));
    }

    public List<Map<String,Object>> checklist(UUID caseId) {
        return jdbc.queryForList("SELECT BIN_TO_UUID(bale_id) bale_id,delivery_code,state,dispatch_cycle,item_no "
            + "FROM handover_checklist WHERE case_id=? ORDER BY item_no",Ids.bytes(caseId));
    }

    /** How many device-produced stop proofs are already recorded for this case. */
    public int evidenceCount(UUID caseId,String kind) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM handover_evidence WHERE case_id=? AND kind=?",
            Integer.class,Ids.bytes(caseId),kind);
    }

    public void insertChecklist(UUID id,UUID caseId,int itemNo,UUID bale,String deliveryCode,String state,long cycle,
        UUID verifiedBy,Instant now) {
        jdbc.update("""
            INSERT INTO handover_checklist(id,case_id,item_no,bale_id,delivery_code,state,dispatch_cycle,verified_by,verified_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            """,Ids.bytes(id),Ids.bytes(caseId),itemNo,Ids.bytes(bale),deliveryCode,state,cycle,Ids.bytes(verifiedBy),Timestamp.from(now));
    }

    public List<Map<String,Object>> audits(UUID receiptId) {
        return jdbc.queryForList("SELECT actor_id,details,event_type FROM operation_event WHERE entity_type='HANDOVER_COMMAND' AND entity_id=?",
            Ids.bytes(receiptId));
    }

    private static byte[] unhex(String hex) {
        return java.util.HexFormat.of().parseHex(hex);
    }
}
