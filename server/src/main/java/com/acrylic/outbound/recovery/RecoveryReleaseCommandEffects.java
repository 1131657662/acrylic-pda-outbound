package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Exact original rows plus one idempotency/audit effect for each verified command. */
final class RecoveryReleaseCommandEffects {
    static void verify(String table,List<Map<String,String>> added,RecoveryReleaseFenceReceipts receipts,Instant observedAt) {
        require(added.size()==2,"RECOVERY_SCOPE_COMMAND_EFFECT_COUNT");
        for(var receipt:List.of(receipts.request,receipts.fault)) {
            var commandRow=receipt==receipts.request?receipts.requestRow:receipts.faultRow;
            if(table.equals("handover_command_receipt")) { require(added.contains(commandRow),"RECOVERY_SCOPE_RECEIPT_ROW"); continue; }
            String field=table.equals("operation_event")?"entity_id":"resource_id";
            var rows=added.stream().filter(r->Objects.equals(r.get(field),id(text(receipt,"receiptId")))).toList();
            require(rows.size()==1,"RECOVERY_SCOPE_COMMAND_EFFECT_BINDING");
            if(table.equals("operation_event")) audit(rows.getFirst(),receipt,commandRow,observedAt);
            else idempotency(rows.getFirst(),receipt);
        }
    }
    private static void idempotency(Map<String,String> row,JsonNode receipt) {
        uuid(row,"principal_id",text(receipt.path("actor"),"principalId"));
        eq(row,"route_scope",text(receipt,"routeScope")); eq(row,"idempotency_key",text(receipt,"commandId"));
        eq(row,"request_sha256",text(receipt,"commandSha256"));
        require(RecoveryReleaseRows.bytes(row.get("id")).length==16 && time(row,"created_at").equals(Instant.parse(text(receipt,"recordedAt"))),"RECOVERY_SCOPE_IDEMPOTENCY_TIME");
        require(row.keySet().equals(Set.of("id","principal_id","route_scope","idempotency_key","request_sha256","resource_id","created_at")),"RECOVERY_SCOPE_IDEMPOTENCY_COLUMNS");
    }
    private static void audit(Map<String,String> row,JsonNode receipt,Map<String,String> commandRow,Instant observedAt) {
        uuid(row,"actor_id",text(receipt.path("actor"),"principalId"));
        eq(row,"event_type","HANDOVER_"+text(receipt,"commandType")); eq(row,"entity_type","HANDOVER_COMMAND");
        var expected=SubmissionJson.object().put("commandSha256",text(receipt,"commandSha256"))
            .put("originalResponseSha256",sha256(RecoveryReleaseRows.bytes(commandRow.get("response_bytes"))))
            .put("caseId",text(receipt.path("businessDecision"),"caseId"));
        require(SubmissionJson.parse(text(row,"details")).equals(expected),"RECOVERY_SCOPE_AUDIT_DETAILS");
        var at=time(row,"server_time"); require(!at.isBefore(Instant.parse(text(receipt,"recordedAt"))) && !at.isAfter(observedAt),"RECOVERY_SCOPE_AUDIT_TIME");
        require(text(row,"id").matches("[1-9][0-9]*") && !text(row,"request_id").isBlank(),"RECOVERY_SCOPE_AUDIT_ID");
        require(row.keySet().equals(Set.of("id","actor_id","event_type","entity_type","entity_id","details","server_time","request_id")),"RECOVERY_SCOPE_AUDIT_COLUMNS");
    }
    private RecoveryReleaseCommandEffects() { }
}
