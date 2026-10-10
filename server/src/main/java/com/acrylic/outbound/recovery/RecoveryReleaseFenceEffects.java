package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Only the existing signed fence's case, source exclusion and outbox effects are admissible. */
final class RecoveryReleaseFenceEffects {
    private final RecoveryReleaseFenceReceipts receipts;
    private final JsonNode inventory;
    private final Instant observedAt;
    RecoveryReleaseFenceEffects(RecoveryReleaseFenceReceipts receipts,JsonNode inventory,Instant observedAt) {
        this.receipts=receipts; this.inventory=inventory; this.observedAt=observedAt;
    }
    void changed(String table,List<Map<String,String>> original,List<Map<String,String>> actual) {
        require(original.size()==actual.size(),"RECOVERY_SCOPE_CHANGED_COUNT_"+table);
        var byId=new HashMap<String,Map<String,String>>();
        for(var row:actual) require(byId.put(row.get("id"),row)==null,"RECOVERY_SCOPE_DUPLICATE");
        for(var row:original) {
            var after=byId.get(row.get("id")); require(after!=null,"RECOVERY_SCOPE_MISSING_ROW");
            var expected=new LinkedHashMap<>(row);
            if(table.equals("device") && row.get("id").equals(id(text(inventory,"deviceId")))) {
                eq(row,"status","ACTIVE"); expected.put("status",encoded("DISABLED"));
                expected.put("row_version",encoded(Long.toString(Math.addExact(Long.parseLong(text(row,"row_version")),1))));
                require(expected.get("row_version").equals(encoded(text(receipts.fault.path("businessDecision"),"disabledDeviceVersion"))),"RECOVERY_SCOPE_DEVICE_VERSION");
            } else if(table.equals("outbound_order") && row.get("id").equals(id(text(receipts.fault.path("caseContext"),"canonicalOrderId")))) {
                expected.put("row_version",encoded(Long.toString(Math.addExact(Long.parseLong(text(row,"row_version")),1))));
                require(time(after,"updated_at").equals(faultTime()),"RECOVERY_SCOPE_ORDER_TIME"); expected.put("updated_at",after.get("updated_at"));
            } else if(table.equals("pda_session") && row.get("device_id").equals(id(text(inventory,"deviceId"))) && row.get("revoked_at")==null) {
                require(!time(after,"revoked_at").isBefore(faultTime()) && !time(after,"revoked_at").isAfter(observedAt),"RECOVERY_SCOPE_REVOCATION_TIME");
                expected.put("revoked_at",after.get("revoked_at"));
            }
            require(expected.equals(after),"RECOVERY_SCOPE_ORIGINAL_CHANGED_"+table);
        }
    }
    void added(String table,Map<String,String> row) {
        switch(table) {
            case "handover_case" -> kase(row);
            case "handover_fault_fence" -> fence(row);
            case "order_sync_outbox" -> outbox(row);
            default -> throw new RecoveryControlException("RECOVERY_SCOPE_EFFECT_TABLE");
        }
    }
    private void kase(Map<String,String> row) {
        var context=receipts.fault.path("caseContext"); var decision=receipts.fault.path("businessDecision");
        var expected=new LinkedHashMap<String,String>(); row.keySet().forEach(c->expected.put(c,null));
        Map.of("id","caseId","warehouse_id","warehouseId","scope_id","scopeId","submission_id","submissionId",
            "canonical_order_id","canonicalOrderId","requested_by","requestedBy").forEach((c,f)->expected.put(c,id(text(context,f))));
        Map.of("source_kind","TEMP","from_work_round",text(context,"fromWorkRound"),"to_work_round",text(context,"toWorkRound"),
            "status","EVIDENCE_PENDING","stop_state","FAULT_FENCED","integrity_state","COMPLETE","original_result_state","ABORTED",
            "physical_state","UNCHECKED","open_flag","1","row_version","2").forEach((c,v)->expected.put(c,encoded(v)));
        expected.put("input_high_watermark",encoded(text(decision.path("archivedEvidence"),"orderScanHighWatermark")));
        for(String c:List.of("requested_at","created_at")) {
            require(time(row,c).equals(Instant.parse(text(receipts.request,"recordedAt"))),"RECOVERY_SCOPE_CASE_TIME"); expected.put(c,row.get(c));
        }
        require(time(row,"updated_at").equals(faultTime()),"RECOVERY_SCOPE_CASE_TIME"); expected.put("updated_at",row.get("updated_at"));
        require(expected.equals(row),"RECOVERY_SCOPE_CASE_CHANGED");
    }
    private void fence(Map<String,String> row) {
        var decision=receipts.fault.path("businessDecision"); var source=decision.path("source"); var archive=decision.path("archivedEvidence");
        var expected=new LinkedHashMap<String,String>();
        Map.of("case_id",text(decision,"caseId"),"warehouse_id",text(source,"warehouseId"),"scope_id",text(source,"scopeId"),
            "submission_id",text(source,"submissionId"),"source_device_id",text(source,"deviceId"),
            "source_evidence_set_id",text(archive,"sourceEvidenceSetId"),"verification_id",text(archive,"verificationId"),
            "command_key",text(receipts.fault,"commandId"),"recorded_by",text(receipts.fault.path("actor"),"principalId"))
            .forEach((c,v)->expected.put(c,id(v)));
        expected.put("device_version",encoded(text(decision,"disabledDeviceVersion")));
        expected.put("input_high_watermark",encoded(text(archive,"orderScanHighWatermark")));
        expected.put("command_sha256",encoded(text(receipts.fault,"commandSha256")));
        require(time(row,"recorded_at").equals(faultTime()),"RECOVERY_SCOPE_FENCE_TIME"); expected.put("recorded_at",row.get("recorded_at"));
        require(expected.equals(row),"RECOVERY_SCOPE_FENCE_CHANGED");
    }
    private void outbox(Map<String,String> row) {
        var source=receipts.fault.path("businessDecision").path("source"); var context=receipts.fault.path("caseContext");
        var expected=SubmissionJson.object().put("caseId",text(context,"caseId")).put("orderId",text(source,"orderId"))
            .put("scopeId",text(source,"scopeId")).put("status","REVIEW_REQUIRED").put("stopKind","FAULT_FENCE")
            .put("sourceDeviceId",text(source,"deviceId")).put("oldDeviceAcknowledged",false);
        require(SubmissionJson.parse(text(row,"payload")).equals(expected),"RECOVERY_SCOPE_OUTBOX_PAYLOAD");
        uuid(row,"warehouse_id",text(inventory,"warehouseId")); uuid(row,"order_id",text(source,"orderId"));
        eq(row,"recovery_epoch",text(inventory.path("control"),"transportRecoveryEpoch"));
        eq(row,"event_type","HANDOVER_FAULT_FENCED"); eq(row,"published_seq",null);
        require(RecoveryReleaseRows.bytes(row.get("id")).length==16 && time(row,"created_at").equals(faultTime()),"RECOVERY_SCOPE_OUTBOX_TIME");
        var orders=decode(inventory.path("tables").path("outbound_order"));
        require(orders.size()==1 && Objects.equals(row.get("entity_version"),orders.getFirst().get("row_version")),"RECOVERY_SCOPE_OUTBOX_VERSION");
        require(row.keySet().equals(Set.of("id","recovery_epoch","warehouse_id","order_id","entity_version","event_type","payload","published_seq","created_at")),"RECOVERY_SCOPE_OUTBOX_COLUMNS");
    }
    private Instant faultTime() { return Instant.parse(text(receipts.fault,"recordedAt")); }
}
