package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class RecallStops {
    private final RecallAccess access;private final RecallStore store;private final RecallProofs proofs;
    private final JdbcTemplate jdbc;private final RecallEvents events;
    public RecallStops(RecallAccess access,RecallStore store,RecallProofs proofs,JdbcTemplate jdbc,RecallEvents events) {
        this.access=access;this.store=store;this.proofs=proofs;this.jdbc=jdbc;this.events=events;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode acknowledge(UUID id,JsonNode body,String epoch) {
        var before=store.get(id);var actor=access.pda(before.assignment(),epoch,true);var h=store.lock(before);
        var p=store.recovery(id);require(p!=null,"必须先完整上传恢复包");
        equal(uuid(body,"recoveryPackageId"),p.id(),"恢复包ID不匹配");equal(text(body,"payloadSha256"),p.hash(),"恢复包摘要不匹配");
        equal(number(body,"captureHighWatermark"),p.captureHigh(),"输入高水位不匹配");equal(number(body,"auditThroughSeq"),p.auditHigh(),"审计覆盖终点不匹配");
        if(h.stop()!=null)return parse(h.stop().getBytes(StandardCharsets.UTF_8));
        if(!h.status().equals("REQUESTED"))throw new BusinessException(409,"RECALL_STATE_CONFLICT","当前召回不可确认停止");
        var receipt=proofs.sign(h,p,"STOPPED");
        jdbc.update("UPDATE order_assignment SET status='REVOKED' WHERE id=?",Ids.bytes(h.assignment()));
        jdbc.update("UPDATE offline_authorization SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(3)) WHERE assignment_id=?",Ids.bytes(h.assignment()));
        jdbc.update("UPDATE order_change_request SET status='STOPPED',stopped_at=UTC_TIMESTAMP(3),stop_receipt=? WHERE id=?",canonical(receipt),Ids.bytes(id));
        events.transition(h,actor.userId(),"ORDER_RECALL_STOPPED","RECALL_PENDING");return receipt;
    }
}
