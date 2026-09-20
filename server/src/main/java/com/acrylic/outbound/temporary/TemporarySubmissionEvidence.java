package com.acrylic.outbound.temporary;

import com.acrylic.outbound.audit.PdaAuditFacts;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.submissions.SubmissionEvidence.time;

@Component
public class TemporarySubmissionEvidence {
    public void validate(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan,JsonNode body) {
        require(body.path("schemaVersion").isInt() && body.path("schemaVersion").intValue()==2,"临时证据版本错误");
        require(array(body,"dispositions").isEmpty(),"临时首轮不接受未经批准的处置");
        equal(text(body,"authorizationEpoch"),plan.authorization().authorizationEpoch(),"原资格序号不符");
        Instant closed=time(text(body,"closedAt"));require(!closed.isAfter(Instant.now().plusSeconds(300)),"结单时间超前");
        var captures=captures(head,plan,body);var events=events(head,body,captures);
        var ids=new HashSet<String>();var codes=new HashSet<String>();long previous=0,saved=0,voided=0;
        for(var scan:array(body,"scans")) {
            String id=text(scan,"captureId");require(ids.add(id),"保存记录重复");var c=captures.get(id);
            require(c!=null && text(c,"state").equals("COMMITTED"),"明细缺少原输入");
            long seq=number(scan,"orderScanSeq");require(seq>previous,"扫码明细乱序");previous=seq;
            scan(scan,c,events,plan.authorization());require(!time(text(scan,"savedAt")).isAfter(closed),"保存晚于冻结");
            if(text(scan,"status").equals("ACTIVE")) { require(codes.add(text(scan,"barcode")),"有效条码重复");saved++; }
            else { equal(text(scan,"status"),"VOID","保存状态无效");require(has(events,id,"VOID"),"撤销缺少实物核对事件");voided++; }
        }
        for(var c:captures.values())equal(text(c,"state").equals("COMMITTED"),ids.contains(text(c,"id")),"保存事实不完整");
        summary(body,captures.values(),saved,voided);require(saved>0,"零包不能结单");
    }
    private Map<String,JsonNode> captures(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan,JsonNode body) {
        var result=new LinkedHashMap<String,JsonNode>();long seq=0,deviceSeq=0;var a=plan.authorization();
        for(var c:array(body,"captures")) {
            String id=uuid(c,"id").toString();require(result.put(id,c)==null,"输入ID重复");equal(number(c,"orderScanSeq"),++seq,"输入缺口或乱序");
            long d=number(c,"deviceCaptureSeq");require(d>deviceSeq,"设备输入序号乱序");deviceSeq=d;
            equal(text(c,"clientOrderId"),head.control().temporary().client().toString(),"输入原临时单错误");
            equal(text(c,"originDeviceId"),a.deviceId().toString(),"输入原设备错误");equal(text(c,"operatorId"),a.userId().toString(),"输入原人员错误");
            equal(text(c,"authorizationId"),a.id().toString(),"输入原资格错误");equal(text(c,"authorizationEpoch"),a.authorizationEpoch(),"输入资格序号错误");
            equal(text(c,"snapshotId"),text(body,"inventorySnapshotId"),"输入原快照错误");equal(text(c,"sourceRecoveryEpoch"),a.recoveryEpoch(),"输入恢复代次错误");
            equal(number(c,"orderRevision"),number(body,"orderRevision"),"输入原计划版本错误");
            String source=text(c,"inputSource"),state=text(c,"state");require(Set.of("MANUAL","BROADCAST").contains(source),"输入方式无效");
            equal(text(c,"confirmationPolicy"),source.equals("BROADCAST")?"AUTO_AFTER_VALIDATION":"MANUAL","确认策略无效");
            require(Set.of("COMMITTED","INVALID","BUSINESS_REJECTED","DISCARDED").contains(state),"存在未决输入");
            require(text(c,"rawInput").length()<=128 && text(c,"error").length()<=500,"输入过长");
            number(c,"receivedWallMs");number(c,"receivedElapsedMs");require(c.path("receivedBoot").isInt(),"启动次数错误");
            if(Set.of("INVALID","BUSINESS_REJECTED").contains(state))time(text(c,"failureAckAt"));
            if(state.equals("DISCARDED"))require(source.equals("MANUAL") && c.path("confirmedAt").isNull(),"不能放弃已确认输入");
        }
        equal(number(body,"captureHighWatermark"),seq,"输入高水位不符");return result;
    }
    private List<JsonNode> events(SubmissionStore.Head head,JsonNode body,Map<String,JsonNode> captures) {
        var result=new ArrayList<JsonNode>();var ids=new HashSet<UUID>();long previous=0;
        for(var e:array(body,"events")) {
            PdaAuditFacts.validate(e);equal(e.get("source"),head.control().source(),"审计原来源错误");
            equal(text(e,"authorizationId"),head.control().temporary().authorization().toString(),"审计原资格错误");
            equal(uuid(e,"operatorId"),head.actor(),"审计原人员错误");require(ids.add(uuid(e,"id")),"事件重复");
            long sequence=number(e,"localSeq");require(sequence>previous,"事件乱序");previous=sequence;
            if(!e.path("captureId").isNull())require(captures.containsKey(text(e,"captureId")),"事件引用未知输入");result.add(e);
        }
        require(!result.isEmpty(),"缺少冻结事件");var last=result.getLast();equal(text(last,"type"),"FROZEN","缺少冻结事件");
        equal(text(last,"detail"),head.control().id().toString(),"冻结批次错误");require(last.path("captureId").isNull(),"冻结不可指向单包");
        for(var c:captures.values()) {
            String id=text(c,"id"),state=text(c,"state");require(has(result,id,"RECEIVED") && has(result,id,state.equals("COMMITTED")?"SAVED":state),"缺输入/终态事件");
            if(Set.of("INVALID","BUSINESS_REJECTED").contains(state))require(has(result,id,"FAILURE_ACKNOWLEDGED"),"缺失败确认事件");
        }
        return result;
    }
    private void scan(JsonNode s,JsonNode c,List<JsonNode> events,TemporaryStore.Stored a) {
        equal(number(s,"orderScanSeq"),number(c,"orderScanSeq"),"扫码序号不符");equal(text(s,"barcode"),text(c,"barcode"),"条码不符");
        String raw=text(c,"rawInput").replaceAll("^[ \\r\\n\\t]+|[ \\r\\n\\t]+$","");require(raw.matches("[0-9A-Za-z]{11}"),"有效条码格式错误");
        equal(raw.toUpperCase(Locale.ROOT),text(s,"barcode"),"原始条码错误");equal(number(s,"expectedDispatchCycle"),number(c,"expectedDispatchCycle"),"原周期不符");
        require(number(s,"expectedDispatchCycle")>0,"原周期错误");Instant saved=time(text(s,"savedAt")),confirmed=time(text(c,"confirmedAt"));
        require(!confirmed.isBefore(a.issuedAt()) && !saved.isBefore(confirmed) && saved.isBefore(a.expiresAt()),"保存超出原资格有效期");
        String id=text(c,"id");require(has(events,id,"CONFIRM_REQUESTED"),"缺确认意图");
        require(events.stream().anyMatch(e->number(e,"localSeq")==number(s,"localSeq") && e.path("captureId").asText().equals(id) && text(e,"type").equals("SAVED")),"保存事件引用错误");
    }
    private boolean has(List<JsonNode> events,String id,String type) { return events.stream().anyMatch(e->e.path("captureId").asText().equals(id) && text(e,"type").equals(type)); }
    private void summary(JsonNode body,Collection<JsonNode> captures,long saved,long voided) {
        var s=body.path("captureSummary");equal(number(s,"received"),(long)captures.size(),"接收数不符");equal(number(s,"saved"),saved,"有效数不符");
        equal(number(s,"voided"),voided,"撤销数不符");equal(number(s,"pending"),0L,"存在未决输入");equal(number(body,"expectedCount"),saved,"有效数不符");
        Map.of("invalid","INVALID","businessRejected","BUSINESS_REJECTED","discarded","DISCARDED").forEach((key,state)->
            equal(number(s,key),captures.stream().filter(c->text(c,"state").equals(state)).count(),"终态统计不符"));
    }
}
