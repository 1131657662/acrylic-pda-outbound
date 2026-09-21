package com.acrylic.outbound.continuation;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.recall.*;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class ContinuationChecks {
    private final RecallStore recalls;private final SubmissionStore submissions;private final AssignmentStore assignments;
    private final SubmissionFiles files;private final JdbcTemplate jdbc;private final DispositionEvidence evidence;private final DispositionStock dispositions;
    public ContinuationChecks(RecallStore recalls,SubmissionStore submissions,AssignmentStore assignments,SubmissionFiles files,JdbcTemplate jdbc,DispositionEvidence evidence,DispositionStock dispositions) {
        this.recalls=recalls;this.submissions=submissions;this.assignments=assignments;this.files=files;this.jdbc=jdbc;this.evidence=evidence;this.dispositions=dispositions;
    }
    public Validated validate(RecallStore.Head r,JsonNode c) {
        var p=recalls.recovery(r.id());
        conflict(r.status().equals("STOPPED") && p!=null,"RECALL_STOP_REQUIRED","须原机完整恢复并确认停止");
        equal(uuid(c,"recoveryPackageId"),p.id(),"恢复包不匹配");
        conflict(parse(p.issues().getBytes(java.nio.charset.StandardCharsets.UTF_8)).isEmpty(),"RECOVERY_RECONCILIATION_REQUIRED","恢复证据存在冲突，须先对账");
        var old=submissions.required(uuid(c,"previousSubmissionId"));
        equal(old.control().assignment(),r.assignment(),"原批次不属于此次召回");
        conflict(Set.of("ABORTED","REJECTED").contains(old.status()),"PREVIOUS_SUBMISSION_NOT_TERMINAL","旧批次须已取消或退审，成功批次只能更正");
        submissions.current(old);var order=recalls.lockOrder(r.warehouse(),r.order());
        conflict(order.status().equals("RECALL_PENDING"),"ORDER_STATE_CONFLICT","订单不在召回待处理状态");
        conflict(number(c,"expectedWorkRound")==old.control().round(),"WORK_ROUND_CONFLICT","原轮次已变化");
        require(c.path("expectedRevision").isIntegralNumber(),"请填写计划修订号");
        conflict(c.path("expectedRevision").longValue()==order.revision() && order.revision()==r.revision(),"ORDER_VERSION_CONFLICT","计划版本已变化");
        var a=assignments.get(r.assignment());equal(uuid(c,"requestedDeviceId").toString(),a.deviceId(),"当前仅支持原人原机续作");
        equal(a.status(),"REVOKED","原机尚未停止");equal(a.serverRecoveryEpoch(),r.epoch(),"原恢复代次不符");
        JsonNode body=parse(files.read(p.hash()));var proofs=DispositionProofs.compose(body,c);evidence.validate(r,body,c);
        var retained=DispositionProofs.effective(body,proofs);
        return new Validated(old,body,retained,Math.addExact(p.captureHigh(),1));
    }
    public static JsonNode manifest(JsonNode body) {
        return DispositionProofs.effective(body);
    }
    public void stock(UUID order,JsonNode body,JsonNode proofs) {
        conflict(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE order_id=? AND source_evidence_set_id IS NOT NULL",Long.class,Ids.bytes(order))==0,
            "SOURCE_COMMITMENT_REQUIRES_RESOLUTION","来源证据占用须通过独立来源处置核验");
        var held=dispositions.held(body,proofs);var excluded=new HashSet<String>();proofs.forEach(p->excluded.add(text(p,"captureId")));
        var ids=new ArrayList<JsonNode>();held.forEach(ids::add);ids.sort(Comparator.comparing(r->text(r,"baleId")));
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE order_id=? AND state!='QUARANTINED'",Long.class,Ids.bytes(order));
        conflict(count==held.size(),"RETAINED_SET_MISMATCH","订单占用与核验保留及待处置集合不一致");
        for(var row:ids)checkBale(order,row,!excluded.contains(text(row,"captureId")));
    }
    private void checkBale(UUID order,JsonNode row,boolean retained) {
        byte[] bale=Ids.bytes(uuid(row,"baleId"));long cycle=jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE",Long.class,bale);
        conflict(cycle==number(row,"expectedDispatchCycle"),"DISPATCH_CYCLE_CONFLICT","保留包周期已变化，须先对账");
        var owner=jdbc.queryForMap("SELECT BIN_TO_UUID(order_id) AS owner,state,dispatch_cycle FROM bale_commitment WHERE bale_id=? FOR UPDATE",bale);
        conflict(order.toString().equals(owner.get("owner")) && Set.of("REVIEW_RESERVED","RECOVERY_RESERVED","CONTINUATION_RESERVED").contains(owner.get("state")) && ((Number)owner.get("dispatch_cycle")).longValue()==cycle,
            "BALE_ALREADY_COMMITTED","保留包占用不匹配");
        if(!retained)return;
        var positions=jdbc.queryForList("""
            SELECT p.upstream_present,p.book_status,p.blocked_reason,s.delivery_code,s.weight_g,s.model_snapshot,s.name_snapshot
            FROM inventory_position p INNER JOIN inventory_snapshot_bale s ON s.snapshot_id=p.last_seen_snapshot_id AND s.bale_id=p.bale_id
            WHERE p.bale_id=? FOR UPDATE
            """,bale);
        conflict(positions.size()==1,"INVENTORY_UNAVAILABLE","当前库存不可核验");var p=positions.getFirst();
        conflict(Boolean.TRUE.equals(p.get("upstream_present")) && p.get("book_status").equals("NOT_DEDUCTED") && p.get("blocked_reason")==null,
            "INVENTORY_UNAVAILABLE","当前包状态存在冲突");
        conflict(text(row,"deliveryCode").equals(p.get("delivery_code")) && number(row,"weightGrams")==((Number)p.get("weight_g")).longValue()
            && text(row,"model").equals(p.get("model_snapshot")) && text(row,"name").equals(p.get("name_snapshot")),"INVENTORY_BASELINE_CHANGED","产品重量快照已变化");
    }
    public static void conflict(boolean valid,String code,String message) { if(!valid)throw new BusinessException(409,code,message); }
    public record Validated(SubmissionStore.Head previous,JsonNode body,JsonNode retained,long nextSeq) {}
}
