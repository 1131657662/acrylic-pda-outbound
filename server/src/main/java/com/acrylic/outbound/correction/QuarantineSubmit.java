package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;

final class QuarantineSubmit implements CorrectionCommand.Operation {
    private final QuarantineContext context;
    private final ReconciliationContext guard;
    private final CorrectionStore store;
    private final ReconciliationRequestStore requests;
    private final ReconciliationAttachments attachments;
    QuarantineSubmit(QuarantineContext context,ReconciliationContext guard,CorrectionStore store,ReconciliationRequestStore requests,ReconciliationAttachments attachments) {
        this.context=context; this.guard=guard; this.store=store; this.requests=requests; this.attachments=attachments;
    }
    @Override public String commandType() { return "RECONCILE_QUARANTINE_SUBMIT"; }
    @Override public void beforeLocks(CorrectionCommand.Input input) { guard.beforeLocks(input); }
    @Override public JsonNode lockContext(CorrectionCommand.Input input,JsonNode anchor) {
        return context.read(input.uuid("warehouseId"),null,input.uuid("caseId"),true);
    }
    @Override public JsonNode execute(CorrectionCommand.Write write) {
        ReconciliationPolicy.permission(write.actor()); var anchor=write.anchor();
        if(!"OPEN".equals(anchor.at("/case/status").asText())
            || !anchor.path("factsSha256").equals(anchor.at("/discoveredReceipt/businessDecision/factsSha256"))) throw CorrectionCommand.stale();
        if(!write.actor().path("principalId").equals(anchor.at("/discoveredReceipt/actor/principalId"))) throw CorrectionCommand.denied();
        QuarantinePolicy.eligible(anchor.path("facts")); var evidence=write.command().body().path("evidence");
        QuarantinePolicy.evidence(evidence,write.recordedAt()); attachments.verify(evidence,write.actor());
        if(store.advanceReconciliation(write.command().uuid("caseId"),"OPEN","REVIEW_READY",null,write.recordedAt())!=1) throw CorrectionCommand.stale();
        return SubmissionJson.object().put("caseId",write.command().text("caseId")).put("status","REVIEW_READY")
            .put("factsSha256",anchor.path("factsSha256").asText()).put("availability","UNCHANGED");
    }
    @Override public void afterReceipt(CorrectionCommand.Write write,JsonNode decision) { requests.submitted(write); }
}
