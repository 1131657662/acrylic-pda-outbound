package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class RecallResolution {
    private final RecallAccess access;private final RecallStore store;private final RecallProofs proofs;private final RecallEvents events;
    private final IdempotencyRequests requests;private final JdbcTemplate jdbc;private final SubmissionStore submissions;private final SubmissionReceipts receipts;
    public RecallResolution(RecallAccess access,RecallStore store,RecallProofs proofs,RecallEvents events,IdempotencyRequests requests,
        JdbcTemplate jdbc,SubmissionStore submissions,SubmissionReceipts receipts) {
        this.access=access;this.store=store;this.proofs=proofs;this.events=events;this.requests=requests;this.jdbc=jdbc;this.submissions=submissions;this.receipts=receipts;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode resolve(RecallRequests.Context c,UUID id,JsonNode command) {
        var before=store.get(id);equal(before.warehouse(),c.warehouse(),"仓库不匹配");
        UUID actor=access.web(c.warehouse(),c.epoch(),true);access.cancel(c.warehouse());var h=store.lock(before);
        var key=new IdempotencyRequests.Key(actor,"order.recall.resolve",c.key());String hash=sha(object().put("changeRequestId",id.toString()).set("command",command));
        var replay=requests.find(key,hash);if(replay!=null)return parse(store.get(replay).resolution().getBytes(StandardCharsets.UTF_8));
        equal(text(command,"action"),"CANCEL_EMPTY","当前仅支持主管核验零包作废");var p=store.recovery(id);
        validate(h,p,command);abortSubmission(h);
        String physical=text(command,"physicalEvidence").strip();require(!physical.isBlank() && physical.length()<=1000,"请填写1至1000字实物核验凭据");
        var receipt=proofs.sign(h,p,"CANCELLED");
        jdbc.update("UPDATE order_change_request SET status='CANCELLED',resolution_receipt=?,resolved_by=?,physical_evidence=?,resolved_at=UTC_TIMESTAMP(3) WHERE id=?",
            canonical(receipt),Ids.bytes(actor),physical,Ids.bytes(id));
        events.transition(h,actor,"ORDER_RECALL_CANCELLED","CANCELLED");requests.record(key,hash,id);return receipt;
    }
    private void validate(RecallStore.Head h,RecallStore.Package p,JsonNode command) {
        if(!h.status().equals("STOPPED") || p==null)throw new BusinessException(409,"RECALL_STOP_REQUIRED","须原机完整恢复并确认停止");
        var order=store.lockOrder(h.warehouse(),h.order());
        if(!order.rowVersion().equals(text(command,"expectedVersion")))throw new BusinessException(409,"ORDER_VERSION_CONFLICT","订单已变化，请刷新核对");
        if(!order.status().equals("RECALL_PENDING"))throw new BusinessException(409,"ORDER_STATE_CONFLICT","当前订单不可作废");
        equal(uuid(command,"recoveryPackageId"),p.id(),"须核对原恢复包");equal(text(command,"payloadSha256"),p.hash(),"恢复包摘要不匹配");
        long reserved=jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE order_id=? AND state!='QUARANTINED'",Long.class,Ids.bytes(h.order()));
        if(p.count()!=0 || reserved!=0)throw new BusinessException(409,"RECALL_HAS_LOADED_BALES","有装车记录或包占用，必须保留待处理");
        equal(jdbc.queryForObject("SELECT status FROM order_assignment WHERE id=?",String.class,Ids.bytes(h.assignment())),"REVOKED","原授权尚未停止");
    }
    private void abortSubmission(RecallStore.Head h) {
        var ids=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM submission WHERE assignment_id=? ORDER BY id FOR UPDATE",String.class,Ids.bytes(h.assignment()));
        for(String id:ids) {
            var s=submissions.required(UUID.fromString(id));
            if(s.status().equals("SUCCEEDED"))throw new BusinessException(409,"ORDER_STATE_CONFLICT","已成功批次须办理更正");
            if(Set.of("ABORTED","REJECTED").contains(s.status()))continue;
            var receipt=receipts.abort(s,submissions.payloadHash(s.control().id()),"原机停止并完成零包实物核验，主管作废");
            submissions.state(s.control().id(),"ABORTED",null,canonical(receipt));
        }
    }
}
