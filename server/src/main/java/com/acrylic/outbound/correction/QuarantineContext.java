package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Nullable order identity is preserved; a TEMP source does not become a formal order during reconciliation. */
@Component
final class QuarantineContext {
    private final QuarantineOrigin origins;
    private final QuarantineFacts facts;
    private final CorrectionStore cases;
    private final ReconciliationRequestStore requests;
    QuarantineContext(QuarantineOrigin origins,QuarantineFacts facts,CorrectionStore cases,ReconciliationRequestStore requests) {
        this.origins=origins; this.facts=facts; this.cases=cases; this.requests=requests;
    }

    ObjectNode read(UUID warehouse,UUID bale,UUID caseId,boolean lock) {
        JsonNode caseFacts=caseId==null?null:cases.reconciliationFacts(caseId);
        if(caseFacts!=null) {
            if(!warehouse.toString().equals(caseFacts.path("warehouseId").asText())
                || !"QUARANTINE_RELEASE".equals(caseFacts.path("kind").asText())) throw CorrectionCommand.missing();
            bale=UUID.fromString(caseFacts.path("baleId").asText());
        }
        if(bale==null) throw CorrectionCommand.missing();
        var origin=origins.read(bale,caseId);
        if(!warehouse.toString().equals(origin.path("warehouseId").asText())) throw CorrectionCommand.missing();
        if(lock) { facts.lock(warehouse,bale,origin); origin=origins.read(bale,caseId); }
        var current=facts.read(bale,origin); var result=SubmissionJson.object();
        result.put("warehouseId",warehouse.toString()).put("baleId",bale.toString()).put("dispositionId",origin.path("dispositionId").asText());
        result.set("orderId",origin.get("orderId")); result.set("facts",current); result.put("factsSha256",ReconciliationFacts.hash(current));
        if(caseFacts!=null) attach(result,caseFacts,caseId); return result;
    }

    private void attach(ObjectNode result,JsonNode caseFacts,UUID id) {
        result.set("case",caseFacts); var opened=requests.original(id,false); result.set("discoveredReceipt",opened);
        if(!"RECONCILE_QUARANTINE_DISCOVER".equals(opened.path("commandType").asText())
            || !result.path("orderId").equals(opened.path("orderId"))) throw CorrectionCommand.corrupt();
        for(String key:Set.of("baleId","dispositionId"))
            if(!result.path(key).equals(opened.path("businessDecision").path(key))) throw CorrectionCommand.corrupt();
        if(Set.of("REVIEW_READY","RESOLVED").contains(caseFacts.path("status").asText())) {
            var submitted=requests.original(id,true);
            if(!"RECONCILE_QUARANTINE_SUBMIT".equals(submitted.path("commandType").asText())) throw CorrectionCommand.corrupt();
            result.set("submittedReceipt",submitted); result.set("submittedCommand",requests.submittedCommand(id));
        }
    }
    byte[] original(JsonNode context) { return origins.original(context.at("/facts/origin")); }
}
