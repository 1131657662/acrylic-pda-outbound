package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Approval records exact shortages; stock and physical blockers remain separate prerequisites. */
final class RelayAssociationPlan {
    private RelayAssociationPlan() { }
    static void validate(JsonNode request) {
        if(!request.has("shortfallApproval"))return;
        var approval=request.path("shortfallApproval");RelayPhysicalPolicy.exact(approval,"originalShortfall targetShortfall reason");
        count(approval,"originalShortfall");count(approval,"targetShortfall");
        if(!approval.path("reason").isTextual() || text(approval,"reason").isBlank() || text(approval,"reason").length()>500)
            throw RelayAssociationPolicy.invalid("短装批准须填写不超过500字的说明");
    }
    static void complete(JsonNode request,JsonNode target,JsonNode context) {
        completeGroup(request,target,List.of(context));
    }
    static void completeGroup(JsonNode request,JsonNode target,List<JsonNode> contexts) {
        var actual=new HashMap<String,Long>();
        for(var context:contexts)for(var row:context.path("inventory")) {
            actual.merge(productKey(row.path("original")),1L,Long::sum);
            if(!text(target,"inventorySnapshotId").equals(text(row.path("server"),"snapshotId")))
                throw RelayAssociationPolicy.blocked("目标库存快照不符");
        }
        var planned=new HashMap<String,Long>();long total=0;
        for(var row:target.path("products")) {
            long quantity=count(row,"plannedCount");
            if(quantity==0 || planned.put(productKey(row),quantity)!=null)throw RelayAssociationPolicy.blocked("目标产品重复或计划无效");
            total=Math.addExact(total,quantity);
        }
        if(total!=count(target,"plannedTotal"))throw RelayAssociationPolicy.blocked("目标整单计划与产品计划不符");
        for(var row:actual.entrySet())if(row.getValue()>planned.getOrDefault(row.getKey(),0L))
            throw RelayAssociationPolicy.blocked("原有效包包含异产品或超过目标产品计划，不能以短装批准放行");
        long original=contexts.stream().mapToLong(RelayAssociationPlan::originalShortfall).sum();
        long active=contexts.stream().mapToLong(context->context.path("inventory").size()).sum();
        if(original>100000 || active>100000)throw RelayAssociationPolicy.blocked("合计数量超过资源上限");
        approve(request,original,total-active);
    }
    private static long originalShortfall(JsonNode context) {
        var closure=context.path("original").path("closure");long active=count(closure,"active");
        long planned=count(closure,"plannedTotal"),shortfall=count(closure,"shortfall");
        if(active!=context.path("inventory").size() || planned-active!=shortfall ||
            shortfall>0 && (!closure.path("shortfallReason").isTextual() || text(closure,"shortfallReason").isBlank()))
            throw RelayAssociationPolicy.blocked("原结束计划、有效包或短装原因不自洽");
        return shortfall;
    }
    private static void approve(JsonNode request,long original,long target) {
        if(original==0 && target==0) {
            if(request.has("shortfallApproval"))throw RelayAssociationPolicy.invalid("无短装不得附加无关批准");return;
        }
        if(!request.has("shortfallApproval"))throw RelayAssociationPolicy.blocked("原任务或目标有短装，须独立主管显式批准精确差额");
        var approval=request.path("shortfallApproval");
        if(count(approval,"originalShortfall")!=original || count(approval,"targetShortfall")!=target)
            throw RelayAssociationPolicy.changed();
    }
    private static long count(JsonNode row,String field) {
        String value=text(row,field);
        if(!row.path(field).isTextual() || !value.matches("0|[1-9][0-9]{0,5}"))throw RelayAssociationPolicy.invalid("包数须为精确非负整数字符串");
        long result=Long.parseLong(value);if(result>100000)throw RelayAssociationPolicy.invalid("包数超过资源上限");return result;
    }
    private static String productKey(JsonNode row) {
        return canonical(object().put("deliveryCode",text(row,"deliveryCode")).put("model",text(row,"model")).put("name",text(row,"name")));
    }
}
