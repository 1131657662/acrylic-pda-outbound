package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;

final class ReturnedStockSubmit implements CorrectionCommand.Operation {
    private final ReconciliationContext contexts;
    private final CorrectionStore store;
    private final ReconciliationRequestStore requests;
    private final ReconciliationAttachments attachments;
    ReturnedStockSubmit(ReconciliationContext contexts, CorrectionStore store, ReconciliationRequestStore requests,
        ReconciliationAttachments attachments) {
        this.contexts = contexts; this.store = store; this.requests = requests; this.attachments = attachments;
    }
    @Override public String commandType() { return "RECONCILE_RETURN_SUBMIT"; }
    @Override public void beforeLocks(CorrectionCommand.Input input) { contexts.beforeLocks(input); }
    @Override public JsonNode lockContext(CorrectionCommand.Input input, JsonNode anchor) {
        return contexts.returned(input.uuid("warehouseId"), null, input.uuid("caseId"), true);
    }
    @Override public JsonNode execute(CorrectionCommand.Write write) {
        ReconciliationPolicy.permission(write.actor()); var anchor = write.anchor();
        if (!"OPEN".equals(anchor.at("/case/status").asText())
            || !anchor.path("factsSha256").equals(anchor.at("/discoveredReceipt/businessDecision/factsSha256"))) throw CorrectionCommand.stale();
        if (!write.actor().path("principalId").equals(anchor.at("/discoveredReceipt/actor/principalId"))) throw CorrectionCommand.denied();
        ReturnedStockPolicy.eligible(anchor.path("facts"));
        var evidence = write.command().body().path("evidence");
        ReconciliationPolicy.returnedEvidence(evidence, write.recordedAt()); attachments.verify(evidence, write.actor());
        var id = write.command().uuid("caseId");
        if (store.advanceReconciliation(id, "OPEN", "REVIEW_READY", null, write.recordedAt())!=1) throw CorrectionCommand.stale();
        return SubmissionJson.object().put("caseId", id.toString()).put("status", "REVIEW_READY")
            .put("factsSha256", anchor.path("factsSha256").asText()).put("availability", "UNCHANGED");
    }
    @Override public void afterReceipt(CorrectionCommand.Write write, JsonNode decision) { requests.submitted(write); }
}
