package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Removes only separately verified approval effects from a copy, never from the actual database. */
final class RecoveryReleaseApprovedRows {
    static ObjectNode before(JsonNode actual,RecoveryReleaseApprovalEffects approval,Context context) {
        ObjectNode result=actual.deepCopy();
        for(String table:List.of("handover_command_receipt","api_idempotency_request","operation_event")) {
            var rows=decode(result.path(table)); String field=table.equals("handover_command_receipt")?"id":table.equals("operation_event")?"entity_id":"resource_id";
            var added=rows.stream().filter(r->Objects.equals(r.get(field),id(text(approval.receipt,"receiptId")))).toList();
            require(added.size()==1,"RECOVERY_SCOPE_APPROVAL_EFFECT_COUNT"); var row=added.getFirst();
            if(table.equals("operation_event")) RecoveryReleaseCommandEffects.audit(row,approval.receipt,approval.row,context.observedAt());
            if(table.equals("api_idempotency_request")) RecoveryReleaseCommandEffects.idempotency(row,approval.receipt);
            rows.remove(row); set(result,table,rows);
        }
        var cases=decode(result.path("handover_case")); int index=-1;
        for(int i=0;i<cases.size();i++) if(Objects.equals(cases.get(i).get("id"),id(text(approval.command,"caseId")))) index=i;
        require(index>=0,"RECOVERY_SCOPE_APPROVAL_CASE"); cases.set(index,approval.caseBefore(cases.get(index),context.inventory())); set(result,"handover_case",cases);
        var checklist=decode(result.path("handover_checklist"));
        var selected=checklist.stream().filter(r->Objects.equals(r.get("case_id"),id(text(approval.command,"caseId")))).toList();
        approval.checklist(selected); checklist.removeAll(selected); set(result,"handover_checklist",checklist);
        return result;
    }
    private static void set(ObjectNode tables,String name,List<Map<String,String>> rows) {
        var table=(ObjectNode)tables.path(name); table.set("rows",encode(table.path("columns"),rows));
    }
    record Context(JsonNode inventory,Instant observedAt) { }
    private RecoveryReleaseApprovedRows() { }
}
