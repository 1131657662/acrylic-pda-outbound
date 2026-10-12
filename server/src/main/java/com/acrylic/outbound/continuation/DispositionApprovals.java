package com.acrylic.outbound.continuation;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.recall.*;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.continuation.ContinuationChecks.conflict;

@Service
public class DispositionApprovals {
    private final RecallAccess access;private final Access permissions;private final RecallStore recalls;private final JdbcTemplate jdbc;
    private final IdempotencyRequests requests;private final SubmissionFiles files;private final DispositionEvidence evidence;
    private final DispositionReceipts receipts;
    public DispositionApprovals(RecallAccess access,Access permissions,RecallStore recalls,JdbcTemplate jdbc,IdempotencyRequests requests,SubmissionFiles files,DispositionEvidence evidence,DispositionReceipts receipts) {
        this.access=access;this.permissions=permissions;this.recalls=recalls;this.jdbc=jdbc;this.requests=requests;this.files=files;this.evidence=evidence;this.receipts=receipts;
    }
    public JsonNode read(UUID warehouse,UUID packageId) {
        access.web(warehouse,null,false);owned(warehouse,packageId);return evidence.list(packageId,false);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode approve(RecallRequests.Context c,UUID packageId,JsonNode command) {
        UUID actor=access.web(c.warehouse(),c.epoch(),true);permissions.require("order:dispose",c.warehouse());
        var r=recalls.lock(owned(c.warehouse(),packageId));equal(r.epoch(),c.epoch(),"恢复代次变化须先对账");
        var key=new IdempotencyRequests.Key(actor,"order.disposition.approve",c.key());String hash=sha(object().put("packageId",packageId.toString()).set("command",command));
        var replay=requests.find(key,hash);if(replay!=null)return evidence.list(replay,true);
        conflict(r.status().equals("STOPPED"),"RECALL_STOP_REQUIRED","须原机完整恢复并停止后核验实物");
        var p=recalls.recovery(r.id());equal(text(command,"payloadSha256"),p.hash(),"恢复包摘要不匹配");
        conflict(parse(p.issues().getBytes(java.nio.charset.StandardCharsets.UTF_8)).isEmpty(),"RECOVERY_RECONCILIATION_REQUIRED","恢复证据存在冲突，须先对账");
        conflict(recalls.lockOrder(r.warehouse(),r.order()).rowVersion().equals(text(command,"expectedVersion")),"ORDER_VERSION_CONFLICT","订单版本已变化");
        var body=parse(files.read(p.hash()));var scans=new HashMap<String,JsonNode>();DispositionProofs.effective(body).forEach(s->scans.put(text(s,"captureId"),s));
        var rows=array(command,"dispositions");require(!rows.isEmpty() && rows.size()<=scans.size(),"请选择需要卸货释放或隔离的包");
        UUID batch=Ids.next();var seen=new HashSet<String>();
        for(var row:rows) { require(seen.add(text(row,"captureId")),"实物处置行重复");save(new Approval(batch,actor,r,p),row,scans.get(text(row,"captureId"))); }
        requests.record(key,hash,batch);var response=evidence.list(batch,true);
        receipts.record(batch,packageId,actor,hash,command,response);return response;
    }
    private void save(Approval a,JsonNode row,JsonNode scan) {
        require(scan!=null,"处置行不在当前恢复包有效集合中");String action=text(row,"action"),physical=text(row,"physicalEvidence").strip();
        require(Set.of("RELEASE","QUARANTINE").contains(action),"只允许释放或隔离实物核验");
        require(!physical.isBlank() && physical.length()<=1000,"请填写卸货或隔离位置、结果及核验凭据");
        equal(number(row,"expectedDispatchCycle"),number(scan,"expectedDispatchCycle"),"实物处置周期不匹配");
        int exists=jdbc.queryForObject("SELECT COUNT(*) FROM physical_disposition_evidence WHERE recovery_package_id=? AND origin_scan_id=?",Integer.class,
            Ids.bytes(a.p().id()),Ids.bytes(uuid(scan,"captureId")));
        conflict(exists==0,"DISPOSITION_ALREADY_APPROVED","该原包已有不可改写的批准凭据，请查询原凭据");
        jdbc.update("""
            INSERT INTO physical_disposition_evidence(id,approval_batch_id,recovery_package_id,order_id,bale_id,origin_scan_id,expected_cycle,
            action,record_sha256,recovery_epoch,physical_evidence,approved_by,approved_at) VALUES(?,?,?,?,?,?,?,?,UNHEX(?),?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(Ids.next()),Ids.bytes(a.batch()),Ids.bytes(a.p().id()),Ids.bytes(a.r().order()),Ids.bytes(uuid(scan,"baleId")),
            Ids.bytes(uuid(scan,"captureId")),number(scan,"expectedDispatchCycle"),action,sha(scan),a.r().epoch(),physical,Ids.bytes(a.actor()));
    }
    private RecallStore.Head owned(UUID warehouse,UUID packageId) {
        var ids=jdbc.queryForList("SELECT BIN_TO_UUID(request_id) FROM recovery_package WHERE id=?",String.class,Ids.bytes(packageId));
        if(ids.isEmpty())throw new BusinessException(404,"RECOVERY_PACKAGE_NOT_FOUND","恢复包不存在或无权访问");
        var r=recalls.get(UUID.fromString(ids.getFirst()));
        if(!r.warehouse().equals(warehouse))throw new BusinessException(404,"RECOVERY_PACKAGE_NOT_FOUND","恢复包不存在或无权访问");return r;
    }
    private record Approval(UUID batch,UUID actor,RecallStore.Head r,RecallStore.Package p) {}
}
