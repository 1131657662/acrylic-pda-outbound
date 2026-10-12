package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Four fact domains remain separate: upstream projection, source proof, owners, and real dispatch/accounting. */
@Component
final class QuarantineFacts {
    private final JdbcTemplate jdbc;
    QuarantineFacts(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    void lock(UUID warehouse,UUID bale,JsonNode origin) {
        jdbc.queryForList("SELECT id FROM warehouse WHERE id=? FOR UPDATE",Ids.bytes(warehouse));
        if(origin.hasNonNull("orderId")) jdbc.queryForList("SELECT id FROM outbound_order WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("orderId").asText());
        if("PC".equals(origin.path("sourceKind").asText())) {
            jdbc.queryForList("SELECT id FROM pc_work WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("workId").asText());
            jdbc.queryForList("SELECT id FROM pc_exception_case WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("caseId").asText());
            jdbc.queryForList("SELECT id FROM pc_exception_plan WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("planId").asText());
            jdbc.queryForList("SELECT id FROM pc_disposition WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("dispositionId").asText());
        } else if("FORMAL".equals(origin.path("sourceKind").asText())) lockFormal(origin);
        else jdbc.queryForList("SELECT id FROM submission_scope WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("scopeId").asText());
        jdbc.queryForList("SELECT id FROM bale WHERE id=? FOR UPDATE",Ids.bytes(bale));
        jdbc.queryForList("SELECT bale_id FROM inventory_position WHERE bale_id=? FOR UPDATE",Ids.bytes(bale));
        jdbc.queryForList("SELECT bale_id FROM bale_commitment WHERE bale_id=? FOR UPDATE",Ids.bytes(bale));
        for(String table:java.util.List.of("dispatch_record","inventory_ledger","correction_ledger"))
            jdbc.queryForList("SELECT id FROM "+table+" WHERE bale_id=? ORDER BY id FOR UPDATE",Ids.bytes(bale));
    }

    private void lockFormal(JsonNode origin) {
        jdbc.queryForList("SELECT id FROM submission_scope WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("scopeId").asText());
        jdbc.queryForList("SELECT id FROM order_change_request WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("requestId").asText());
        jdbc.queryForList("SELECT id FROM recovery_package WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("recoveryPackageId").asText());
        jdbc.queryForList("SELECT id FROM order_continuation WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("continuationId").asText());
        jdbc.queryForList("SELECT id FROM physical_disposition_evidence WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("dispositionId").asText());
        jdbc.queryForList("SELECT id FROM submission WHERE id=UUID_TO_BIN(?) FOR UPDATE",origin.path("submissionId").asText());
    }

    ObjectNode read(UUID bale,ObjectNode origin) {
        var result=SubmissionJson.object(); result.set("origin",origin); result.set("stock",stock(bale));
        result.set("owners",owners(bale)); result.set("dispatches",dispatches(bale)); result.set("ledger",ledger(bale));
        return result;
    }

    private JsonNode stock(UUID bale) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('baleId',BIN_TO_UUID(b.id),'warehouseId',BIN_TO_UUID(b.warehouse_id),'barcode',b.barcode,
              'dispatchCycle',CAST(b.dispatch_cycle AS CHAR),'snapshotId',BIN_TO_UUID(p.latest_snapshot_id),
              'lastSeenSnapshotId',BIN_TO_UUID(p.last_seen_snapshot_id),'activeSnapshotId',BIN_TO_UUID(w.active_inventory_snapshot_id),
              'upstreamPresent',p.upstream_present,'bookStatus',p.book_status,'blockedReason',p.blocked_reason,
              'stockVersion',CAST(p.row_version AS CHAR)) data
            FROM bale b JOIN warehouse w ON w.id=b.warehouse_id JOIN inventory_position p ON p.bale_id=b.id WHERE b.id=?
            """,Ids.bytes(bale));
        if(rows.size()!=1) throw CorrectionCommand.corrupt();
        return SubmissionJson.parse(rows.getFirst().get("data").toString());
    }

    private JsonNode owners(UUID bale) {
        return array("""
            SELECT JSON_OBJECT('state',state,'cycle',CAST(dispatch_cycle AS CHAR),'orderId',BIN_TO_UUID(order_id),
              'submissionId',BIN_TO_UUID(submission_id),'recoveryPackageId',BIN_TO_UUID(recovery_package_id),
              'continuationId',BIN_TO_UUID(continuation_id),'sourceScopeId',BIN_TO_UUID(source_scope_id),
              'sourceWarehouseId',BIN_TO_UUID(source_warehouse_id),'sourceEvidenceSetId',BIN_TO_UUID(source_evidence_set_id),
              'dispositionId',BIN_TO_UUID(disposition_id),'dispositionEvidenceId',BIN_TO_UUID(disposition_evidence_id),
              'pcWorkId',BIN_TO_UUID(pc_work_id),'pcDispositionId',BIN_TO_UUID(pc_disposition_id),
              'temporaryContinuationId',BIN_TO_UUID(temporary_continuation_id),'temporaryHandoverId',BIN_TO_UUID(temporary_handover_activation_id)) data
            FROM bale_commitment WHERE bale_id=?
            """,bale);
    }

    private JsonNode dispatches(UUID bale) {
        return array("""
            SELECT JSON_OBJECT('dispatchId',BIN_TO_UUID(d.id),'dispatchCycle',CAST(d.dispatch_cycle AS CHAR),
              'orderId',BIN_TO_UUID(d.order_id),'resultVersion',CAST(o.current_result_version AS CHAR)) data
            FROM dispatch_record d JOIN outbound_order o ON o.id=d.order_id WHERE d.bale_id=? ORDER BY d.id
            """,bale);
    }

    private JsonNode ledger(UUID bale) {
        var result=SubmissionJson.object().putArray("entries");
        for(String table:java.util.List.of("inventory_ledger","correction_ledger"))
            for(var row:array("SELECT JSON_OBJECT('id',BIN_TO_UUID(id),'kind','"+table+"','dispatchId',BIN_TO_UUID(dispatch_id),"
                +"'quantityDelta',CAST(quantity_delta AS CHAR),'weightDeltaG',CAST(weight_delta_g AS CHAR)) data FROM "+table+" WHERE bale_id=? ORDER BY id",bale)) result.add(row);
        return result;
    }

    private JsonNode array(String sql,UUID bale) {
        var result=SubmissionJson.object().putArray("items");
        jdbc.queryForList(sql,Ids.bytes(bale)).forEach(row->result.add(SubmissionJson.parse(row.get("data").toString())));
        return result;
    }
}
