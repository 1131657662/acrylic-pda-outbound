package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.acrylic.outbound.temporary.review.TemporaryLinkTarget;
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
        var facts=write.context();var command=write.command();
        var source=dependencies.source().read(facts);
        if(source.snapshot().shortShipment())
            throw reject("TEMPORARY_LINK_SHORT_SHIPMENT_UNRESOLVED","原实绩存在短装，须先按短装审批与冲突分流处理，不能直接关联");
        var warehouse=facts.id("warehouseId");var submission=facts.id("submissionId");
        var context=linkContext(source.body());
        var reviewed=dependencies.candidates().reviewed(warehouse,submission,source.body());
        if(!reviewed.snapshot().path("total").asText().equals("1"))
            throw reviewed.snapshot().path("total").asText().equals("0")
                ? reject("TEMPORARY_LINK_TARGET_NOT_FOUND","没有可供人工核对的正式候选，不能关联")
                : reject("TEMPORARY_LINK_DUPLICATES_UNRESOLVED","完整候选集合存在多条疑似重复正式单，必须逐单人工核对后才能关联");
        var head=reviewed.heads().getFirst();
        dependencies.targets().lockAndVerify(warehouse,context,head);
        // Recompute the reviewed set with the target row locked, so a change between review and write cannot pass.
        reviewed=dependencies.candidates().reviewed(warehouse,submission,source.body());
        if(!reviewed.snapshot().path("contextSha256").equals(command.expected().path("candidateContextSha256"))
            || !reviewed.snapshot().path("total").asText().equals("1") || !reviewed.heads().getFirst().id().equals(head.id()))
            throw CommandErrors.stale();
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
        decision.set("products",TemporaryDecisionCandidates.products(source.body()));
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
