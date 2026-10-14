package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** A disposition preserves both signed originals. It confers no stock or business capability. */
final class RelayConflictPolicy {
    private RelayConflictPolicy() { }
    static void validate(JsonNode body) {
        var fields=new HashSet<>(List.of("contextSha256","physicalCheckId","peerChecks","reason"));
        if(body.has("predecessorDecisionId")) { fields.add("predecessorDecisionId");RelayPhysicalPolicy.identifier(body,"predecessorDecisionId"); }
        RelayPhysicalPolicy.exact(body,String.join(" ",fields));
        if(!text(body,"contextSha256").matches("[0-9a-f]{64}"))throw invalid("核对摘要无效");
        RelayPhysicalPolicy.identifier(body,"physicalCheckId");reason(body);
        var rows=body.path("peerChecks");var seen=new HashSet<UUID>();
        if(!rows.isArray() || rows.isEmpty() || rows.size()>199)throw invalid("须逐项引用全部冲突任务实物回执");
        for(var row:rows) {
            RelayPhysicalPolicy.exact(row,"relayId physicalCheckId");RelayPhysicalPolicy.identifier(row,"physicalCheckId");
            if(!seen.add(RelayPhysicalPolicy.identifier(row,"relayId")))throw invalid("冲突原件重复");
        }
    }
    static List<JsonNode> peers(JsonNode body,JsonNode context) {
        if(!sha(context).equals(text(body,"contextSha256")))throw changed();
        var result=new ArrayList<JsonNode>();
        for(var peer:context.path("peers")) {
            if(peer.path("closureDivergence").asBoolean())throw blocked("结束分歧须单独核对，不得选择性忽略原件");
            if(!peer.path("crossTaskConflict").asBoolean())continue;
            for(var original:peer.path("originals"))if(!text(original,"sourceTrust").equals("TRUSTED") || !original.path("completeBoundaryVerified").asBoolean())
                throw blocked("冲突各方原件必须可信且完整结束");
            result.add(peer);
        }
        if(result.isEmpty() || result.size()!=body.path("peerChecks").size())throw invalid("引用须恰好覆盖全部重叠任务");
        return List.copyOf(result);
    }
    static JsonNode peerFor(JsonNode reference,List<JsonNode> peers) {
        for(var peer:peers)for(var original:peer.path("originals"))
            if(text(original,"relayId").equals(text(reference,"relayId")))return peer;
        throw invalid("引用原件不是完整冲突集合成员");
    }
    record Reviewer(UUID actor,Instant now) { }
    static void check(JsonNode context,JsonNode physical,Reviewer reviewer) {
        RelayPhysicalPolicy.complete(physical.path("evidence"),context,reviewer.actor(),reviewer.now());
        var r=physical.path("receipt");var original=context.path("original");
        if(!text(r,"relayId").equals(text(original,"relayId")) || !text(r,"warehouseId").equals(text(original,"warehouseId"))
            || !physical.path("originalContext").equals(context))throw changed();
    }
    static void peerIdentity(JsonNode context,JsonNode group) {
        var original=context.path("original");
        if(!text(original,"deviceId").equals(text(group,"deviceId")) || !text(original,"taskId").equals(text(group,"taskId")))throw changed();
    }
    static List<ObjectNode> claims(JsonNode selected,JsonNode physical,Map<String,JsonNode> others) {
        var barcodes=new TreeSet<String>();others.values().forEach(peer->peer.path("group").path("overlappingBarcodes").forEach(code->barcodes.add(code.asText())));
        var claims=new ArrayList<ObjectNode>();
        for(String barcode:barcodes) {
            var scan=stock(selected,barcode);observation(scan,physical,"LOADED");
            if(!scan.path("blockers").isEmpty() || scan.path("server").isNull())throw blocked("选定包仍有库存/占用阻断");
            for(var peer:others.values())if(contains(peer.path("group").path("overlappingBarcodes"),barcode))
                observation(stock(peer.path("context"),barcode),peer.path("physical"),"UNLOADED");
            var stock=scan.path("server");claims.add(object().put("barcode",barcode).put("baleId",text(stock,"baleId"))
                .put("dispatchCycle",text(stock,"dispatchCycle")));
        }
        if(claims.isEmpty())throw invalid("没有可决定的重叠包");return List.copyOf(claims);
    }
    private static boolean contains(JsonNode values,String barcode) {
        for(var value:values)if(value.asText().equals(barcode))return true;return false;
    }
    private static JsonNode stock(JsonNode context,String barcode) {
        for(var row:context.path("inventory"))if(text(row,"barcode").equals(barcode))return row;
        throw changed();
    }
    private static void observation(JsonNode stock,JsonNode physical,String expected) {
        for(var row:physical.path("evidence").path("packages"))if(text(row,"captureId").equals(text(stock,"captureId"))) {
            if(!text(row,"status").equals(expected) || expected.equals("UNLOADED") && text(row,"note").isBlank())
                throw blocked("重码须选定方已装载、其余各方已卸下并说明，不能凭未找到处理");return;
        }
        throw invalid("实物检查缺少原有效包");
    }
    private static void reason(JsonNode row) {
        if(!row.path("reason").isTextual() || text(row,"reason").isBlank() || text(row,"reason").length()>500)throw invalid("须填写不超过500字的决定说明");
    }
    static BusinessException invalid(String detail) { return new BusinessException(400,"INVALID_INDEPENDENT_CONFLICT",detail); }
    static BusinessException blocked(String detail) { return new BusinessException(409,"INDEPENDENT_CONFLICT_BLOCKED",detail); }
    static BusinessException changed() { return new BusinessException(409,"INDEPENDENT_REVIEW_CONTEXT_CHANGED","各方完整依据已变化，请重新核对"); }
}
