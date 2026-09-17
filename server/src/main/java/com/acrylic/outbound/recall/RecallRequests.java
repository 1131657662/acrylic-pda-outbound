package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.AssignmentStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class RecallRequests {
    private final RecallAccess access;private final RecallStore store;private final AssignmentStore assignments;private final IdempotencyRequests requests;
    private final JdbcTemplate jdbc;private final RecallEvents events;
    public RecallRequests(RecallAccess access,RecallStore store,AssignmentStore assignments,IdempotencyRequests requests,JdbcTemplate jdbc,RecallEvents events) {
        this.access=access;this.store=store;this.assignments=assignments;this.requests=requests;this.jdbc=jdbc;this.events=events;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode create(Context c,UUID order,JsonNode command) {
        UUID actor=access.web(c.warehouse(),c.epoch(),true);var head=store.lockOrder(c.warehouse(),order);
        var key=new IdempotencyRequests.Key(actor,"order.recall",c.key());String hash=sha(object().put("orderId",order.toString()).set("command",command));
        var replay=requests.find(key,hash);if(replay!=null)return store.view(store.get(replay));
        equal(text(command,"type"),"RECALL","当前入口仅支持召回");String reason=text(command,"reason").strip();require(!reason.isBlank() && reason.length()<=500,"请填写召回原因");
        if(!head.rowVersion().equals(text(command,"expectedVersion"))) throw new BusinessException(409,"ORDER_VERSION_CONFLICT","订单已变化，请刷新核对");
        if(!Set.of("ASSIGNED","REVIEW_REQUIRED","CONTINUING").contains(head.status())) throw new BusinessException(409,"ORDER_STATE_CONFLICT","已成功订单须办理更正；当前状态不可申请召回");
        var a=assignments.active(order);require(a!=null,"缺少原活动授权");
        if(store.forAssignment(UUID.fromString(a.id()))!=null) throw new BusinessException(409,"RECALL_ALREADY_EXISTS","原授权已有召回，请查询原申请");
        equal(a.serverRecoveryEpoch(),c.epoch(),"原授权恢复代次不一致");UUID id=Ids.next();
        jdbc.update("""
            INSERT INTO order_change_request(id,warehouse_id,order_id,assignment_id,revision,recovery_epoch,status,reason,requested_by,created_at)
            VALUES(?,?,?,?,?,?,'REQUESTED',?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(c.warehouse()),Ids.bytes(order),Ids.bytes(UUID.fromString(a.id())),a.revision(),Long.parseLong(c.epoch()),reason,Ids.bytes(actor));
        jdbc.update("UPDATE order_assignment SET status='RECALL_PENDING' WHERE id=?",Ids.bytes(UUID.fromString(a.id())));
        events.transition(store.get(id),actor,"ORDER_RECALL_REQUESTED","RECALL_PENDING");requests.record(key,hash,id);return store.view(store.get(id));
    }
    @Transactional(readOnly=true) public JsonNode web(UUID order,UUID warehouse) {
        access.web(warehouse,"",false);return wrapper(store.forOrder(order,warehouse));
    }
    @Transactional(readOnly=true) public JsonNode pda(UUID assignment) {
        access.pda(assignment,"",false);return wrapper(store.forAssignment(assignment));
    }
    private JsonNode wrapper(UUID id) { var result=object();result.set("request",id==null?com.fasterxml.jackson.databind.node.NullNode.instance:store.view(store.get(id)));return result; }
    public record Context(UUID warehouse,String epoch,String key) {}
}
