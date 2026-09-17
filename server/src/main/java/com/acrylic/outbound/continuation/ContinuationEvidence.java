package com.acrylic.outbound.continuation;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.continuation.ContinuationChecks.conflict;

@Component
public class ContinuationEvidence {
    private final ContinuationStore store;private final AssignmentStore assignments;private final JdbcTemplate jdbc;private final ContinuationChecks checks;private final DispositionStock dispositions;
    public ContinuationEvidence(ContinuationStore store,AssignmentStore assignments,JdbcTemplate jdbc,ContinuationChecks checks,DispositionStock dispositions) { this.store=store;this.assignments=assignments;this.jdbc=jdbc;this.checks=checks;this.dispositions=dispositions; }
    public Map<String,AssignmentStore.View> origins(AssignmentStore.View current,JsonNode body) {
        var result=new HashMap<String,AssignmentStore.View>();result.put(current.id(),current);var h=store.forAssignment(UUID.fromString(current.id()));
        if(h==null) { require(body.path("continuationId").isNull() && DispositionProofs.rows(body).isEmpty(),"首轮不可使用续作票据或处置证明");return result; }
        equal(uuid(body,"continuationId"),h.id(),"续作票据与当前授权不匹配");equal(number(body,"workRound"),h.toRound(),"续作轮次不匹配");
        conflict(Set.of("ACTIVATED","COMPLETED").contains(h.status()),"CONTINUATION_NOT_ACTIVATED","原机须联网核验并激活续作");
        conflict(DispositionProofs.rows(body).equals(store.dispositions(h)),"DISPOSITION_EVIDENCE_MISMATCH","累计实物处置凭据被遗漏或改写");
        var old=store.body(h);var ids=new HashSet<String>();
        for(String key:List.of("captures","scans","events")) {
            var previous=array(old,key);var next=array(body,key);conflict(next.size()>=previous.size(),"RETAINED_SET_MISMATCH","缺少原恢复证据");
            for(int i=0;i<previous.size();i++)conflict(previous.get(i).equals(next.get(i)),"RETAINED_SET_MISMATCH","原恢复证据被改写或遗漏");
        }
        for(var c:array(old,"captures")) { ids.add(text(c,"id"));origin(result,text(c,"assignmentId"),current); }
        for(var e:array(old,"events"))origin(result,text(e,"assignmentId"),current);
        for(int i=array(old,"captures").size();i<array(body,"captures").size();i++)equal(text(array(body,"captures").get(i),"assignmentId"),current.id(),"新增输入必须使用新授权");
        for(int i=array(old,"events").size();i<array(body,"events").size();i++) {
            var event=array(body,"events").get(i);equal(text(event,"assignmentId"),current.id(),"新增审计必须使用新授权");
            conflict(!ids.contains(event.path("captureId").asText()),"RETAINED_SET_MISMATCH","保留原记录只读，变更须独立实物凭据");
        }
        return result;
    }
    private void origin(Map<String,AssignmentStore.View> result,String id,AssignmentStore.View current) {
        var old=result.computeIfAbsent(id,k->assignments.get(UUID.fromString(k)));
        equal(old.orderId(),current.orderId(),"原记录订单不匹配");equal(old.deviceId(),current.deviceId(),"原记录设备不匹配");equal(old.operatorId(),current.operatorId(),"原记录人员不匹配");
    }
    public void transfer(SubmissionStore.Head head,JsonNode body) {
        var h=store.forAssignment(head.control().assignment());if(h==null)return;
        var old=store.body(h);var proofs=store.dispositions(h);checks.stock(h.order(),old,proofs);
        var retained=DispositionProofs.effective(old,proofs);dispositions.apply(head,proofs);
        // Only current retained rows are required; newly received bales are reserved by the normal finalizer.
        for(var row:retained) {
            byte[] bale=Ids.bytes(uuid(row,"baleId"));long cycle=jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE",Long.class,bale);
            conflict(cycle==number(row,"expectedDispatchCycle"),"DISPATCH_CYCLE_CONFLICT","保留包周期变化，须先对账");
            int moved=jdbc.update("""
                UPDATE bale_commitment SET state='REVIEW_RESERVED',submission_id=?,continuation_id=NULL
                WHERE bale_id=? AND order_id=? AND continuation_id=? AND state='CONTINUATION_RESERVED' AND dispatch_cycle=?
                """,Ids.bytes(head.control().id()),bale,Ids.bytes(h.order()),Ids.bytes(h.id()),cycle);
            conflict(moved==1,"RETAINED_SET_MISMATCH","保留包占用已变化，停止接收新批次");
        }
    }
}
