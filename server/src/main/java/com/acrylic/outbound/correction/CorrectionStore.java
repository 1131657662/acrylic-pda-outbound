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

    /**
     * SUBMITTED -> APPROVED under the row lock the kernel already holds. The status predicate makes a second approval
     * of the same document a no-op at the database level as well, so the kernel's expected-context check and this
     * guard agree. Returns the number of rows changed (must be 1).
     */
    public int approveDocument(UUID documentId,UUID approvedBy,Instant approvedAt) {
        return jdbc.update("""
            UPDATE correction_document SET status='APPROVED',approved_by=?,approved_at=?,row_version=row_version+1
            WHERE id=? AND status='SUBMITTED'
            """,Ids.bytes(approvedBy),Timestamp.from(approvedAt),Ids.bytes(documentId));
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
            SELECT JSON_OBJECT('documentId',BIN_TO_UUID(id),'orderId',BIN_TO_UUID(order_id),
              'warehouseId',BIN_TO_UUID(warehouse_id),'type',type,'status',status,
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

    // ---------------------------------------------------------------- steps 4-5: effect, ledger, result version

    /** The frozen lines of one document, ascending; their before/target values drive every delta. */
    public List<Map<String,Object>> lines(UUID documentId) {
        return jdbc.queryForList("""
            SELECT BIN_TO_UUID(id) line_id,BIN_TO_UUID(dispatch_id) dispatch_id,line_no,
              before_count,before_weight_g,target_count,target_weight_g,business_occurred_at
            FROM correction_line WHERE document_id=? ORDER BY line_no
            """,Ids.bytes(documentId));
    }

    /** §3 lock order: the current version row under the order lock, before any dispatch is touched. */
    public Map<String,Object> lockCurrentVersion(UUID orderId) {
        var rows=jdbc.queryForList("""
            SELECT CAST(result_version AS CHAR) result_version,CAST(actual_count AS CHAR) actual_count,
              CAST(weight_g AS CHAR) weight_g,inventory_deducted,header_snapshot,
              IFNULL(BIN_TO_UUID(original_submission_id),IFNULL(BIN_TO_UUID(submission_id),'null')) original_submission_id
            FROM order_result_version WHERE order_id=? ORDER BY result_version DESC LIMIT 1 FOR UPDATE
            """,Ids.bytes(orderId));
        if (rows.size()!=1) throw CorrectionCommand.missing();
        return rows.getFirst();
    }

    public int resultVersionExists(UUID orderId,long version) {
        Integer found=jdbc.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE order_id=? AND result_version=?",
            Integer.class,Ids.bytes(orderId),version);
        return found==null?0:found;
    }

    /** A correction-driven version carries no submission of its own: original_submission_id keeps v1's, submission_id stays NULL. */
    public void insertResultVersion(UUID orderId,long version,UUID originalSubmission,UUID sourceCorrection,
        long count,long weight,boolean deducted,String headerSnapshot,Instant now) {
        jdbc.update("""
            INSERT INTO order_result_version(order_id,result_version,submission_id,actual_count,weight_g,
              inventory_deducted,header_snapshot,created_at,original_submission_id,source_correction_id)
            VALUES(?,?,NULL,?,?,?,CAST(? AS JSON),?,?,?)
            """,Ids.bytes(orderId),version,count,weight,deducted?1:0,headerSnapshot,Timestamp.from(now),
            originalSubmission==null?null:Ids.bytes(originalSubmission),
            sourceCorrection==null?null:Ids.bytes(sourceCorrection));
    }

    /** All original dispatches of the order in ascending id order - the lock order §3 requires. */
    public List<Map<String,Object>> dispatchesForUpdate(UUID orderId) {
        return jdbc.queryForList("""
            SELECT BIN_TO_UUID(d.id) dispatch_id,BIN_TO_UUID(d.bale_id) bale_id,d.delivery_code,
              CAST(d.order_scan_seq AS CHAR) scan_seq,CAST(d.weight_g AS CHAR) weight_g,
              BIN_TO_UUID(d.capture_id) capture_id
            FROM dispatch_record d WHERE d.order_id=? ORDER BY d.id FOR UPDATE
            """,Ids.bytes(orderId));
    }

    public void insertEffectiveScan(UUID orderId,long version,UUID dispatchId,UUID captureId,long sequence,
        boolean effective,long weightG,UUID lineId) {
        jdbc.update("""
            INSERT INTO order_effective_scan(order_id,result_version,dispatch_id,original_scan_ref,original_sequence,
              is_effective,effective_weight_g,latest_correction_line_id) VALUES(?,?,?,?,?,?,?,?)
            """,Ids.bytes(orderId),version,Ids.bytes(dispatchId),captureId==null?null:Ids.bytes(captureId),sequence,
            effective?1:0,weightG,lineId==null?null:Ids.bytes(lineId));
    }

    public void insertEffectiveLine(UUID orderId,long version,String deliveryCode,long count,long weight) {
        jdbc.update("INSERT INTO order_effective_line(order_id,result_version,delivery_code,actual_count,weight_g)"
            + " VALUES(?,?,?,?,?)",Ids.bytes(orderId),version,deliveryCode,count,weight);
    }

    public void insertEffect(UUID id,UUID lineId,UUID orderId,long version,UUID dispatchId,String action,
        long countDelta,long weightDelta,Instant occurredAt,Instant now) {
        jdbc.update("""
            INSERT INTO correction_effect(id,line_id,order_id,result_version,dispatch_id,action,count_delta,
              weight_delta_g,cycle_before,cycle_after,business_occurred_at,effective_at)
            VALUES(?,?,?,?,?,?,?,?,NULL,NULL,?,?)
            """,Ids.bytes(id),Ids.bytes(lineId),Ids.bytes(orderId),version,
            dispatchId==null?null:Ids.bytes(dispatchId),action,countDelta,weightDelta,
            Timestamp.from(occurredAt),Timestamp.from(now));
    }

    /** business_key makes the append idempotent; a full reversal may carry reverses_entry_id, a weight diff never does. */
    public void insertCorrectionLedger(UUID id,UUID dispatchId,UUID baleId,UUID lineId,String kind,int quantityDelta,
        long weightDelta,UUID reversesEntryId,String businessKey,Instant now) {
        jdbc.update("""
            INSERT INTO correction_ledger(id,dispatch_id,bale_id,correction_line_id,entry_kind,quantity_delta,
              weight_delta_g,reverses_entry_id,business_key,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)
            """,Ids.bytes(id),Ids.bytes(dispatchId),Ids.bytes(baleId),lineId==null?null:Ids.bytes(lineId),kind,
            quantityDelta,weightDelta,reversesEntryId==null?null:Ids.bytes(reversesEntryId),businessKey,Timestamp.from(now));
    }

    /** The pointer must name a real version of the same order; both published pointers move together. */
    public void moveCurrentPointer(UUID orderId,long version,Instant now) {
        jdbc.update("UPDATE outbound_order SET current_result_version=? WHERE id=?",version,Ids.bytes(orderId));
        jdbc.update("""
            INSERT INTO order_current_result(order_id,result_version,updated_at) VALUES(?,?,?)
            ON DUPLICATE KEY UPDATE result_version=VALUES(result_version),updated_at=VALUES(updated_at)
            """,Ids.bytes(orderId),version,Timestamp.from(now));
    }

    public int effectDocument(UUID documentId,long version) {
        return jdbc.update("UPDATE correction_document SET status='EFFECTIVE',effective_result_version=?,"
            + " row_version=row_version+1 WHERE id=? AND status='APPROVED'",version,Ids.bytes(documentId));
    }
}
