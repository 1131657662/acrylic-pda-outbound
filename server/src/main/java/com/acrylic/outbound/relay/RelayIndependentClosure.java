package com.acrylic.outbound.relay;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.inventory.ImportFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** A device closure proves a complete collection prefix, never physical disposition or business success. */
public final class RelayIndependentClosure {
    private static final String PURPOSE="INDEPENDENT_CAPTURE_CLOSED_V1",EVENT="INDEPENDENT_CLOSED";
    private RelayIndependentClosure() { }
    public static JsonNode read(Map<String,byte[]> files,JsonNode source,JsonNode order,String publicKey) {
        if(source==null)return null;
        var rows=new Rows(records(files,"captures"),records(files,"scans"),records(files,"events"),records(files,"lines"),records(files,"independent-voids"));
        var endings=rows.events().stream().filter(row->text(row,"type").equals(EVENT)).toList();
        if(!text(order,"state").equals("SEALED")) { require(endings.isEmpty(),"未封存任务不能携带结束边界");return null; }
        require(endings.size()==1 && rows.events().getLast()==endings.getFirst(),"结束必须为唯一末事件");
        var end=endings.getFirst();var envelope=parse(text(end,"detail"));
        equal(fields(envelope),Set.of("purpose","body","bodySha256","devicePublicKey","signature"),"结束公开字段不符");
        equal(text(envelope,"purpose"),PURPOSE,"结束用途不符");equal(text(envelope,"devicePublicKey"),publicKey,"结束与原封包设备钥不符");
        String raw=text(envelope,"body");var body=parse(raw);var prefix=rows.events().subList(0,rows.events().size()-1);
        sequence(rows.captures(),"sequence");sequence(rows.events(),"sequence");scope(rows,source);terminal(rows);
        equal(raw,canonical(expected(source,rows,end,prefix,text(body,"shortfallReason"))),"完整结束原事实不符");
        equal(DeviceCrypto.sha(raw),text(envelope,"bodySha256"),"结束正文摘要不符");
        DeviceCrypto.verify(publicKey,PURPOSE+"\n"+raw,text(envelope,"signature"));
        RelayIndependentCollection.verify(rows,source,files.get("independent-sku.bin"));
        var state=parse(files.get("independent-state.json"));
        equal(text(state,"state"),"SEALED","原结束状态不符");equal(text(state,"taskId"),text(source.path("task"),"id"),"原结束任务不符");
        equal(number(state,"nextScanSeq"),(long)rows.captures().size()+1,"输入水位不符");
        equal(number(state,"nextEventSeq"),(long)rows.events().size()+1,"审计水位不符");
        equal(text(state,"actualCount"),text(body,"active"),"原有效汇总不符");equal(text(state,"weightGrams"),text(body,"weightGrams"),"原重量汇总不符");
        return body;
    }
    private static JsonNode expected(JsonNode source,Rows rows,JsonNode end,List<JsonNode> prefix,String reason) {
        var task=source.path("task");var credential=source.path("credential");var active=rows.scans().stream().filter(row->text(row,"status").equals("ACTIVE")).toList();
        long shortfall=number(task,"plannedTotal")-active.size();
        require(shortfall>=0 && (shortfall==0 || !reason.isBlank()) && reason.length()<=500,"短装结束原因无效");
        var facts=object();facts.set("source",source);facts.set("captures",jsonArray(rows.captures()));facts.set("scans",jsonArray(rows.scans()));
        facts.set("lines",jsonArray(rows.lines()));facts.set("events",jsonArray(prefix));facts.set("voids",jsonArray(rows.voids()));
        var result=object().put("purpose",PURPOSE).put("taskId",text(task,"id"));
        for(String field:List.of("operatorId","deviceId","warehouseId","skuSha256","commandSha256"))result.put(field,text(task,field));
        for(String field:List.of("credentialId","credentialSha256"))result.put(field,text(credential,field));
        result.put("recoveryEpoch",text(task,"recoveryEpoch")).put("inputHighWater",Integer.toString(rows.captures().size()))
            .put("eventHighWater",Integer.toString(prefix.size())).put("plannedTotal",text(task,"plannedTotal"))
            .put("active",Integer.toString(active.size())).put("void",Long.toString(rows.scans().stream().filter(row->text(row,"status").equals("VOID")).count()))
            .put("weightGrams",active.stream().map(row->new java.math.BigInteger(text(row,"weightGrams"))).reduce(java.math.BigInteger.ZERO,java.math.BigInteger::add).toString())
            .put("shortfall",Long.toString(shortfall)).put("shortfallReason",reason).put("closedAt",anchored(end,source).toString())
            .put("closedWall",text(end,"wall")).put("closedElapsed",text(end,"elapsed")).put("closedBoot",text(end,"boot"))
            .put("factsSha256",sha(facts)).put("pendingReconciliation",true).put("businessProcessed",false).put("localDeletionAllowed",false);
        require(prefix.stream().allMatch(row->number(row,"elapsed")<=number(end,"elapsed")),"结束早于原审计");return result;
    }
    private static Instant anchored(JsonNode end,JsonNode source) {
        var anchor=source.path("timeAnchor");long delta=number(end,"elapsed")-number(anchor,"elapsed");
        require(delta>=0 && text(end,"boot").equals(text(anchor,"boot")),"结束启动代次不可信");
        require(java.math.BigInteger.valueOf(number(end,"wall")).subtract(java.math.BigInteger.valueOf(number(anchor,"wall")))
            .subtract(java.math.BigInteger.valueOf(delta)).abs().compareTo(java.math.BigInteger.valueOf(120000))<=0,"结束采样不可信");
        try {
            var at=Instant.parse(text(anchor,"server")).plusMillis(delta);var credential=source.path("credential");
            require(!at.isBefore(Instant.parse(text(credential,"issuedAt"))) && at.isBefore(Instant.parse(text(credential,"expiresAt"))),"结束超原授权期限");return at;
        } catch(DateTimeException error) { throw bad("结束时间无效"); }
    }
    private static void terminal(Rows rows) {
        for(var capture:rows.captures()) {
            String state=text(capture,"state");require(!Set.of("RECEIVED","WAITING_CONFIRM","CONFIRM_REQUESTED").contains(state),"结束仍有未决输入");
            require(!Set.of("INVALID","BUSINESS_REJECTED").contains(state) || !capture.path("failureAckAt").isNull(),"结束仍有未核失败输入");
            require(Set.of("COMMITTED","INVALID","BUSINESS_REJECTED","DISCARDED").contains(state),"结束输入状态无效");
        }
        equal(ids(rows.captures().stream().filter(row->text(row,"state").equals("COMMITTED")).toList(),"id"),ids(rows.scans(),"captureId"),"结束保存输入不完整");
        equal(ids(rows.scans().stream().filter(row->text(row,"status").equals("VOID")).toList(),"captureId"),ids(rows.voids(),"captureId"),"结束VOID不完整");
        for(var scan:rows.scans()) { number(scan,"weightGrams");require(Set.of("ACTIVE","VOID").contains(text(scan,"status")),"结束扫码状态无效"); }
        for(var entry:Map.of("INDEPENDENT_RECEIVED",ids(rows.captures(),"id"),"INDEPENDENT_SAVED",ids(rows.scans(),"captureId"),"INDEPENDENT_VOID",ids(rows.voids(),"captureId")).entrySet()) {
            var originals=rows.events().stream().filter(row->text(row,"type").equals(entry.getKey())).map(row->parse(text(row,"detail"))).toList();
            equal(ids(originals,"captureId"),entry.getValue(),"结束审计缺原事实");
        }
    }
    private static void scope(Rows rows,JsonNode source) {
        String id=text(source.path("task"),"id");
        for(var group:List.of(rows.captures(),rows.scans(),rows.events(),rows.lines(),rows.voids()))
            for(var row:group)equal(text(row,"taskId"),id,"结束事实跨任务");
        require(!rows.events().isEmpty() && text(rows.events().getFirst(),"type").equals("CREATE_PLANNED"),"结束缺创建事件");
        require(rows.events().stream().filter(row->text(row,"type").equals("CREATE_PLANNED")).count()==1,"原创建不唯一");
    }
    private static Set<String> ids(List<JsonNode> rows,String field) {
        var result=new HashSet<String>();for(var row:rows)require(result.add(text(row,field)),"结束原事实ID重复");return result;
    }
    private static void sequence(List<JsonNode> rows,String field) { for(int i=0;i<rows.size();i++)equal(number(rows.get(i),field),(long)i+1,"完整结束前缀缺失"); }
    private static Set<String> fields(JsonNode node) { var result=new HashSet<String>();node.fieldNames().forEachRemaining(result::add);return result; }
    private static com.fasterxml.jackson.databind.node.ArrayNode jsonArray(List<JsonNode> rows) {
        var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();rows.forEach(result::add);return result;
    }
    private static List<JsonNode> records(Map<String,byte[]> files,String name) {
        var result=new ArrayList<JsonNode>();for(String line:new String(files.get(name+".ndjson"),StandardCharsets.UTF_8).split("\n"))if(!line.isBlank())result.add(parse(line));
        require(result.size()<=100000,"结束原事实过多");return result;
    }
    record Rows(List<JsonNode> captures,List<JsonNode> scans,List<JsonNode> events,List<JsonNode> lines,List<JsonNode> voids) { }
}
