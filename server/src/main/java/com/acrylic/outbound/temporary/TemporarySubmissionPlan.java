package com.acrylic.outbound.temporary;

import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class TemporarySubmissionPlan {
    private final TemporaryStore store;
    public TemporarySubmissionPlan(TemporaryStore store) { this.store=store; }
    public Plan validate(SubmissionStore.Head head,JsonNode body) {
        var a=store.get(head.control().temporary().authorization());var manifest=parse(a.receipt().manifest());
        var plan=body.path("temporaryOrder");require(plan.isObject() && plan.size()==8,"临时计划字段不完整");
        equal(text(plan,"clientOrderId"),head.control().temporary().client().toString(),"计划原临时单不符");
        equal(text(plan,"creatorDeviceId"),a.deviceId().toString(),"计划原设备不符");equal(text(plan,"authorizationId"),a.id().toString(),"计划原资格不符");
        long revision=number(plan,"revision");require(revision>0 && revision<=Integer.MAX_VALUE,"临时计划版本无效");equal(number(body,"orderRevision"),revision,"冻结计划版本不符");
        bounded(plan,"vehicleNo",50,true);bounded(plan,"containerNo",50,false);bounded(plan,"reason",500,true);bounded(body,"temporaryNo",64,true);
        equal(sha(plan),text(body,"planSha256"),"临时计划摘要不符");
        equal(text(body,"inventorySnapshotId"),text(manifest,"inventorySnapshotId"),"原库存不符");
        equal(text(body,"policyVersion"),text(manifest,"policyVersion"),"原政策版本不符");
        var policy=part(a.id(),"policy");require(body.path("autoDeductInventory").isBoolean(),"扣账模式无效");
        equal(body.get("autoDeductInventory"),policy.get("autoDeductInventory"),"固定扣账模式不符");
        var mappings=new HashMap<String,JsonNode>();part(a.id(),"mapping").forEach(m->mappings.put(text(m,"deliveryCode"),m));
        var lines=lines(plan,mappings);return new Plan(a,manifest,plan,lines);
    }
    private List<Line> lines(JsonNode plan,Map<String,JsonNode> mapping) {
        var result=new ArrayList<Line>();String previous="";long total=0;
        require(array(plan,"lines").size()<=500,"临时计划最多500项产品");
        for(var row:array(plan,"lines")) {
            String code=text(row,"deliveryCode");require(code.matches("[0-9]{3}") && code.compareTo(previous)>0,"产品必须按代码排序且不重复");previous=code;
            long count=number(row,"plannedCount");require(count>0 && count<=Integer.MAX_VALUE,"产品计划须为正整数");total=Math.addExact(total,count);
            var m=mapping.get(code);require(m!=null && m.path("enabled").asBoolean(),"产品不在原启用映射内");
            require(row.size()==5,"产品计划字段无效");
            for(String field:List.of("productId","model","name"))equal(row.get(field),m.get(field),"原产品快照不符");
            result.add(new Line(code,count,m));
        }
        require(!result.isEmpty(),"临时单须有产品计划");return result;
    }
    public JsonNode part(UUID id,String name) {
        try(var gzip=new GZIPInputStream(new ByteArrayInputStream(store.content(id,name)))) {
            byte[] bytes=gzip.readNBytes(33554433);require(bytes.length<=33554432,"原资料超出资源限额");return parse(bytes);
        } catch(IOException error) { throw new IllegalStateException("原临时准备资料损坏",error); }
    }
    private static void bounded(JsonNode row,String field,int max,boolean required) {
        String value=text(row,field);require(value.length()<=max && (!required || !value.isBlank()) && value.equals(value.strip()) && value.chars().noneMatch(Character::isISOControl),"临时计划文本无效："+field);
    }
    public record Line(String code,long count,JsonNode mapping) { }
    public record Plan(TemporaryStore.Stored authorization,JsonNode manifest,JsonNode body,List<Line> lines) { }
}
