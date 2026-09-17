package com.acrylic.outbound.submissions;

import com.acrylic.outbound.pda.AssignmentStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class SubmissionEvidence {
    private final com.acrylic.outbound.continuation.ContinuationEvidence continuations;
    public SubmissionEvidence(com.acrylic.outbound.continuation.ContinuationEvidence continuations) { this.continuations=continuations; }
    public void validate(SubmissionStore.Head head,AssignmentStore.View a,JsonNode body) {
        validate(new Context(head.control().id(),head.control().assignment(),head.control().device(),head.actor(),head.warehouse()),a,body,false);
    }
    public void validateRecovery(AssignmentStore.View a,UUID warehouse,JsonNode body) {
        validate(new Context(uuid(body,"recoveryPackageId"),UUID.fromString(a.id()),UUID.fromString(a.deviceId()),UUID.fromString(a.operatorId()),warehouse),a,body,true);
    }
    private record Context(UUID id,UUID assignment,UUID device,UUID actor,UUID warehouse) {}
    private void validate(Context context,AssignmentStore.View a,JsonNode body,boolean recovery) {
        equal(body.path("schemaVersion").intValue(),2,"证据协议版本无效");equal(uuid(body,recovery?"recoveryPackageId":"submissionId"),context.id(),"证据ID不一致");
        equal(body.path("orderRevision").intValue(),a.revision(),"原计划版本不一致");equal(text(body,"assignmentEpoch"),a.epoch(),"授权代次不一致");
        equal(text(body,"inventorySnapshotId"),a.inventorySnapshotId(),"固定库存版本不一致");
        var origins=continuations.origins(a,body);
        Instant closed=time(text(body,"closedAt"));require(!closed.isAfter(Instant.now().plusSeconds(300)),"结单时间超前");
        var captures=captures(context,a,body,origins);var events=events(context,body,captures,recovery,origins);
        var excluded=com.acrylic.outbound.continuation.DispositionProofs.excluded(body);
        var scanIds=new HashSet<UUID>();var activeCodes=new HashSet<String>();long saved=0,voided=0,last=0;
        for(var scan:array(body,"scans")) {
            UUID id=uuid(scan,"captureId");require(scanIds.add(id),"重复保存记录");JsonNode capture=captures.get(id);
            require(capture!=null && text(capture,"state").equals("COMMITTED"),"保存记录缺少有效原输入");
            long seq=number(scan,"orderScanSeq");require(seq>last,"明细必须保持原扫码顺序");last=seq;
            scan(origins.get(text(capture,"assignmentId")),scan,capture,events);require(!time(text(scan,"savedAt")).isAfter(closed),"保存时间晚于结单");
            if(text(scan,"status").equals("ACTIVE")) { if(!excluded.contains(id.toString())) { saved++;require(activeCodes.add(text(scan,"barcode")),"有效明细重复条码"); } }
            else { equal(text(scan,"status"),"VOID","明细状态无效");voided++;require(has(events,id,"VOID"),"撤销缺少实物核对审计"); }
        }
        for(var entry:captures.entrySet()) require(text(entry.getValue(),"state").equals("COMMITTED")==scanIds.contains(entry.getKey()),"输入与保存明细不完整");
        summary(body,captures.values(),saved,voided);require(recovery || saved>0,"整单0包不能确认出库");
    }
    private Map<UUID,JsonNode> captures(Context h,AssignmentStore.View current,JsonNode body,Map<String,AssignmentStore.View> origins) {
        var result=new LinkedHashMap<UUID,JsonNode>();var deviceSeqs=new HashSet<Long>();long seq=1,previousDevice=0;
        for(var c:array(body,"captures")) {
            var a=origins.get(text(c,"assignmentId"));require(a!=null,"输入原授权不在核验来源中");
            UUID id=uuid(c,"id");require(result.put(id,c)==null,"原输入ID重复");
            equal(number(c,"orderScanSeq"),seq++,"输入序号有缺口或乱序");long d=number(c,"deviceCaptureSeq");require(d>previousDevice && deviceSeqs.add(d),"设备输入序号重复或乱序");previousDevice=d;
            equal(text(c,"assignmentId"),a.id(),"输入原授权错误");equal(text(c,"orderId"),a.orderId(),"输入原订单错误");
            equal(text(c,"originDeviceId"),a.deviceId(),"输入原设备错误");equal(text(c,"operatorId"),a.operatorId(),"输入原人员错误");
            equal(text(c,"warehouseId"),h.warehouse().toString(),"输入仓库错误");equal(text(c,"authorizationId"),a.offlineAuthorizationId(),"输入离线凭据错误");
            equal(text(c,"assignmentEpoch"),a.epoch(),"输入授权代次错误");equal(c.path("orderRevision").intValue(),a.revision(),"输入修订错误");
            equal(text(c,"snapshotId"),a.inventorySnapshotId(),"输入快照错误");equal(text(c,"sourceRecoveryEpoch"),a.serverRecoveryEpoch(),"输入恢复代次错误");
            String source=text(c,"inputSource"),state=text(c,"state");require(Set.of("BROADCAST","MANUAL").contains(source),"输入来源无效");
            equal(text(c,"confirmationPolicy"),source.equals("BROADCAST")?"AUTO_AFTER_VALIDATION":"MANUAL","确认策略无效");
            require(Set.of("COMMITTED","INVALID","BUSINESS_REJECTED","DISCARDED").contains(state),"存在未决输入，不能结单");
            require(text(c,"rawInput").length()<=128 && text(c,"error").length()<=500,"输入字段过长");
            number(c,"receivedWallMs");number(c,"receivedElapsedMs");require(c.path("receivedBoot").isIntegralNumber(),"缺少启动次数");
            if(state.equals("INVALID") || state.equals("BUSINESS_REJECTED")) time(text(c,"failureAckAt"));
            if(state.equals("DISCARDED")) require(source.equals("MANUAL") && c.path("confirmedAt").isNull(),"已确认输入不可放弃");
        }
        equal(number(body,"captureHighWatermark"),seq-1,"输入高水位错误");return result;
    }
    private record EventIndex(Map<UUID,Set<String>> types,Map<Long,JsonNode> sequences) {}
    private EventIndex events(Context h,JsonNode body,Map<UUID,JsonNode> captures,boolean recovery,Map<String,AssignmentStore.View> origins) {
        var result=new EventIndex(new HashMap<>(),new HashMap<>());var ids=new HashSet<UUID>();long previous=0;JsonNode last=null;
        for(var e:array(body,"events")) {
            require(ids.add(uuid(e,"id")),"审计ID重复");long seq=number(e,"localSeq");require(seq>previous,"本单审计顺序错误");previous=seq;
            require(origins.containsKey(text(e,"assignmentId")),"审计订单来源错误");equal(text(e,"originDeviceId"),h.device().toString(),"审计设备错误");
            equal(text(e,"operatorId"),h.actor().toString(),"审计人员错误");number(e,"wallMs");number(e,"elapsedMs");
            require(e.path("boot").isIntegralNumber() && text(e,"detail").length()<=500,"审计字段错误");
            if(!e.path("captureId").isNull()) {
                UUID id=uuid(e,"captureId");require(captures.containsKey(id),"审计指向不存在输入");
                result.types().computeIfAbsent(id,key->new HashSet<>()).add(text(e,"type"));
            }
            result.sequences().put(seq,e);last=e;
        }
        require(last!=null && text(last,"type").equals(recovery?"RECALL_STOPPED":"FROZEN"),"缺少停止/冻结审计");
        equal(text(last,"assignmentId"),h.assignment().toString(),"最后审计须使用当前授权");
        require(last.path("captureId").isNull(),"冻结审计不可关联单条输入");
        equal(text(last,"detail"),h.id().toString(),"冻结审计批次错误");
        for(var entry:captures.entrySet()) {
            UUID id=entry.getKey();String state=text(entry.getValue(),"state");require(has(result,id,"RECEIVED"),"缺少接收审计");
            require(has(result,id,state.equals("COMMITTED")?"SAVED":state),"缺少输入终态审计");
            if(state.equals("INVALID") || state.equals("BUSINESS_REJECTED")) require(has(result,id,"FAILURE_ACKNOWLEDGED"),"未确认失败处理");
        }
        return result;
    }
    private void scan(AssignmentStore.View a,JsonNode scan,JsonNode capture,EventIndex events) {
        equal(number(scan,"orderScanSeq"),number(capture,"orderScanSeq"),"保存序号错误");equal(text(scan,"barcode"),text(capture,"barcode"),"保存条码错误");
        String raw=text(capture,"rawInput").replaceAll("^[ \\r\\n\\t]+|[ \\r\\n\\t]+$","");require(raw.matches("[0-9A-Za-z]{11}"),"有效条码格式错误");
        equal(raw.toUpperCase(Locale.ROOT),text(scan,"barcode"),"原输入与条码不一致");
        equal(number(scan,"expectedDispatchCycle"),number(capture,"expectedDispatchCycle"),"原周期不一致");require(number(scan,"expectedDispatchCycle")>0,"原周期无效");
        Instant saved=time(text(scan,"savedAt")),confirmed=time(text(capture,"confirmedAt")),issued=Instant.parse(a.issuedAt()),expires=Instant.parse(a.expiresAt());
        require(!confirmed.isBefore(issued) && !saved.isBefore(confirmed) && saved.isBefore(expires),"保存不在原离线授权期限内");
        require(has(events,uuid(capture,"id"),"CONFIRM_REQUESTED"),"缺少确认意图审计");
        JsonNode savedEvent=events.sequences().get(number(scan,"localSeq"));
        require(savedEvent!=null && savedEvent.path("captureId").asText().equals(text(capture,"id")) && text(savedEvent,"type").equals("SAVED"),"保存审计引用错误");
    }
    private boolean has(EventIndex events,UUID id,String type) { return events.types().getOrDefault(id,Set.of()).contains(type); }
    private void summary(JsonNode body,Collection<JsonNode> captures,long saved,long voided) {
        var summary=body.path("captureSummary");equal(number(summary,"received"),(long)captures.size(),"接收统计不一致");
        equal(number(summary,"saved"),saved,"保存统计不一致");equal(number(summary,"voided"),voided,"撤销统计不一致");equal(number(summary,"pending"),0L,"存在未决输入");
        var states=Map.of("invalid","INVALID","businessRejected","BUSINESS_REJECTED","discarded","DISCARDED");
        states.forEach((k,v)->equal(number(summary,k),captures.stream().filter(c->text(c,"state").equals(v)).count(),"输入终态统计不一致"));
        equal(number(body,"expectedCount"),saved,"有效包数不一致");
    }
    public static Instant time(String text) { try{return Instant.parse(text);}catch(java.time.DateTimeException e){throw bad("时间格式错误");} }
}
