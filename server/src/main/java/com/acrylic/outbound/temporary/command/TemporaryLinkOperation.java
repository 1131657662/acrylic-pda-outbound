package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.acrylic.outbound.temporary.review.TemporaryLinkTarget;
import com.acrylic.outbound.temporary.review.TemporaryDuplicateModels.Head;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/**
 * Controlled TEMP/formal linkage decision. It is the only place that can decide a link, it is never
 * constructed by production wiring, and it always requires an explicit manual confirmation bound to
 * the complete reviewed duplicate candidate set. No automatic rule links or merges anything.
 *
 * The verified decision and its pointer switch (mapping version, scope canonical, slot and superseded
 * PDA_TEMP order) share one transaction: either the link is applied and recorded, or nothing changes.
 */
public final class TemporaryLinkOperation implements CommandOperation {
    private final Dependencies dependencies;
    public TemporaryLinkOperation(Dependencies dependencies) { this.dependencies=dependencies; }
    public String commandType() { return "LINK"; }
    public boolean recordsDecision() { return true; }
    public boolean requiresCandidateContext() { return true; }
    public void apply(CommandContext context) { throw new IllegalStateException("Full command/actor required"); }
    public JsonNode execute(CommandWrite write) {
        var facts=write.context();
        if(facts.version("mappingVersion")<1)throw CommandErrors.stale();
        var source=dependencies.source().read(facts);
        if(source.snapshot().shortShipment())
            throw reject("TEMPORARY_LINK_SHORT_SHIPMENT_UNRESOLVED","原实绩存在短装，须先按短装审批与冲突分流处理，不能直接关联");
        var reviewed=lockedCandidates(write,source.body());
        var head=target(facts,reviewed);
        return decision(facts,source.body(),reviewed,head);
    }
    private TemporaryDecisionCandidates.Reviewed lockedCandidates(CommandWrite write,JsonNode body) {
        var facts=write.context();var warehouse=facts.id("warehouseId");var submission=facts.id("submissionId");
        var reviewed=dependencies.candidates().reviewed(warehouse,submission,body);
        var head=target(facts,reviewed);
        if(facts.version("mappingVersion")>1)
            dependencies.targets().lockAndVerifyHistory(warehouse,facts.id("scopeId"),head.id());
        dependencies.targets().lockAndVerify(warehouse,linkContext(body),head);
        var locked=dependencies.candidates().reviewed(warehouse,submission,body);
        if(!locked.snapshot().path("contextSha256").equals(write.command().expected().path("candidateContextSha256"))
            || !head.equals(target(facts,locked)))throw CommandErrors.stale();
        return locked;
    }
    private Head target(CommandContext facts,TemporaryDecisionCandidates.Reviewed reviewed) {
        long total=reviewed.snapshot().path("total").asLong();
        if(total==0)throw reject("TEMPORARY_LINK_TARGET_NOT_FOUND","没有可供人工核对的正式候选，不能关联");
        boolean first=facts.version("mappingVersion")==1;
        int required=first?1:2;
        if(total!=required || reviewed.heads().size()!=required)
            throw reject("TEMPORARY_LINK_DUPLICATES_UNRESOLVED","完整候选集合不满足受控关联条件，请逐单核对");
        if(first)return reviewed.heads().getFirst();
        String current=facts.text("canonicalOrderId");
        if(reviewed.heads().stream().noneMatch(head->head.id().equals(current)))
            throw reject("TEMPORARY_LINK_DUPLICATES_UNRESOLVED","现关联正式单不在完整候选集合中，不能重关联");
        return reviewed.heads().stream().filter(head->!head.id().equals(current)).findFirst().orElseThrow(CommandErrors::stale);
    }
    private JsonNode decision(CommandContext facts,JsonNode body,TemporaryDecisionCandidates.Reviewed reviewed,Head head) {
        var installed=dependencies.mappings().install(facts,head.id());
        var link=SubmissionJson.object().put("applyState","APPLIED").put("targetOrderId",head.id())
            .put("targetOrderNo",head.orderNo()).put("targetRevision",Integer.toString(head.revision()))
            .put("targetRowVersion",head.rowVersion())
            .putNull("sourceCanonicalOrderId").put("sourceMappingVersion",facts.text("mappingVersion"))
            .put("candidateContextSha256",reviewed.snapshot().path("contextSha256").asText())
            .put("mappingId",installed.mappingId()).put("mappingVersion",Long.toString(installed.mappingVersion()));
        if(facts.id("canonicalOrderId")!=null)link.put("sourceCanonicalOrderId",facts.id("canonicalOrderId").toString());
        var decision=SubmissionJson.object().put("kind","LINK").put("shortShipmentReason","")
            .putNull("shortApprovalReceiptId").putNull("shortApprovalSha256");
        decision.set("products",TemporaryDecisionCandidates.products(body));
        decision.set("candidates",reviewed.snapshot());decision.set("submissionReceipt",link);return decision;
    }
    private JsonNode linkContext(JsonNode body) {
        var context=SubmissionJson.object().put("vehicleNo",body.at("/temporaryOrder/vehicleNo").asText())
            .put("inventorySnapshotId",body.path("inventorySnapshotId").asText());
        context.set("products",TemporaryDecisionCandidates.products(body));return context;
    }
    private static BusinessException reject(String code,String message) { return new BusinessException(409,code,message); }
    public record Dependencies(TemporaryConfirmationSource source,TemporaryDecisionCandidates candidates,
        TemporaryLinkTarget targets,TemporaryLinkMapping mappings) { }
}
