package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Storage for the correction command kernel, mirroring the R07-1 handover store: the original key is reserved before
 * any work (the unique {@code api_idempotency_request} row is what makes a retry replay), and the immutable receipt
 * lives in its own table added by V45.
 */
@Repository
public class CorrectionStore {
    private final JdbcTemplate jdbc;
    public CorrectionStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    /** Same key + same canonical bytes replays; the kernel compares bytes, never re-executes. */
    public Map<String,Object> receipt(String originalKey) {
        var rows=jdbc.queryForList("SELECT * FROM correction_command_receipt WHERE original_key=?",
            Ids.bytes(UUID.fromString(originalKey)));
        return rows.isEmpty()?null:rows.getFirst();
    }

    public void reserve(UUID requestId,UUID actor,String key,String commandSha256,UUID receiptId,Instant recordedAt) {
        jdbc.update("""
            INSERT INTO api_idempotency_request(id,principal_id,route_scope,idempotency_key,request_sha256,resource_id,created_at)
            VALUES(?,?,?,?,?,?,?)
            """,Ids.bytes(requestId),Ids.bytes(actor),CorrectionCommand.ROUTE_SCOPE,key,commandSha256,
            Ids.bytes(receiptId),Timestamp.from(recordedAt));
    }

    public void receipt(CorrectionCommand.Write write,String expectedJson,String[] response) {
        jdbc.update("""
            INSERT INTO correction_command_receipt(id,document_id,order_id,original_key,command_type,command_sha256,
              canonical_command_bytes,response_bytes,response_sha256,actor_id,expected_json,status,event_id,recorded_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,'RECORDED',?,?)
            """,Ids.bytes(write.receiptId()),
            write.documentId()==null?null:Ids.bytes(write.documentId()),Ids.bytes(write.orderId()),
            Ids.bytes(UUID.fromString(write.command().key())),write.command().kind(),unhex(write.command().digest()),
            write.command().bytes(),response[0].getBytes(java.nio.charset.StandardCharsets.UTF_8),unhex(response[1]),
            Ids.bytes(UUID.fromString(write.actor().path("principalId").asText())),expectedJson,
            Ids.bytes(write.eventId()),Timestamp.from(write.recordedAt()));
    }

