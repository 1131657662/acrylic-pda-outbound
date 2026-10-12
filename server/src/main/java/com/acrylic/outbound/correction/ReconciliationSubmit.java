package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;

/** Original discoverer freezes the observed physical evidence and unchanged four-part context. */
final class ReconciliationSubmit implements CorrectionCommand.Operation {
    private final ReconciliationContext contexts;
    private final CorrectionStore store;
    private final ReconciliationRequestStore requests;
    private final ReconciliationAttachments attachments;
    ReconciliationSubmit(ReconciliationContext contexts, CorrectionStore store, ReconciliationRequestStore requests,
        ReconciliationAttachments attachments) {
        this.contexts = contexts; this.store = store; this.requests = requests; this.attachments = attachments;
    }
    @Override public String commandType() { return "RECONCILE_SUBMIT"; }
    @Override public void beforeLocks(CorrectionCommand.Input input) { contexts.beforeLocks(input); }
    @Override public JsonNode lockContext(CorrectionCommand.Input input, JsonNode anchor) {
        return contexts.read(input.uuid("warehouseId"), null, input.uuid("caseId"), true);
    }

    @Override public JsonNode execute(CorrectionCommand.Write write) {
        ReconciliationPolicy.permission(write.actor()); var anchor = write.anchor();
        if (!"OPEN".equals(anchor.at("/case/status").asText())) throw CorrectionCommand.stale();
        if (!anchor.path("factsSha256").equals(anchor.at("/discoveredReceipt/businessDecision/factsSha256"))) throw CorrectionCommand.stale();
        if (!write.actor().path("principalId").equals(anchor.at("/discoveredReceipt/actor/principalId"))) throw CorrectionCommand.denied();
        ReconciliationPolicy.missingPosting(anchor.path("facts"));
        ReconciliationPolicy.text(write.command().body().path("reason"), 500);
        var evidence = write.command().body().path("evidence"); ReconciliationPolicy.evidence(evidence, write.recordedAt());
        attachments.verify(evidence, write.actor());
        var id = write.command().uuid("caseId");
        if (store.advanceReconciliation(id, "OPEN", "REVIEW_READY", null, write.recordedAt()) != 1) throw CorrectionCommand.stale();
        return SubmissionJson.object().put("caseId", id.toString()).put("status", "REVIEW_READY")
            .put("factsSha256", anchor.path("factsSha256").asText()).put("availability", "UNCHANGED");
    }

    @Override public void afterReceipt(CorrectionCommand.Write write, JsonNode decision) { requests.submitted(write); }
}
