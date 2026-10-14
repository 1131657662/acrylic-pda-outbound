package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Explicit evidence linkage never manufactures a formal submission or stock capability. */
final class RelayAssociationPolicy {
    private RelayAssociationPolicy() { }
    static void validate(JsonNode body) {
        String fields="contextSha256 physicalCheckId targetOrderId candidateExclusions reason";
        if(body.has("conflictDecisionId"))fields+=" conflictDecisionId";
        if(body.has("shortfallApproval"))fields+=" shortfallApproval";
        if(body.has("additionalTasks"))fields+=" additionalTasks";
        RelayPhysicalPolicy.exact(body,fields);RelayAssociationPlan.validate(body);
        if(body.has("conflictDecisionId"))RelayPhysicalPolicy.identifier(body,"conflictDecisionId");
        if(!text(body,"contextSha256").matches("[0-9a-f]{64}"))throw invalid("核对摘要无效");
        RelayPhysicalPolicy.identifier(body,"physicalCheckId");RelayPhysicalPolicy.identifier(body,"targetOrderId");reason(body);
        var exclusions=body.path("candidateExclusions");var seen=new HashSet<UUID>();
        if(!exclusions.isArray() || exclusions.size()>2000)throw invalid("排除集合无效");
        for(var row:exclusions) {
            RelayPhysicalPolicy.exact(row,"orderId reason");reason(row);
            if(!seen.add(RelayPhysicalPolicy.identifier(row,"orderId")))throw invalid("候选重复排除");
        }
        RelayAssociationGroupPolicy.validate(body);
    }
    static JsonNode complete(JsonNode body,JsonNode context,JsonNode physical,UUID actor,Instant now) {
        return complete(body,context,physical,actor,now,null);
    }
    static JsonNode complete(JsonNode body,JsonNode context,JsonNode physical,UUID actor,Instant now,JsonNode conflict) {
        var target=member(body,context,physical,actor,now,conflict);
        RelayAssociationPlan.complete(body,target,context);return target;
    }
    static JsonNode member(JsonNode body,JsonNode context,JsonNode physical,UUID actor,Instant now,JsonNode conflict) {
        if(!sha(context).equals(text(body,"contextSha256")))throw changed();
        if(text(context.path("original"),"operatorId").equals(actor.toString()))
            throw new BusinessException(403,"INDEPENDENT_ASSOCIATION_SELF_REVIEW","原采集者不得独自关联本任务");
        RelayPhysicalPolicy.complete(physical,context,actor,now);
        for(var row:physical.path("packages"))if(!text(row,"status").equals("LOADED"))throw blocked("实物异常须先专门核对");
        if(context.path("inventory").isEmpty())throw blocked("空任务不得关联正式单");
        for(var row:context.path("inventory"))if(!row.path("blockers").isEmpty())throw blocked("库存或占用尚有阻断");
        RelayAssociationConflictPolicy.complete(context,conflict,actor,now);
        return target(body,context);
    }
    private static JsonNode target(JsonNode body,JsonNode context) {
        String id=text(body,"targetOrderId");JsonNode target=null;var expected=new HashSet<String>();
        for(var candidate:context.path("candidates").path("items")) {
            if(text(candidate,"id").equals(id))target=candidate;else expected.add(text(candidate,"id"));
        }
        var actual=new HashSet<String>();body.path("candidateExclusions").forEach(row->actual.add(text(row,"orderId")));
        if(!actual.equals(expected))throw invalid("须恰好逐项排除其余完整候选");
        if(target==null || !text(target,"source").equals("WEB") || !Set.of("DRAFT","READY").contains(text(target,"status"))
            || !target.path("blockers").isEmpty() || !target.path("workFacts").isEmpty())throw blocked("目标不是无既有作业的正式单");
        if(!text(target,"vehicleNo").equals(text(context.path("original").path("task"),"vehicleNo")))throw blocked("目标车号与原任务不符");
        return target;
    }
    private static void reason(JsonNode row) {
        if(!row.path("reason").isTextual() || text(row,"reason").isBlank() || text(row,"reason").length()>500)throw invalid("须填写不超过500字的关联或排除依据");
    }
    static BusinessException invalid(String detail) { return new BusinessException(400,"INVALID_INDEPENDENT_ASSOCIATION",detail); }
    static BusinessException blocked(String detail) { return new BusinessException(409,"INDEPENDENT_ASSOCIATION_BLOCKED",detail); }
    static BusinessException changed() { return new BusinessException(409,"INDEPENDENT_REVIEW_CONTEXT_CHANGED","完整依据已变化，请重新核对"); }
}
