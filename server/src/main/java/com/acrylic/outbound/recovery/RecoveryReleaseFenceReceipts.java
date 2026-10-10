package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryReleaseRows.*;

/** Exactly two original signed commands explain this single recovered TEMP fence. */
final class RecoveryReleaseFenceReceipts {
    final JsonNode request,fault,command;
    final Map<String,String> requestRow,faultRow;

    RecoveryReleaseFenceReceipts(JsonNode tables,RecoveryReceiptKeys keys,JsonNode inventory) {
        var rows=decode(tables.path("handover_command_receipt"));
        String caseId=text(inventory.path("faultReceipt").path("businessDecision"),"caseId");
        var selected=rows.stream().filter(r->Objects.equals(r.get("case_id"),id(caseId))).toList();
        require(selected.size()==2,"RECOVERY_SCOPE_COMMAND_COUNT");
        requestRow=one(selected,"REQUEST"); faultRow=one(selected,"FAULT_FENCE");
        request=receipt(requestRow,keys); fault=receipt(faultRow,keys);
        command=SubmissionJson.parse(RecoveryReleaseRows.bytes(faultRow.get("canonical_command_bytes")));
        require(fault.equals(inventory.path("faultReceipt")),"RECOVERY_SCOPE_FAULT_BINDING");
        require(!text(request.path("actor"),"principalId").equals(text(fault.path("actor"),"principalId"))
            && !Instant.parse(text(request,"recordedAt")).isAfter(Instant.parse(text(fault,"recordedAt"))),"RECOVERY_SCOPE_APPROVAL");
        for(var receipt:List.of(request,fault)) {
            require(text(receipt,"routeScope").equals("r07.handover.v1") && text(receipt,"status").equals("RECORDED"),"RECOVERY_SCOPE_RECEIPT_BINDING");
            require(text(receipt.path("businessDecision"),"caseId").equals(caseId)
                && text(receipt.path("actor"),"warehouseId").equals(text(inventory,"warehouseId")),"RECOVERY_SCOPE_RECEIPT_BINDING");
        }
        require(text(command,"transportRecoveryEpoch").equals(text(inventory.path("control"),"transportRecoveryEpoch")),"RECOVERY_SCOPE_EPOCH");
        bindSource(inventory);
    }
    private void bindSource(JsonNode inventory) {
        var source=fault.path("businessDecision").path("source"); var context=fault.path("caseContext");
        var originalRequest=SubmissionJson.parse(RecoveryReleaseRows.bytes(requestRow.get("canonical_command_bytes")));
        require(text(originalRequest,"scopeId").equals(text(inventory,"scopeId"))
            && text(originalRequest,"warehouseId").equals(text(inventory,"warehouseId"))
            && text(originalRequest,"transportRecoveryEpoch").equals(text(command,"transportRecoveryEpoch"))
            && text(command,"caseId").equals(text(context,"caseId"))
            && text(command,"sourceDeviceId").equals(text(inventory,"deviceId")),"RECOVERY_SCOPE_COMMAND_SOURCE");
        for(String field:List.of("warehouseId","scopeId","canonicalOrderId","sourceKind","fromWorkRound"))
            require(request.path("caseContext").path(field).equals(context.path(field)),"RECOVERY_SCOPE_REQUEST_CONTEXT");
        require(text(request.path("caseContext"),"currentSubmissionId").equals(text(inventory,"submissionId"))
            && text(context,"requestedBy").equals(text(request.path("actor"),"principalId"))
            && !text(source,"operatorId").equals(text(fault.path("actor"),"principalId"))
            && text(context,"caseVersion").equals("1") && text(context,"physicalState").equals("UNCHECKED")
            && text(context,"stopState").equals("REQUESTED")
            && Long.parseLong(text(context,"toWorkRound"))==Math.addExact(Long.parseLong(text(context,"fromWorkRound")),1),"RECOVERY_SCOPE_REQUEST_CONTEXT");
    }
    private Map<String,String> one(List<Map<String,String>> rows,String kind) {
        var found=rows.stream().filter(r->text(r,"command_type").equals(kind)).toList();
        require(found.size()==1,"RECOVERY_SCOPE_COMMAND_KIND"); return found.getFirst();
    }
    static JsonNode receipt(Map<String,String> row,RecoveryReceiptKeys keys) {
        var response=RecoveryReleaseRows.bytes(row.get("response_bytes")); var receipt=RecoveryReleaseFenceSignature.verify(response,keys);
        var original=RecoveryReleaseRows.bytes(row.get("canonical_command_bytes")); var command=SubmissionJson.parse(original);
        require(Arrays.equals(original,SubmissionJson.canonical(command).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"RECOVERY_SCOPE_COMMAND_CANONICAL");
        require(sha256(original).equals(text(receipt,"commandSha256"))
            && row.get("command_sha256").equals(hashBytes(sha256(original)))
            && row.get("response_sha256").equals(hashBytes(sha256(response))),"RECOVERY_SCOPE_COMMAND_HASH");
        uuid(row,"id",text(receipt,"receiptId")); uuid(row,"original_key",text(receipt,"commandId"));
        uuid(row,"actor_id",text(receipt.path("actor"),"principalId")); uuid(row,"event_id",text(receipt,"eventId"));
        uuid(row,"case_id",text(receipt.path("businessDecision"),"caseId")); eq(row,"status","RECORDED");
        eq(row,"command_type",text(receipt,"commandType"));
        require(text(command,"commandId").equals(text(receipt,"commandId")) && text(command,"commandType").equals(text(receipt,"commandType"))
            && command.path("expected").equals(receipt.path("expected"))
            && SubmissionJson.parse(text(row,"expected_json")).equals(receipt.path("expected"))
            && time(row,"recorded_at").equals(Instant.parse(text(receipt,"recordedAt"))),"RECOVERY_SCOPE_COMMAND_BINDING");
        require(receipt.path("capabilities").equals(SubmissionJson.object().put("writeConfirmationAvailable",false).put("releaseAllowed",false)
            .put("mayResumeScanning",false).put("stockReleased",false).put("localDeletionAllowed",false)),"RECOVERY_SCOPE_CAPABILITIES");
        return receipt;
    }
}
