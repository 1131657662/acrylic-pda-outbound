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
    private final JdbcTemplate jdbc;private final OrderWriter orders;
    public SubmissionStore(JdbcTemplate jdbc,OrderWriter orders) { this.jdbc=jdbc;this.orders=orders; }
    private static final String SELECT="""
        SELECT BIN_TO_UUID(s.id),BIN_TO_UUID(c.source_order_id),BIN_TO_UUID(s.assignment_id),BIN_TO_UUID(s.freeze_device_id),
         s.work_round,s.source_recovery_epoch,BIN_TO_UUID(s.operator_id),BIN_TO_UUID(c.warehouse_id),s.status,s.receipt_text,s.issues_json
        FROM submission s INNER JOIN submission_scope c ON c.id=s.scope_id
        """;
    public Head find(UUID id) {
        var rows=jdbc.query(SELECT+" WHERE s.id=?",(rs,n)->new Head(new SubmissionControl(UUID.fromString(rs.getString(1)),
            UUID.fromString(rs.getString(2)),UUID.fromString(rs.getString(3)),UUID.fromString(rs.getString(4)),rs.getLong(5),rs.getString(6)),
            UUID.fromString(rs.getString(7)),UUID.fromString(rs.getString(8)),rs.getString(9),rs.getString(10),rs.getString(11)),Ids.bytes(id));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Head required(UUID id) { var head=find(id);if(head==null) throw SubmissionAccess.missing();return head; }
    public Head lock(SubmissionControl c,SubmissionAccess.Actor actor) {
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
        if(!order.status().equals("ASSIGNED")) throw new BusinessException(409,"ORDER_STATE_CONFLICT","当前订单不可创建新提交");
        jdbc.update("""
            INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,status,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,'REGISTERED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """,Ids.bytes(c.id()),Ids.bytes(scope),Ids.bytes(c.assignment()),Ids.bytes(c.device()),Ids.bytes(actor.user()),c.round(),Long.parseLong(c.epoch()));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?",Ids.bytes(c.id()),Ids.bytes(scope));
        jdbc.update("INSERT INTO order_submission_slot VALUES(?,?,?,?,'FROZEN')",Ids.bytes(c.order()),Ids.bytes(scope),c.round(),Ids.bytes(c.id()));
        return required(c.id());
    }
    public Head lockExisting(Head head) {
        var c=head.control();jdbc.queryForList("SELECT id FROM submission_scope WHERE source_order_id=? FOR UPDATE",Ids.bytes(c.order()));
        orders.lock(head.warehouse(),c.order());jdbc.queryForList("SELECT id FROM submission WHERE id=? FOR UPDATE",Ids.bytes(c.id()));
        return required(c.id());
    }
    public void current(Head head) {
        var c=head.control();int count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM order_submission_slot t INNER JOIN submission_scope c ON c.id=t.scope_id
            WHERE t.order_id=? AND t.current_submission_id=? AND c.current_submission_id=t.current_submission_id AND t.work_round=? AND c.work_round=t.work_round
            """,Integer.class,Ids.bytes(c.order()),Ids.bytes(c.id()),c.round());
        if(count!=1) throw new BusinessException(409,"SUBMISSION_SUPERSEDED","此批次不是订单当前提交，请保留记录并核对");
    }
    public String payloadHash(UUID id) {
        var hashes=jdbc.queryForList("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?",String.class,Ids.bytes(id));return hashes.isEmpty()?null:hashes.getFirst();
    }
    public void payload(Head head,JsonNode body,String hash,int bytes) {
        jdbc.update("INSERT INTO submission_payload VALUES(?,2,?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(head.control().id()),hash,"submissions/"+hash+".json",bytes,
            number(body,"expectedCount"),number(body,"expectedWeightGrams"),number(body,"captureHighWatermark"));
    }
    public void state(UUID id,String status,JsonNode issues,String receipt) {
        jdbc.update("UPDATE submission SET status=?,issues_json=?,receipt_text=?,updated_at=UTC_TIMESTAMP(3) WHERE id=?",status,
            issues==null?null:canonical(issues),receipt,Ids.bytes(id));
    }
    public record Head(SubmissionControl control,UUID actor,UUID warehouse,String status,String receipt,String issues) { }
}
