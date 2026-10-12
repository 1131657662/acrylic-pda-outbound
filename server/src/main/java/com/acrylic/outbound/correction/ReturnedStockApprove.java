package com.acrylic.outbound.correction;

import com.fasterxml.jackson.databind.JsonNode;

final class ReturnedStockApprove implements CorrectionCommand.Operation {
    private final ReconciliationContext contexts;
    private final CorrectionStore store;
    private final ReconciliationAttachments attachments;
    private final ReturnedStockPosting posting;
    ReturnedStockApprove(ReconciliationContext contexts, CorrectionStore store,
        ReconciliationAttachments attachments, ReturnedStockPosting posting) {
        this.contexts = contexts; this.store = store; this.attachments = attachments; this.posting = posting;
    }
    @Override public String commandType() { return "RECONCILE_RETURN_APPROVE"; }
    @Override public void beforeLocks(CorrectionCommand.Input input) { contexts.beforeLocks(input); }
    @Override public JsonNode lockContext(CorrectionCommand.Input input, JsonNode anchor) {
        return contexts.returned(input.uuid("warehouseId"), null, input.uuid("caseId"), true);
    }
    @Override public JsonNode execute(CorrectionCommand.Write write) {
        var anchor = write.anchor(); var submitted = anchor.path("submittedReceipt");
        if (!"REVIEW_READY".equals(anchor.at("/case/status").asText())) throw CorrectionCommand.stale();
        if (write.actor().path("principalId").equals(submitted.at("/actor/principalId"))
            || write.actor().path("principalId").equals(anchor.at("/discoveredReceipt/actor/principalId"))) throw CorrectionCommand.selfApproval();
        if (!anchor.path("factsSha256").equals(submitted.at("/businessDecision/factsSha256"))
            || !anchor.path("factsSha256").equals(anchor.at("/discoveredReceipt/businessDecision/factsSha256"))) throw CorrectionCommand.stale();
        ReconciliationPolicy.returnedEvidence(anchor.at("/submittedCommand/evidence"), write.recordedAt());
        attachments.verify(anchor.at("/submittedCommand/evidence"), submitted.path("actor"));
        var result = posting.apply(write);
        if (store.advanceReconciliation(write.command().uuid("caseId"), "REVIEW_READY", "RESOLVED",
            write.command().text("reason"), write.recordedAt())!=1) throw CorrectionCommand.stale();
        return result.put("caseId", write.command().text("caseId")).put("status", "RESOLVED")
            .put("submittedReceiptId", submitted.path("receiptId").asText()).put("factsSha256", anchor.path("factsSha256").asText());
    }
    @Override public void afterReceipt(CorrectionCommand.Write write, JsonNode decision) { posting.record(write); }
}
