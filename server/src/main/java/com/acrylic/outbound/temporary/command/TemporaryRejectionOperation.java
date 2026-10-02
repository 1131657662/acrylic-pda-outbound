package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionEvents;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Records a TEMP rejection while deliberately retaining every source reservation for later disposition. */
public final class TemporaryRejectionOperation implements CommandOperation {
    private final Dependencies dependencies;

    public TemporaryRejectionOperation(Dependencies dependencies) { this.dependencies = dependencies; }

    @Override public String commandType() { return "REJECT"; }
    @Override public boolean recordsDecision() { return true; }
    @Override public boolean requiresCandidateContext() { return true; }
    @Override public void apply(CommandContext context) { throw new IllegalStateException("Full command/actor required"); }

    @Override public JsonNode execute(CommandWrite write) {
        var source = dependencies.source().read(write.context());
        var candidates = dependencies.candidates().snapshot(write.context().id("warehouseId"),
            write.context().id("submissionId"), source.body());
        if (!candidates.path("contextSha256").equals(write.command().expected().path("candidateContextSha256")))
            throw CommandErrors.stale();
        UUID actor = UUID.fromString(write.actor().path("principalId").asText());
        if (actor.equals(source.head().actor()))
            throw new com.acrylic.outbound.common.BusinessException(403,
                "TEMPORARY_REJECTION_SELF_FORBIDDEN", "原提交人员不能自行拒绝临时单");
        var issues = SubmissionJson.object().putArray("issues");
        issues.add(SubmissionJson.object().put("code", "SUPERVISOR_REJECTED")
            .put("reason", write.command().text("reason")));
        dependencies.submissions().state(source.head().control().id(), "REJECTED", issues, null);
        // The order remains REVIEW_REQUIRED so a later, separately evidenced disposition or continuation can act.
        dependencies.events().transition(source.head(), "REVIEW_REQUIRED", actor);
        var decision = SubmissionJson.object().put("kind", "REJECT")
            .put("shortShipmentReason", source.body().path("shortShipmentReason").asText(""))
            .putNull("shortApprovalReceiptId").putNull("shortApprovalSha256").putNull("submissionReceipt");
        decision.set("products", TemporaryDecisionCandidates.products(source.body()));
        decision.set("candidates", candidates);
        return decision;
    }

    public record Dependencies(TemporaryConfirmationSource source, TemporaryDecisionCandidates candidates,
        SubmissionStore submissions, SubmissionEvents events) { }
}