    /**
     * Facts a CREATE anchors on: the order's current result version plus its effective totals. The stock/ledger
     * version sources are deliberately NOT read yet - §3 lists them for the effect step, and this repository has no
     * {@code stock_state} table to read them from (verified against the migrations).
     */
    public JsonNode orderFacts(UUID orderId) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('orderId',BIN_TO_UUID(o.id),
              'currentResultVersion',CAST(IFNULL(o.current_result_version,0) AS CHAR),
              'currentResultEffectiveCount',CAST(IFNULL(r.actual_count,0) AS CHAR),
              'currentResultEffectiveWeightG',CAST(IFNULL(r.weight_g,0) AS CHAR),
              'currentResultCreatedAt',IFNULL(DATE_FORMAT(r.created_at,'%Y-%m-%dT%H:%i:%s.%fZ'),'null'),
              'sourceRecoveryEpoch',CAST(IFNULL(s.source_recovery_epoch,1) AS CHAR),
              'orderStatus',o.status,'warehouseId',BIN_TO_UUID(o.warehouse_id))
            FROM outbound_order o
            LEFT JOIN order_result_version r ON r.order_id=o.id AND r.result_version=o.current_result_version
            LEFT JOIN submission s ON s.id=r.submission_id
            WHERE o.id=?
            """,String.class,Ids.bytes(orderId));
        if (rows.size()!=1) throw CorrectionCommand.missing();
        return SubmissionJson.parse(rows.getFirst());
    }

    /**
     * Server-side recompute of one line's "before" values. There is no {@code order_effective_scan} table in this
     * repository (design §2 assumed one; it belongs to the result-version step 5), so the current effective values are
     * read from the facts that exist today: the original dispatch plus its ledger row when it has one. This is exact
     * while the order is still on its first result version, which is the only state a first correction can start from.
     */
    public JsonNode dispatchFacts(UUID orderId,UUID dispatchId) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('dispatchId',BIN_TO_UUID(d.id),'baleId',BIN_TO_UUID(d.bale_id),
              'deliveryCode',d.delivery_code,'dispatchCycle',CAST(d.dispatch_cycle AS CHAR),
              'confirmedAt',DATE_FORMAT(d.confirmed_at,'%Y-%m-%dT%H:%i:%s.%fZ'),
              'count',CAST(IFNULL((SELECT -SUM(l.quantity_delta) FROM inventory_ledger l WHERE l.dispatch_id=d.id),1) AS CHAR),
              'weightG',CAST(IFNULL((SELECT -SUM(l.weight_delta_g) FROM inventory_ledger l WHERE l.dispatch_id=d.id),d.weight_g) AS CHAR))
            FROM dispatch_record d WHERE d.id=? AND d.order_id=?
            """,String.class,Ids.bytes(dispatchId),Ids.bytes(orderId));
        if (rows.size()!=1) throw CorrectionCommand.missing();
        return SubmissionJson.parse(rows.getFirst());
    }

    public void insertDocument(UUID id,UUID warehouse,UUID order,String type,long expectedResultVersion,String reason,
        String documentSha256,String status,long sourceRecoveryEpoch,UUID requestedBy,Instant now) {
        jdbc.update("""
            INSERT INTO correction_document(id,warehouse_id,order_id,type,expected_result_version,reason,document_sha256,
              status,source_recovery_epoch,row_version,requested_by,requested_at)
            VALUES(?,?,?,?,?,?,?,?,?,1,?,?)
            """,Ids.bytes(id),Ids.bytes(warehouse),Ids.bytes(order),type,expectedResultVersion,reason,documentSha256,
            status,sourceRecoveryEpoch,Ids.bytes(requestedBy),Timestamp.from(now));
    }

    public void insertLine(UUID id,UUID document,int lineNo,UUID dispatchId,UUID baleId,String deliveryCode,
        long beforeCount,long beforeWeight,long targetCount,long targetWeight,UUID evidenceId,String evidenceSha256,
        Instant occurredAt) {
        jdbc.update("""
            INSERT INTO correction_line(id,document_id,line_no,dispatch_id,bale_id,delivery_code,before_count,
              before_weight_g,target_count,target_weight_g,evidence_id,evidence_sha256,business_occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,Ids.bytes(id),Ids.bytes(document),lineNo,dispatchId==null?null:Ids.bytes(dispatchId),
            baleId==null?null:Ids.bytes(baleId),deliveryCode,beforeCount,beforeWeight,targetCount,targetWeight,
            evidenceId==null?null:Ids.bytes(evidenceId),evidenceSha256,Timestamp.from(occurredAt));
    }

    /** Facts an APPROVE or WITHDRAW anchors on: the frozen document digest and its expected result version. */
    public JsonNode documentFacts(UUID documentId) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('documentId',BIN_TO_UUID(id),'orderId',BIN_TO_UUID(order_id),'type',type,'status',status,
              'documentSha256',document_sha256,'expectedResultVersion',CAST(expected_result_version AS CHAR),
              'documentVersion',CAST(row_version AS CHAR),
              'approvedBy',IFNULL(BIN_TO_UUID(approved_by),'null'),'requestedBy',BIN_TO_UUID(requested_by))
            FROM correction_document WHERE id=?
            """,String.class,Ids.bytes(documentId));
        if (rows.size()!=1) throw CorrectionCommand.missing();
        return SubmissionJson.parse(rows.getFirst());
    }

    /** Exactly one audit event per receipt closes the command, as in the R06/R07-1 chains. */
    public List<Map<String,Object>> audits(UUID receipt) {
        return jdbc.queryForList("SELECT actor_id,details,event_type FROM operation_event WHERE entity_type='CORRECTION_COMMAND' AND entity_id=?",
            Ids.bytes(receipt));
    }

    private static byte[] unhex(String hex) { return java.util.HexFormat.of().parseHex(hex); }
}
