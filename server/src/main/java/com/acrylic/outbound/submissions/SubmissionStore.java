package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.orders.OrderWriter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class SubmissionStore {
    private final JdbcTemplate jdbc;private final OrderWriter orders;private final com.acrylic.outbound.temporary.TemporarySubmissionControls temporary;
    public SubmissionStore(JdbcTemplate jdbc,OrderWriter orders,com.acrylic.outbound.temporary.TemporarySubmissionControls temporary) { this.jdbc=jdbc;this.orders=orders;this.temporary=temporary; }
    private static final String SELECT="""
        SELECT BIN_TO_UUID(s.id),BIN_TO_UUID(c.source_order_id),BIN_TO_UUID(s.assignment_id),BIN_TO_UUID(s.freeze_device_id),
         s.work_round,s.source_recovery_epoch,BIN_TO_UUID(s.operator_id),BIN_TO_UUID(c.warehouse_id),s.status,s.receipt_text,s.issues_json,BIN_TO_UUID(s.continuation_id),
         BIN_TO_UUID(c.creator_device_id),BIN_TO_UUID(c.client_order_id),BIN_TO_UUID(s.temporary_authorization_id),BIN_TO_UUID(c.canonical_order_id)
        FROM submission s INNER JOIN submission_scope c ON c.id=s.scope_id
        """;
    public Head find(UUID id) {
        var rows=jdbc.query(SELECT+" WHERE s.id=?",(rs,n)->new Head(new SubmissionControl(UUID.fromString(rs.getString(1)),
            nullable(rs.getString(2)),nullable(rs.getString(3)),UUID.fromString(rs.getString(4)),rs.getLong(5),rs.getString(6),nullable(rs.getString(12)),
            rs.getString(13)==null?null:new SubmissionControl.Temporary(nullable(rs.getString(13)),nullable(rs.getString(14)),nullable(rs.getString(15)))),
            UUID.fromString(rs.getString(7)),UUID.fromString(rs.getString(8)),rs.getString(9),rs.getString(10),rs.getString(11),nullable(rs.getString(16))),Ids.bytes(id));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Head required(UUID id) { var head=find(id);if(head==null) throw SubmissionAccess.missing();return head; }
    public Head lock(SubmissionControl c,SubmissionAccess.Actor actor) {
        if(c.temporary()!=null) {
            temporary.lock(c,actor);var head=required(c.id());equal(head.control(),c,"同一提交ID的来源不可改变");equal(head.actor(),actor.user(),"原提交人员不匹配");return head;
        }
        var scopes=jdbc.query("SELECT BIN_TO_UUID(id) FROM submission_scope WHERE source_order_id=? FOR UPDATE",(rs,n)->UUID.fromString(rs.getString(1)),Ids.bytes(c.order()));
        UUID scope;
        if(scopes.isEmpty()) { scope=Ids.next();jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id) VALUES(?,?,?)",Ids.bytes(scope),Ids.bytes(actor.warehouse()),Ids.bytes(c.order())); }
        else scope=scopes.getFirst();
        var order=orders.lock(actor.warehouse(),c.order());
        jdbc.queryForList("SELECT id FROM submission WHERE id=? FOR UPDATE",Ids.bytes(c.id()));
        var head=find(c.id());
        if(head!=null) { equal(head.control(),c,"同一提交ID的来源不可改变");equal(head.actor(),actor.user(),"原提交人员不匹配");return head; }
        var current=jdbc.queryForObject("SELECT BIN_TO_UUID(current_submission_id) FROM submission_scope WHERE id=?",String.class,Ids.bytes(scope));
        if(current!=null) throw new BusinessException(409,"CURRENT_SUBMISSION_EXISTS","订单已有冻结提交，请查询原批次，不能换键重提");
        int registered=jdbc.queryForObject("SELECT COUNT(*) FROM submission WHERE scope_id=? AND work_round=?",Integer.class,Ids.bytes(scope),c.round());
        if(registered!=0)throw new BusinessException(409,"CURRENT_SUBMISSION_EXISTS","当前轮次已有在途控制头，须恢复原批次");
        long round=jdbc.queryForObject("SELECT work_round FROM submission_scope WHERE id=?",Long.class,Ids.bytes(scope));
        if(round!=c.round())throw new BusinessException(409,"WORK_ROUND_CONFLICT","提交轮次已过期");
        if(c.continuation()!=null) {
            int valid=jdbc.queryForObject("SELECT COUNT(*) FROM order_continuation WHERE id=? AND scope_id=? AND new_assignment_id=? AND to_round=? AND status='ACTIVATED'",
                Integer.class,Ids.bytes(c.continuation()),Ids.bytes(scope),Ids.bytes(c.assignment()),c.round());
            if(valid!=1)throw new BusinessException(409,"WORK_ROUND_CONFLICT","续作票据无效或尚未激活");
        }
        if(!Set.of("ASSIGNED","RECALL_PENDING","CONTINUING").contains(order.status())) throw new BusinessException(409,"ORDER_STATE_CONFLICT","当前订单不可创建新提交");
        jdbc.update("""
            INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,status,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,'REGISTERED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """,Ids.bytes(c.id()),Ids.bytes(scope),Ids.bytes(c.assignment()),Ids.bytes(c.device()),Ids.bytes(actor.user()),c.round(),Long.parseLong(c.epoch()));
        if(c.continuation()==null) {
            jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?",Ids.bytes(c.id()),Ids.bytes(scope));
            jdbc.update("INSERT INTO order_submission_slot(order_id,scope_id,work_round,current_submission_id,phase) VALUES(?,?,?,?,'FROZEN')",Ids.bytes(c.order()),Ids.bytes(scope),c.round(),Ids.bytes(c.id()));
        }
        else {
            jdbc.update("UPDATE submission SET continuation_id=? WHERE id=?",Ids.bytes(c.continuation()),Ids.bytes(c.id()));
        }
        return required(c.id());
    }
    public Head lockExisting(Head head) {
        var c=head.control();jdbc.queryForList("SELECT id FROM submission_scope WHERE id=(SELECT scope_id FROM submission WHERE id=?) FOR UPDATE",Ids.bytes(c.id()));
        head=required(c.id());
        if(head.order()!=null)orders.lock(head.warehouse(),head.order());jdbc.queryForList("SELECT id FROM submission WHERE id=? FOR UPDATE",Ids.bytes(c.id()));
        return required(c.id());
    }
    public void current(Head head) {
        if(head.control().temporary()!=null && head.order()==null) {
            int active=jdbc.queryForObject("SELECT COUNT(*) FROM submission_scope c INNER JOIN submission s ON s.scope_id=c.id WHERE s.id=? AND c.current_submission_id=s.id AND c.work_round=s.work_round",
                Integer.class,Ids.bytes(head.control().id()));
            if(active!=1)throw new BusinessException(409,"SUBMISSION_SUPERSEDED","原临时批次已被替代");return;
        }
        var c=head.control();int count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM order_submission_slot t INNER JOIN submission_scope c ON c.id=t.scope_id
            WHERE t.order_id=? AND t.current_submission_id=? AND c.current_submission_id=t.current_submission_id AND t.work_round=? AND c.work_round=t.work_round
            """,Integer.class,Ids.bytes(head.order()),Ids.bytes(c.id()),c.round());
        if(count!=1 && !pendingContinuation(head)) throw new BusinessException(409,"SUBMISSION_SUPERSEDED","此批次不是订单当前提交，请保留记录并核对");
    }
    private boolean pendingContinuation(Head h) {
        if(h.control().continuation()==null || !Set.of("REGISTERED","RECEIVING").contains(h.status()))return false;
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM order_submission_slot t INNER JOIN submission_scope c ON c.id=t.scope_id
            WHERE t.order_id=? AND t.work_round=? AND c.work_round=t.work_round AND t.current_submission_id IS NULL
            AND c.current_submission_id IS NULL AND t.phase='CONTINUING'
            """,Integer.class,Ids.bytes(h.control().order()),h.control().round())==1;
    }
    public void select(Head h) {
        if(h.control().continuation()==null)return;current(h);var c=h.control();
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE source_order_id=? AND work_round=?",Ids.bytes(c.id()),Ids.bytes(c.order()),c.round());
        jdbc.update("UPDATE order_submission_slot SET current_submission_id=?,phase='FROZEN',slot_version=slot_version+1 WHERE order_id=? AND work_round=?",Ids.bytes(c.id()),Ids.bytes(c.order()),c.round());
    }
    public String payloadHash(UUID id) {
        var hashes=jdbc.queryForList("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?",String.class,Ids.bytes(id));return hashes.isEmpty()?null:hashes.getFirst();
    }
    public void requireUploadHash(UUID id,String hash) {
        var declared=jdbc.queryForList("SELECT payload_sha256 FROM submission_upload WHERE submission_id=?",String.class,Ids.bytes(id));
        if(!declared.isEmpty() && !declared.getFirst().equals(hash))
            throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","正文与原分块清单不一致，请恢复原正文");
    }
    public void requireBeforeRecovery(UUID assignment) {
        int received=jdbc.queryForObject("SELECT COUNT(*) FROM recovery_package p INNER JOIN order_change_request r ON r.id=p.request_id WHERE r.assignment_id=?",Integer.class,Ids.bytes(assignment));
        if(received>0)throw new BusinessException(409,"RECOVERY_ALREADY_FIXED","原机恢复证据已固定，请查询召回；不可新增旧提交正文");
    }
    public void payload(Head head,JsonNode body,String hash,int bytes) {
        jdbc.update("INSERT INTO submission_payload VALUES(?,2,?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(head.control().id()),hash,"submissions/"+hash+".json",bytes,
            number(body,"expectedCount"),number(body,"expectedWeightGrams"),number(body,"captureHighWatermark"));
    }
    public void state(UUID id,String status,JsonNode issues,String receipt) {
        jdbc.update("UPDATE submission SET status=?,issues_json=?,receipt_text=?,updated_at=UTC_TIMESTAMP(3) WHERE id=?",status,
            issues==null?null:canonical(issues),receipt,Ids.bytes(id));
    }
    private static UUID nullable(String id) { return id==null?null:UUID.fromString(id); }
    public record Head(SubmissionControl control,UUID actor,UUID warehouse,String status,String receipt,String issues,UUID target) {
        public UUID order() { return control.temporary()==null?control.order():target; }
    }
}
