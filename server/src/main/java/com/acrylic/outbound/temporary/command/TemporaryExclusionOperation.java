package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;

/** Installed only by the existing explicit development command assembly. */
public final class TemporaryExclusionOperation implements CommandOperation {
    private final TemporaryConfirmationSource source;private final TemporaryDecisionCandidates candidates;
    private final TemporaryExclusionFacts exclusions;
    public TemporaryExclusionOperation(TemporaryConfirmationSource source,TemporaryDecisionCandidates candidates,TemporaryExclusionFacts exclusions) {
        this.source=source;this.candidates=candidates;this.exclusions=exclusions;
    }
    public String commandType() { return "DUPLICATE_EXCLUDE"; }
    public boolean recordsDecision() { return true; }
    public boolean requiresCandidateContext() { return true; }
    public void apply(CommandContext context) { throw new IllegalStateException("Full command/actor required"); }
    public JsonNode execute(CommandWrite write) {
        var facts=write.context();
        if(write.actor().path("principalId").asText().equals(facts.text("originalOperatorId")))
            throw new BusinessException(403,"TEMPORARY_EXCLUSION_SELF_APPROVAL_FORBIDDEN","候选排除须由另一有权主管逐项核对");
        var body=source.read(facts).body();
        var before=candidates.snapshot(facts.id("warehouseId"),facts.id("submissionId"),body);
        if(!before.path("contextSha256").equals(write.command().expected().path("candidateContextSha256")))throw CommandErrors.stale();
        var exclusion=exclusions.read(facts,body,write.command().body().path("exclusion"));
        var after=candidates.snapshot(facts.id("warehouseId"),facts.id("submissionId"),body);
        if(!before.equals(after))throw CommandErrors.stale();
        var decision=SubmissionJson.object().put("kind",commandType()).put("shortShipmentReason",body.path("shortShipmentReason").asText(""))
            .putNull("shortApprovalReceiptId").putNull("shortApprovalSha256").putNull("submissionReceipt");
        decision.set("products",TemporaryDecisionCandidates.products(body));decision.set("candidates",after);
        decision.set("exclusion",exclusion);return decision;
    }
}
