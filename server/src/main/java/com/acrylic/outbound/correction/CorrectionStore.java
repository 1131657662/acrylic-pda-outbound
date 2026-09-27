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
            write.documentId()==null?null:Ids.bytes(write.documentId()),
            // A reconciliation command has no order; V47 relaxed this column to NULL for exactly that case.
            write.orderId()==null?null:Ids.bytes(write.orderId()),
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
     * repository (design §2 assumed one; step 5 added it), so the current effective values are read from the scan
     * snapshot of the order's current version when that version has one, and only otherwise from the original dispatch
     * plus its ledger row. Without this preference a second correction on an already corrected dispatch would compute
     * its "before" side from v1 and silently offer a wrong delta.
     */
    public JsonNode dispatchFacts(UUID orderId,UUID dispatchId) {
        var rows=jdbc.queryForList("""
            SELECT s.is_effective,CAST(s.effective_weight_g AS CHAR) effective_weight_g,
              BIN_TO_UUID(d.id) dispatch_id,BIN_TO_UUID(d.bale_id) bale_id,d.delivery_code,
              CAST(d.dispatch_cycle AS CHAR) dispatch_cycle,DATE_FORMAT(d.confirmed_at,'%Y-%m-%dT%H:%i:%s.%fZ') confirmed_at
            FROM dispatch_record d JOIN outbound_order o ON o.id=d.order_id
            LEFT JOIN order_effective_scan s ON s.order_id=d.order_id
              AND s.result_version=o.current_result_version AND s.dispatch_id=d.id
            WHERE d.id=? AND d.order_id=?
            """,Ids.bytes(dispatchId),Ids.bytes(orderId));
        if (rows.size()!=1) throw CorrectionCommand.missing();
        var row=rows.getFirst();
        boolean fromScan=row.get("is_effective")!=null;
        long count=fromScan?(asBoolean(row.get("is_effective"))?1L:0L):ledgerCount(dispatchId);
        long weight=fromScan?Long.parseLong(String.valueOf(row.get("effective_weight_g"))):ledgerWeight(dispatchId);
        return SubmissionJson.object()
            .put("dispatchId",String.valueOf(row.get("dispatch_id")))
            .put("baleId",String.valueOf(row.get("bale_id")))
            .put("deliveryCode",String.valueOf(row.get("delivery_code")))
            .put("dispatchCycle",String.valueOf(row.get("dispatch_cycle")))
            .put("confirmedAt",String.valueOf(row.get("confirmed_at")))
            .put("count",Long.toString(count)).put("weightG",Long.toString(weight));
    }

    private long ledgerCount(UUID dispatchId) {
        Long value=jdbc.queryForObject("SELECT IFNULL(-SUM(quantity_delta),1) FROM inventory_ledger WHERE dispatch_id=?",
            Long.class,Ids.bytes(dispatchId));
        return value==null?1:value;
    }

    private long ledgerWeight(UUID dispatchId) {
        Long value=jdbc.queryForObject("SELECT IFNULL(-SUM(weight_delta_g),"
            + "(SELECT weight_g FROM dispatch_record WHERE id=?)) FROM inventory_ledger WHERE dispatch_id=?",
            Long.class,Ids.bytes(dispatchId),Ids.bytes(dispatchId));
        return value==null?0:value;
    }

    private static boolean asBoolean(Object value) {
        if(value instanceof Boolean flag) return flag;
        if(value instanceof Number number) return number.intValue()!=0;
        return Boolean.parseBoolean(String.valueOf(value));
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
              'approvedBy',IFNULL(BIN_TO_UUID(approved_by),'null'),'requestedBy',BIN_TO_UUID(requested_by),
              'withdrawalRequestedBy',IFNULL(BIN_TO_UUID(withdrawal_requested_by),'null'),
              'withdrawalConfirmedBy',IFNULL(BIN_TO_UUID(withdrawal_confirmed_by),'null'))
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
              before_count,before_weight_g,target_count,target_weight_g,business_occurred_at,evidence_sha256
            FROM correction_line WHERE document_id=? ORDER BY line_no
            """,Ids.bytes(documentId));
    }

    /** §3 lock order: the current version row under the order lock, before any dispatch is touched. */
    public Map<String,Object> lockCurrentVersion(UUID orderId) {
        var rows=jdbc.queryForList("""
            SELECT CAST(result_version AS CHAR) result_version,CAST(actual_count AS CHAR) actual_count,
              CAST(weight_g AS CHAR) weight_g,inventory_deducted,header_snapshot,
              -- original_submission_id is only filled by V46's backfill or by this service's own inserts; for versions
              -- written later by the R05/R06 writers it stays NULL, so fall back to whichever channel the version has.
              COALESCE(BIN_TO_UUID(original_submission_id),BIN_TO_UUID(submission_id),BIN_TO_UUID(pc_submission_id),'null')
                original_submission_id
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

    // ---------------------------------------------------------------- A1 withdraw: entry-by-entry rollback

    /** The correction_ledger entries this document wrote: the rows a withdrawal must reverse one by one (§7.2). */
    public List<Map<String,Object>> ledgerEntries(UUID documentId) {
        return jdbc.queryForList("""
            SELECT BIN_TO_UUID(l.id) entry_id,BIN_TO_UUID(l.dispatch_id) dispatch_id,BIN_TO_UUID(l.bale_id) bale_id,
              BIN_TO_UUID(l.correction_line_id) line_id,l.entry_kind,l.quantity_delta,l.weight_delta_g,l.business_key
            FROM correction_ledger l JOIN correction_line c ON c.id=l.correction_line_id
            WHERE c.document_id=? ORDER BY l.created_at,l.id
            """,Ids.bytes(documentId));
    }

    /** The version's per-dispatch effective values: what a withdrawal rolls back and what it must conflict-check. */
    public List<Map<String,Object>> effectiveScan(UUID orderId,long version) {
        return jdbc.queryForList("""
            SELECT BIN_TO_UUID(dispatch_id) dispatch_id,is_effective,CAST(effective_weight_g AS CHAR) effective_weight_g,
              IFNULL(BIN_TO_UUID(latest_correction_line_id),'null') latest_correction_line_id
            FROM order_effective_scan WHERE order_id=? AND result_version=? ORDER BY dispatch_id
            """,Ids.bytes(orderId),version);
    }

    /**
     * P-R07-08 initiation by the original approver: the document enters WITHDRAW_PENDING and <b>nothing is rolled
     * back yet</b> - the requester's confirmation is what applies the reversal.
     */
    public int requestWithdrawal(UUID documentId,UUID approver,Instant at) {
        return jdbc.update("UPDATE correction_document SET status='WITHDRAW_PENDING',withdrawal_requested_by=?,"
            + " withdrawal_requested_at=?,row_version=row_version+1 WHERE id=? AND status='EFFECTIVE'",
            Ids.bytes(approver),Timestamp.from(at),Ids.bytes(documentId));
    }

    /** P-R07-08 confirmation by the requester: the moment the rollback is applied and the document becomes WITHDRAWN. */
    public int confirmWithdrawal(UUID documentId,UUID requester,Instant at) {
        return jdbc.update("UPDATE correction_document SET status='WITHDRAWN',withdrawal_confirmed_by=?,"
            + " withdrawal_confirmed_at=?,row_version=row_version+1 WHERE id=? AND status='WITHDRAW_PENDING'",
            Ids.bytes(requester),Timestamp.from(at),Ids.bytes(documentId));
    }

    // ---------------------------------------------------------------- A2 cycle reopening (step 6)

    /** §3 locks server_state first; the reopening records the epoch it was issued under. */
    public long recoveryEpoch() {
        Long epoch=jdbc.queryForObject("SELECT recovery_epoch FROM server_state WHERE id=1",Long.class);
        return epoch==null?0:epoch;
    }

    public boolean cycleReopeningExists(UUID originalDispatchId) {
        Integer found=jdbc.queryForObject("SELECT COUNT(*) FROM cycle_reopening WHERE original_dispatch_id=?",
            Integer.class,Ids.bytes(originalDispatchId));
        return found!=null && found>0;
    }

    /** One reopening per original dispatch (UNIQUE); cycle_after = cycle_before + 1 is enforced by CHECK as well. */
    public void insertCycleReopening(UUID id,UUID originalDispatchId,UUID lineId,int cycleBefore,int cycleAfter,
        String evidenceSha256,long stateVersion,long issuedRecoveryEpoch,Instant now) {
        jdbc.update("""
            INSERT INTO cycle_reopening(id,original_dispatch_id,correction_line_id,cycle_before,cycle_after,
              evidence_sha256,state_version,issued_recovery_epoch,created_at) VALUES(?,?,?,?,?,?,?,?,?)
            """,Ids.bytes(id),Ids.bytes(originalDispatchId),Ids.bytes(lineId),cycleBefore,cycleAfter,
            evidenceSha256,stateVersion,issuedRecoveryEpoch,Timestamp.from(now));
    }

    // ---------------------------------------------------------------- A3 step 7: discrepancy cases

    public void insertReconciliation(UUID id,UUID warehouseId,String kind,UUID baleId,UUID dispatchId,Integer cycle,
        Long expectedResultVersion,Long expectedLedgerVersion,Long expectedStockStateVersion,Instant now) {
        jdbc.update("""
            INSERT INTO inventory_reconciliation(id,warehouse_id,kind,bale_id,dispatch_id,cycle,status,
              expected_result_version,expected_ledger_version,expected_stock_state_version,row_version,
              created_at,updated_at) VALUES(?,?,?,?,?,?,'OPEN',?,?,?,1,?,?)
            """,Ids.bytes(id),Ids.bytes(warehouseId),kind,baleId==null?null:Ids.bytes(baleId),
            dispatchId==null?null:Ids.bytes(dispatchId),cycle,expectedResultVersion,expectedLedgerVersion,
            expectedStockStateVersion,Timestamp.from(now),Timestamp.from(now));
    }

    public JsonNode reconciliationFacts(UUID caseId) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('caseId',BIN_TO_UUID(id),'warehouseId',BIN_TO_UUID(warehouse_id),'kind',kind,
              'status',status,'rowVersion',CAST(row_version AS CHAR),
              'baleId',IFNULL(BIN_TO_UUID(bale_id),'null'),'dispatchId',IFNULL(BIN_TO_UUID(dispatch_id),'null'),
              'cycle',IFNULL(CAST(cycle AS CHAR),'null'),'conclusion',IFNULL(conclusion,'null'))
            FROM inventory_reconciliation WHERE id=?
            """,String.class,Ids.bytes(caseId));
        if (rows.size()!=1) throw CorrectionCommand.missing();
        return SubmissionJson.parse(rows.getFirst());
    }

    /**
     * The authoritative shipment-confirmation instant: the <b>v1</b> result version's {@code created_at}, which
     * {@code SubmissionAccounting.commit} writes inside the confirmation transaction. The current version's own
     * {@code created_at} is a correction time once v&gt;1 exists, so it must not be used for the 3-day backfill window
     * (P-R07-06). Falls back to the current version only if v1 is absent.
     */
    public Instant shipmentCreatedAt(UUID orderId) {
        var rows=jdbc.queryForList("SELECT created_at FROM order_result_version WHERE order_id=?"
            + " ORDER BY (result_version=1) DESC,result_version ASC LIMIT 1",Ids.bytes(orderId));
        if (rows.size()!=1) throw CorrectionCommand.missing();
        Object value=rows.getFirst().get("created_at");
        if (value instanceof java.sql.Timestamp stamp) return stamp.toInstant();
        if (value instanceof java.time.LocalDateTime local) return local.toInstant(java.time.ZoneOffset.UTC);
        throw new com.acrylic.outbound.common.BusinessException(409,"CORRECTION_CONTEXT_CHANGED","出库确认时间不可读");
    }

    /** The current version's header snapshot (the frozen success receipt), used to validate a header patch early. */
    public String currentHeaderSnapshot(UUID orderId) {
        var rows=jdbc.queryForList("SELECT header_snapshot FROM order_result_version WHERE order_id=?"
            + " ORDER BY result_version DESC LIMIT 1",Ids.bytes(orderId));
        if(rows.isEmpty()) throw CorrectionCommand.missing();
        return String.valueOf(rows.getFirst().get("header_snapshot"));
    }

    /**
     * A document's own CREATE bytes, kept in its immutable receipt. For a header correction this is the only place the
     * patch is stored - the request that carries it is the CREATE, while the effect runs in a later APPROVE command.
     */
    public byte[] createCommandBytes(UUID documentId) {
        var rows=jdbc.queryForList("SELECT canonical_command_bytes FROM correction_command_receipt"
            + " WHERE document_id=? AND command_type='CREATE' ORDER BY recorded_at DESC LIMIT 1",Ids.bytes(documentId));
        if(rows.isEmpty())
            throw new com.acrylic.outbound.common.BusinessException(409,"CORRECTION_CONTEXT_CHANGED","更正单缺少原始命令证据");
        Object value=rows.getFirst().get("canonical_command_bytes");
        if(value instanceof byte[] bytes) return bytes;
        return String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Status transition under the row lock: the predicate makes a racing advance fail instead of double-applying. */
    public int advanceReconciliation(UUID caseId,String fromStatus,String toStatus,String conclusion,Instant now) {
        return jdbc.update("UPDATE inventory_reconciliation SET status=?,conclusion=?,row_version=row_version+1,"
            + " updated_at=? WHERE id=? AND status=?",toStatus,conclusion,Timestamp.from(now),Ids.bytes(caseId),fromStatus);
    }

    // ---------------------------------------------------------------- A4 step 8: state outbox

    /**
     * One notification per published version. {@code order_sync_outbox} is unique on
     * {@code (recovery_epoch, order_id, entity_version)} and that version space is <b>shared with the R05/R06 order
     * events</b> - using the result version collided with a row the confirmation chain had already written, so a
     * correction takes the next free state version of its order instead. The order lock is held by the caller, so the
     * read-then-insert cannot race.
     */
    public long nextOutboxEntityVersion(UUID orderId,long epoch) {
        Long next=jdbc.queryForObject("SELECT IFNULL(MAX(entity_version),0)+1 FROM order_sync_outbox"
            + " WHERE recovery_epoch=? AND order_id=?",Long.class,epoch,Ids.bytes(orderId));
        return next==null?1:next;
    }

    public void insertOutbox(UUID orderId,long epoch,String eventType,long entityVersion,String payload,Instant now) {
        jdbc.update("""
            INSERT INTO order_sync_outbox(id,recovery_epoch,warehouse_id,order_id,entity_version,event_type,payload,
              published_seq,created_at)
            SELECT ?,?,o.warehouse_id,o.id,?,?,CAST(? AS JSON),NULL,?
            FROM outbound_order o WHERE o.id=?
            """,Ids.bytes(Ids.next()),epoch,entityVersion,eventType,payload,Timestamp.from(now),Ids.bytes(orderId));
    }
}
