package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Deliberately no @Component or @Bean: isolated tests must explicitly install either operation. */
public final class TemporaryConfirmationOperation implements CommandOperation {
    private final String kind; private final Dependencies dependencies;
    private final TemporaryCandidateSelection selections;
    public TemporaryConfirmationOperation(String kind,Dependencies dependencies) {
        this(kind,dependencies,null);
    }
    public TemporaryConfirmationOperation(String kind,Dependencies dependencies,TemporaryCandidateSelection selections) {
        if(!java.util.Set.of("NORMAL_CONFIRM","SHORT_APPROVE").contains(kind))throw new IllegalArgumentException();
        this.kind=kind;this.dependencies=dependencies;this.selections=selections;
    }
    public String commandType() { return kind; }
    public boolean recordsDecision() { return true; }
    public boolean requiresCandidateContext() { return true; }
    public void apply(CommandContext context) { throw new IllegalStateException("Full command/actor required"); }
    public JsonNode execute(CommandWrite write) {
        var source=dependencies.source().read(write.context());var h=source.head();
        var selection=selections==null?null:selections.read(write.context(),source.body());
        var candidates=selection==null?dependencies.candidates().snapshot(h.warehouse(),h.control().id(),source.body()):selection.reviewed().snapshot();
        if(!candidates.get("contextSha256").equals(write.command().expected().get("candidateContextSha256")))throw CommandErrors.stale();
        var decision=SubmissionJson.object().put("kind",kind).put("shortShipmentReason",source.body().path("shortShipmentReason").asText(""))
            .putNull("shortApprovalReceiptId").putNull("shortApprovalSha256").putNull("submissionReceipt");
        decision.set("products",TemporaryDecisionCandidates.products(source.body()));decision.set("candidates",candidates);
        if(selection!=null)selection.capture(decision);
        var actor=UUID.fromString(write.actor().path("principalId").asText());
        if(kind.equals("SHORT_APPROVE")) {
            requireApproval(source,actor);
            return decision;
        }
        requireCandidates(source,selection,candidates);
        if(source.snapshot().shortShipment())dependencies.approvals().bind(write,decision);
        var issues=dependencies.stock().reserve(h,source.snapshot());
        if(!issues.isEmpty())throw new BusinessException(409,"TEMPORARY_STOCK_CONFLICT","库存或原包周期冲突，不能确认");
        var receipt=dependencies.accounting().commit(h,source.body(),source.snapshot(),actor);
        dependencies.rounds().complete(write,source,receipt);decision.set("submissionReceipt",receipt);return decision;
    }
    private void requireApproval(TemporaryConfirmationSource.Value source,UUID actor) {
        if(!source.snapshot().shortShipment())throw new BusinessException(409,"SHORT_SHIPMENT_NOT_PRESENT","原实绩没有短装");
        if(actor.equals(source.head().actor()))throw new BusinessException(403,"SHORT_SHIPMENT_SELF_APPROVAL_FORBIDDEN","短装须由另一有权主管批准");
    }
    private void requireCandidates(TemporaryConfirmationSource.Value source,TemporaryCandidateSelection.Selection selection,JsonNode candidates) {
        var h=source.head();
        if(source.linked()) {
            // After a controlled link the human already reviewed exactly one formal target, so that candidate
            // is the bound target rather than a suspicion; a second matching order still blocks the confirm.
            var reviewed=selection==null?dependencies.candidates().reviewed(h.warehouse(),h.control().id(),source.body()):selection.reviewed();
            long total=selection==null?Long.parseLong(reviewed.snapshot().path("total").asText()):selection.total();
            boolean singleTarget=reviewed.heads().size()==1 && reviewed.heads().getFirst().id().equals(h.order().toString());
            if(total!=1 || !singleTarget)
                throw new BusinessException(409,"TEMPORARY_DUPLICATES_UNRESOLVED",total==0
                    ?"已关联目标不在当前候选集合中，不能确认"
                    :"完整候选集合存在多条疑似重复正式单，必须逐单人工核对后才能确认");
        } else if((selection==null?candidates.path("total").asLong():selection.total())!=0) {
            throw new BusinessException(409,"TEMPORARY_DUPLICATES_UNRESOLVED","完整候选集合尚待独立核对，不能确认");
        }
    }
    public record Dependencies(TemporaryConfirmationSource source,TemporaryDecisionCandidates candidates,
        TemporaryShortApprovals approvals,SubmissionStock stock,SubmissionAccounting accounting,
        com.acrylic.outbound.temporary.continuation.TemporaryRoundCompletion rounds) { }
}
