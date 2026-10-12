package com.acrylic.outbound.correction;

import com.fasterxml.jackson.databind.JsonNode;

final class QuarantineApprove implements CorrectionCommand.Operation {
    private final QuarantineContext context;
    private final ReconciliationContext guard;
    private final CorrectionStore store;
    private final ReconciliationAttachments attachments;
    private final QuarantinePosting posting;
    QuarantineApprove(QuarantineContext context,ReconciliationContext guard,CorrectionStore store,ReconciliationAttachments attachments,QuarantinePosting posting) {
        this.context=context; this.guard=guard; this.store=store; this.attachments=attachments; this.posting=posting;
    }
    @Override public String commandType() { return "RECONCILE_QUARANTINE_APPROVE"; }
    @Override public void beforeLocks(CorrectionCommand.Input input) { guard.beforeLocks(input); }
    @Override public JsonNode lockContext(CorrectionCommand.Input input,JsonNode anchor) {
        return context.read(input.uuid("warehouseId"),null,input.uuid("caseId"),true);
    }
    @Override public JsonNode execute(CorrectionCommand.Write write) {
        var anchor=write.anchor(); var submitted=anchor.path("submittedReceipt");
        if(!"REVIEW_READY".equals(anchor.at("/case/status").asText())) throw CorrectionCommand.stale();
        if(write.actor().path("principalId").equals(submitted.at("/actor/principalId"))
            || write.actor().path("principalId").equals(anchor.at("/discoveredReceipt/actor/principalId"))) throw CorrectionCommand.selfApproval();
        if(!anchor.path("factsSha256").equals(submitted.at("/businessDecision/factsSha256"))
            || !anchor.path("factsSha256").equals(anchor.at("/discoveredReceipt/businessDecision/factsSha256"))) throw CorrectionCommand.stale();
        QuarantinePolicy.evidence(anchor.at("/submittedCommand/evidence"),write.recordedAt());
        attachments.verify(anchor.at("/submittedCommand/evidence"),submitted.path("actor")); var result=posting.apply(write);
        if(store.advanceReconciliation(write.command().uuid("caseId"),"REVIEW_READY","RESOLVED",write.command().text("reason"),write.recordedAt())!=1)
            throw CorrectionCommand.stale();
        return result.put("caseId",write.command().text("caseId")).put("status","RESOLVED")
            .put("submittedReceiptId",submitted.path("receiptId").asText()).put("factsSha256",anchor.path("factsSha256").asText());
    }
    @Override public void afterReceipt(CorrectionCommand.Write write,JsonNode decision) { posting.record(write); }
}
