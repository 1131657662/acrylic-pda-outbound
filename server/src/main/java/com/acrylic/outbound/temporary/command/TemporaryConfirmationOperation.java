package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Deliberately no @Component or @Bean: isolated tests must explicitly install either operation. */
public final class TemporaryConfirmationOperation implements CommandOperation {
    private final String kind; private final Dependencies dependencies;
    public TemporaryConfirmationOperation(String kind,Dependencies dependencies) {
        if(!java.util.Set.of("NORMAL_CONFIRM","SHORT_APPROVE").contains(kind))throw new IllegalArgumentException();
        this.kind=kind;this.dependencies=dependencies;
    }
    public String commandType() { return kind; }
    public boolean recordsDecision() { return true; }
    public boolean requiresCandidateContext() { return true; }
    public void apply(CommandContext context) { throw new IllegalStateException("Full command/actor required"); }
    public JsonNode execute(CommandWrite write) {
        var source=dependencies.source().read(write.context());var h=source.head();
        var candidates=dependencies.candidates().snapshot(h.warehouse(),h.control().id(),source.body());
        if(!candidates.get("contextSha256").equals(write.command().expected().get("candidateContextSha256")))throw CommandErrors.stale();
        var decision=SubmissionJson.object().put("kind",kind).put("shortShipmentReason",source.body().path("shortShipmentReason").asText(""))
            .putNull("shortApprovalReceiptId").putNull("shortApprovalSha256").putNull("submissionReceipt");
        decision.set("products",TemporaryDecisionCandidates.products(source.body()));decision.set("candidates",candidates);
        var actor=UUID.fromString(write.actor().path("principalId").asText());
        if(kind.equals("SHORT_APPROVE")) {
            if(!source.snapshot().shortShipment())throw new BusinessException(409,"SHORT_SHIPMENT_NOT_PRESENT","原实绩没有短装");
            if(actor.equals(h.actor()))throw new BusinessException(403,"SHORT_SHIPMENT_SELF_APPROVAL_FORBIDDEN","短装须由另一有权主管批准");
            return decision;
        }
        if(!candidates.path("total").asText().equals("0"))
            throw new BusinessException(409,"TEMPORARY_DUPLICATES_UNRESOLVED","完整候选集合尚待独立核对，不能确认");
        if(source.snapshot().shortShipment())dependencies.approvals().bind(write,decision);
        var issues=dependencies.stock().reserve(h,source.snapshot());
        if(!issues.isEmpty())throw new BusinessException(409,"TEMPORARY_STOCK_CONFLICT","库存或原包周期冲突，不能确认");
        decision.set("submissionReceipt",dependencies.accounting().commit(h,source.body(),source.snapshot(),actor));return decision;
    }
    public record Dependencies(TemporaryConfirmationSource source,TemporaryDecisionCandidates candidates,
        TemporaryShortApprovals approvals,SubmissionStock stock,SubmissionAccounting accounting) { }
}
