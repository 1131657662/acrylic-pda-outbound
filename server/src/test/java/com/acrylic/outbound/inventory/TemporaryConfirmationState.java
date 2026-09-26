package com.acrylic.outbound.inventory;

import java.util.*;

/** Compare all observable writes, including orphan reservations and derived sync queues after rollback. */
final class TemporaryConfirmationState {
    private TemporaryConfirmationState() { }
    static Map<String,Object> read(TemporaryConfirmationFixture f) {
        var jdbc=f.jdbc;var id=f.source.id();var result=new LinkedHashMap<String,Object>();
        String order=jdbc.queryForObject("SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)",String.class,id);
        result.put("submission",jdbc.queryForMap("SELECT status,receipt_text,issues_json,updated_at FROM submission WHERE id=UUID_TO_BIN(?)",id));
        result.put("decision",jdbc.queryForMap("SELECT d.decision_version FROM temporary_decision_state d JOIN submission s ON s.scope_id=d.scope_id WHERE s.id=UUID_TO_BIN(?)",id));
        result.put("receipts",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,original_response_sha256 FROM temporary_command_receipt WHERE submission_id=UUID_TO_BIN(?) ORDER BY id",id));
        result.put("requests",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,request_sha256 FROM api_idempotency_request WHERE route_scope='r06.command.v1' ORDER BY id"));
        result.put("events",jdbc.queryForList("SELECT BIN_TO_UUID(e.id) id FROM temporary_decision_event e JOIN temporary_command_receipt r ON r.id=e.receipt_id WHERE r.submission_id=UUID_TO_BIN(?) ORDER BY e.id",id));
        result.put("audit",jdbc.queryForList("SELECT id,event_type,details FROM operation_event WHERE entity_id=UUID_TO_BIN(?) OR entity_type='TEMPORARY_COMMAND' ORDER BY id",order));
        result.put("dispatch",jdbc.queryForList("SELECT BIN_TO_UUID(id) id FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?) ORDER BY id",id));
        result.put("order",jdbc.queryForMap("SELECT status,row_version,current_result_version,updated_at FROM outbound_order WHERE id=UUID_TO_BIN(?)",order));
        result.put("result",jdbc.queryForList("SELECT result_version,header_snapshot FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)",id));
        result.put("slot",jdbc.queryForMap("SELECT phase,slot_version FROM order_submission_slot WHERE current_submission_id=UUID_TO_BIN(?)",id));
        result.put("stock",jdbc.queryForList("SELECT BIN_TO_UUID(k.bale_id) bale,k.dispatch_cycle,k.state,p.book_status,p.row_version FROM bale_commitment k JOIN inventory_position p ON p.bale_id=k.bale_id WHERE k.submission_id=UUID_TO_BIN(?) ORDER BY k.bale_id",id));
        result.put("ledger",jdbc.queryForList("SELECT BIN_TO_UUID(l.id) id FROM inventory_ledger l JOIN bale_commitment k ON k.bale_id=l.bale_id WHERE k.submission_id=UUID_TO_BIN(?) ORDER BY l.id",id));
        result.put("barcode",jdbc.queryForList("SELECT BIN_TO_UUID(p.bale_id) bale,p.row_version FROM barcode_sync_pending p JOIN bale_commitment k ON k.bale_id=p.bale_id WHERE k.submission_id=UUID_TO_BIN(?) ORDER BY p.bale_id",id));
        result.put("outbox",jdbc.queryForList("SELECT BIN_TO_UUID(id) id,entity_version,event_type,payload FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) ORDER BY entity_version",order));
        return result;
    }
}
