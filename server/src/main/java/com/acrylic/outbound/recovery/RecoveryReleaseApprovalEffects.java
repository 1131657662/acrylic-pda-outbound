package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Validates signed approval effects before deriving their pre-approval comparison view. */
final class RecoveryReleaseApprovalEffects {
    final JsonNode receipt,command;
    final Map<String,String> row;
    RecoveryReleaseApprovalEffects(JsonNode tables,RecoveryReceiptKeys keys,JsonNode inventory) {
        String caseId=text(inventory.path("faultReceipt").path("businessDecision"),"caseId");
        var selected=decode(tables.path("handover_command_receipt")).stream()
            .filter(r->Objects.equals(r.get("case_id"),id(caseId)) && text(r,"command_type").equals("APPROVE")).toList();
        require(selected.size()==1,"RECOVERY_SCOPE_APPROVAL_REQUIRED"); row=selected.getFirst();
        receipt=RecoveryReleaseFenceReceipts.receipt(row,keys);
        command=SubmissionJson.parse(RecoveryReleaseRows.bytes(row.get("canonical_command_bytes")));
        bind(inventory); dimensions(inventory);
    }
    private void bind(JsonNode inventory) {
        var fault=inventory.path("faultReceipt"); var context=receipt.path("caseContext");
        String caseId=text(fault.path("businessDecision"),"caseId");
        require(text(receipt,"routeScope").equals("r07.handover.v1") && text(receipt,"status").equals("RECORDED"),"RECOVERY_SCOPE_APPROVAL_BINDING");
        require(text(command,"caseId").equals(caseId) && text(context,"caseId").equals(caseId)
            && text(command,"warehouseId").equals(text(inventory,"warehouseId"))
            && text(receipt.path("actor"),"warehouseId").equals(text(inventory,"warehouseId"))
            && text(command,"transportRecoveryEpoch").equals(text(inventory.path("control"),"transportRecoveryEpoch")),"RECOVERY_SCOPE_APPROVAL_BINDING");
        for(String field:List.of("warehouseId","scopeId","submissionId","canonicalOrderId","sourceKind","fromWorkRound","toWorkRound","requestedBy"))
            require(context.path(field).equals(fault.path("caseContext").path(field)),"RECOVERY_SCOPE_APPROVAL_SOURCE");
        require(text(context,"caseVersion").equals("2") && text(context,"status").equals("EVIDENCE_PENDING")
            && text(context,"physicalState").equals("UNCHECKED") && text(context,"stopState").equals("FAULT_FENCED")
            && !text(receipt.path("actor"),"principalId").equals(text(context,"requestedBy"))
            && !text(receipt.path("actor"),"principalId").equals(text(fault.path("businessDecision").path("source"),"operatorId")),"RECOVERY_SCOPE_APPROVAL_ACTOR");
    }
    private void dimensions(JsonNode inventory) {
        for(var entry:Map.of("physicalState","MATCHED","integrityState","COMPLETE","stopState","FAULT_FENCED","originalResultState","ABORTED").entrySet())
            require(text(command,entry.getKey()).equals(entry.getValue()),"RECOVERY_SCOPE_APPROVAL_DIMENSIONS");
        String high=text(inventory.path("faultReceipt").path("businessDecision").path("archivedEvidence"),"orderScanHighWatermark");
        require(text(command,"inputHighWatermark").equals(high)
            && !Instant.parse(text(receipt,"recordedAt")).isBefore(Instant.parse(text(inventory.path("faultReceipt"),"recordedAt"))),"RECOVERY_SCOPE_APPROVAL_TIME");
        var expected=SubmissionJson.object().put("caseId",text(command,"caseId")).put("status","APPROVED")
            .put("approvedBy",text(receipt.path("actor"),"principalId")).put("inputHighWatermark",high)
            .put("retained",Integer.toString(command.path("checklist").size())).put("evidenceRecorded","1");
        require(receipt.path("businessDecision").equals(expected),"RECOVERY_SCOPE_APPROVAL_DECISION");
    }
    Map<String,String> caseBefore(Map<String,String> current,JsonNode inventory) {
        var before=new LinkedHashMap<>(current);
        uuid(current,"id",text(command,"caseId")); eq(current,"status","APPROVED"); eq(current,"physical_state","MATCHED");
        eq(current,"row_version","3"); uuid(current,"approved_by",text(receipt.path("actor"),"principalId"));
        for(String field:List.of("approved_at","updated_at"))
            require(time(current,field).equals(Instant.parse(text(receipt,"recordedAt"))),"RECOVERY_SCOPE_APPROVAL_CASE_TIME");
        before.put("status",encoded("EVIDENCE_PENDING")); before.put("physical_state",encoded("UNCHECKED"));
        before.put("row_version",encoded("2")); before.put("approved_by",null); before.put("approved_at",null);
        var fence=decode(inventory.path("tables").path("handover_fault_fence"));
        require(fence.size()==1,"RECOVERY_SCOPE_APPROVAL_FENCE"); before.put("updated_at",fence.getFirst().get("recorded_at"));
        return before;
    }
    void checklist(List<Map<String,String>> rows) {
        var lines=command.path("checklist"); require(lines.isArray() && rows.size()==lines.size(),"RECOVERY_SCOPE_APPROVAL_CHECKLIST");
        var byIndex=new HashMap<String,Map<String,String>>();
        for(var current:rows) require(byIndex.put(text(current,"item_no"),current)==null,"RECOVERY_SCOPE_APPROVAL_CHECKLIST");
        for(int i=0;i<lines.size();i++) {
            var line=lines.get(i); var current=byIndex.get(Integer.toString(i)); require(current!=null,"RECOVERY_SCOPE_APPROVAL_CHECKLIST");
            fields(line,"baleId deliveryCode state dispatchCycle"); require(text(line,"state").equals("RETAINED"),"RECOVERY_SCOPE_APPROVAL_RETAIN_ONLY");
            uuid(current,"case_id",text(command,"caseId")); uuid(current,"bale_id",text(line,"baleId"));
            uuid(current,"verified_by",text(receipt.path("actor"),"principalId"));
            eq(current,"delivery_code",text(line,"deliveryCode")); eq(current,"state","RETAINED"); eq(current,"dispatch_cycle",text(line,"dispatchCycle"));
            require(RecoveryReleaseRows.bytes(current.get("id")).length==16 && time(current,"verified_at").equals(Instant.parse(text(receipt,"recordedAt"))),"RECOVERY_SCOPE_APPROVAL_CHECKLIST_TIME");
            require(current.keySet().equals(Set.of("id","case_id","item_no","bale_id","delivery_code","state","dispatch_cycle","verified_by","verified_at")),"RECOVERY_SCOPE_APPROVAL_CHECKLIST_COLUMNS");
        }
    }
}
