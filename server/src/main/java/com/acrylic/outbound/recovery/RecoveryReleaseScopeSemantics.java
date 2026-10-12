package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Preserves the complete original endpoint; unknown work is never inferred safe. */
final class RecoveryReleaseScopeSemantics {
    static void verify(JsonNode original,JsonNode actual,RecoveryReleaseFenceReceipts receipts,Context context) {
        var effects=new RecoveryReleaseFenceEffects(receipts,context.inventory(),context.observedAt());
        for(String name:RecoveryReleaseInventoryRows.tableNames()) {
            var before=original.path(name); var after=actual.path(name);
            require(before.path("columns").equals(after.path("columns")),"RECOVERY_SCOPE_COLUMNS_CHANGED_"+name);
            var oldRows=decode(before); var newRows=decode(after);
            if(Set.of("device","pda_session","outbound_order").contains(name)) { effects.changed(name,oldRows,newRows); continue; }
            var additions=new ArrayList<>(newRows);
            for(var row:oldRows) require(additions.remove(row),"RECOVERY_SCOPE_ORIGINAL_CHANGED_"+name);
            switch(name) {
                case "handover_case","handover_fault_fence","order_sync_outbox" -> {
                    require(additions.size()==1,"RECOVERY_SCOPE_ADDITION_COUNT_"+name); effects.added(name,additions.getFirst());
                }
                case "handover_command_receipt","api_idempotency_request","operation_event" ->
                    RecoveryReleaseCommandEffects.verify(name,additions,receipts,context.observedAt());
                default -> require(additions.isEmpty(),"RECOVERY_SCOPE_UNEXPECTED_ADDITION_"+name);
            }
        }
        knownWork(context.inventory(),receipts);
    }
    private static void knownWork(JsonNode inventory,RecoveryReleaseFenceReceipts receipts) {
        var tables=inventory.path("tables");
        for(String table:List.of("device","submission_scope","submission","temporary_work_origin","outbound_order","handover_case"))
            require(tables.path(table).path("rows").size()==1,"RECOVERY_SCOPE_UNRECONCILED_"+table);
        for(String name:RecoveryReleaseInventoryRows.tableNames()) {
            if(name.startsWith("pc_") || name.startsWith("correction_") || name.startsWith("reconciliation_")
                || Set.of("returned_stock_release","quarantine_origin","quarantine_release","cycle_reopening","inventory_discrepancy",
                    "inventory_reconciliation","dispatch_record","inventory_ledger","order_result_version","order_current_result",
                    "order_assignment","order_continuation","temporary_source_continuation","temporary_handover_activation_ticket").contains(name))
                require(tables.path(name).path("rowCount").asText().equals("0"),"RECOVERY_SCOPE_UNRECONCILED_"+name);
        }
        eq(decode(tables.path("submission")).getFirst(),"status","ABORTED");
        eq(decode(tables.path("outbound_order")).getFirst(),"status","REVIEW_REQUIRED");
        var source=receipts.fault.path("businessDecision").path("source");
        for(String field:List.of("warehouseId","deviceId","scopeId","submissionId"))
            require(text(source,field).equals(text(inventory,field)),"RECOVERY_SCOPE_SOURCE_ID");
        require(text(source,"sourceKind").equals("TEMP") && text(receipts.fault.path("businessDecision"),"status").equals("FAULT_FENCED"),"RECOVERY_SCOPE_SOURCE_KIND");
        for(var row:decode(tables.path("bale_commitment"))) {
            eq(row,"state","REVIEW_RESERVED"); uuid(row,"source_scope_id",text(inventory,"scopeId"));
        }
    }
    record Context(JsonNode inventory,Instant observedAt) { }
    private RecoveryReleaseScopeSemantics() { }
}
