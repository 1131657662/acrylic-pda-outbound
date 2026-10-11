package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.orders.OrderWriter;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RecallFaultState {
    private final JdbcTemplate jdbc;
    private final SubmissionStore submissions;
    private final SubmissionReceipts receipts;
    private final RecallProofs proofs;
    private final RecallEvents events;
    private final RecallFaultReset reset;
    private final RecallFaultStock stock;

    public RecallFaultState(JdbcTemplate jdbc,SubmissionStore submissions,SubmissionReceipts receipts,RecallProofs proofs,RecallEvents events,RecallFaultReset reset,RecallFaultStock stock) {
        this.jdbc=jdbc;this.submissions=submissions;this.receipts=receipts;this.proofs=proofs;this.events=events;this.reset=reset;this.stock=stock;
    }

    public UUID requireUnfinished(OrderWriter.Head head) {
        if(!Set.of("ASSIGNED","REVIEW_REQUIRED","RECALL_PENDING").contains(head.status()))
            throw new BusinessException(409,"ORDER_STATE_CONFLICT","仅已领取未完成订单可故障撤回，成功订单须办理更正");
        int valid=jdbc.queryForObject("""
            SELECT COUNT(*) FROM outbound_order o WHERE o.id=? AND o.source='WEB' AND o.current_result_version IS NULL
              AND NOT EXISTS(SELECT 1 FROM dispatch_record d WHERE d.order_id=o.id)
              AND NOT EXISTS(SELECT 1 FROM submission_scope s WHERE s.canonical_order_id=o.id AND s.source_order_id IS NULL)
            """,Integer.class,Ids.bytes(head.id()));
        if(valid!=1)throw new BusinessException(409,"FAULT_WITHDRAWAL_RECONCILIATION_REQUIRED","原订单结果或来源需先核对，不能故障撤回");
        var a=new AssignmentStore(jdbc).active(head.id());
        require(a!=null,"缺少原活动领取");
        return UUID.fromString(a.id());
    }

    public void ensureScope(UUID warehouse,UUID order) {
        jdbc.update("""
            INSERT INTO submission_scope(id,warehouse_id,source_order_id)
            SELECT ?,warehouse_id,id FROM outbound_order WHERE id=? AND warehouse_id=? AND source='WEB'
            ON DUPLICATE KEY UPDATE source_order_id=submission_scope.source_order_id
            """,Ids.bytes(Ids.next()),Ids.bytes(order),Ids.bytes(warehouse));
    }

    public JsonNode withdraw(Work w,JsonNode command) {
        var checked=stock.review(w,command);
        abort(w);
        var h=new RecallStore.Head(w.id(),w.context().warehouse(),w.order().id(),w.assignment(),w.order().revision(),
            w.context().epoch(),"WITHDRAWN",w.reason(),null,null);
        var proof=proofs.fault(h,w.actor(),checked);
        save(w,h,proof);
        jdbc.update("UPDATE order_assignment SET status='REVOKED' WHERE id=?",Ids.bytes(w.assignment()));
        jdbc.update("UPDATE offline_authorization SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(3)) WHERE assignment_id=?",Ids.bytes(w.assignment()));
        stock.release(w,checked);
        reset.prepare(w);
        events.transition(h,w.actor(),"ORDER_FAULT_WITHDRAWN","DRAFT");
        return proof;
    }

    public UUID requestId(UUID assignment) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(r.id),r.status='REQUESTED' AND r.stop_receipt IS NULL
              AND NOT EXISTS(SELECT 1 FROM recovery_package p WHERE p.request_id=r.id)
              AND NOT EXISTS(SELECT 1 FROM order_continuation c WHERE c.request_id=r.id)
            FROM order_change_request r WHERE r.assignment_id=? FOR UPDATE
            """,(rs,n)->new ExistingRequest(UUID.fromString(rs.getString(1)),rs.getBoolean(2)),Ids.bytes(assignment));
        if(rows.isEmpty())return Ids.next();
        var existing=rows.getFirst();
        if(!existing.unanswered())
            throw new BusinessException(409,"RECALL_ALREADY_EXISTS","原领取已有停止、恢复或续作证据，请保留并核对原申请");
        return existing.id();
    }

    private void save(Work w,RecallStore.Head h,JsonNode proof) {
        int resolved=jdbc.update("""
            UPDATE order_change_request SET status='WITHDRAWN',resolution_receipt=?,resolved_by=?,resolved_at=UTC_TIMESTAMP(3)
            WHERE id=? AND assignment_id=? AND status='REQUESTED' AND stop_receipt IS NULL
            """,canonical(proof),Ids.bytes(w.actor()),Ids.bytes(w.id()),Ids.bytes(w.assignment()));
        if(resolved==1)return;
        jdbc.update("""
            INSERT INTO order_change_request(id,warehouse_id,order_id,assignment_id,revision,recovery_epoch,status,reason,
              requested_by,resolution_receipt,resolved_by,created_at,resolved_at)
            VALUES(?,?,?,?,?,?,'WITHDRAWN',?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """,Ids.bytes(w.id()),Ids.bytes(h.warehouse()),Ids.bytes(h.order()),Ids.bytes(h.assignment()),h.revision(),
            Long.parseLong(h.epoch()),w.reason(),Ids.bytes(w.actor()),canonical(proof),Ids.bytes(w.actor()));
    }

    private void abort(Work w) {
        var ids=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM submission WHERE assignment_id=? ORDER BY id FOR UPDATE",String.class,Ids.bytes(w.assignment()));
        for(String id:ids) {
            var s=submissions.required(UUID.fromString(id));
            if(s.status().equals("SUCCEEDED"))throw new BusinessException(409,"ORDER_STATE_CONFLICT","已成功批次须办理更正");
            if(Set.of("ABORTED","REJECTED").contains(s.status()))continue;
            var receipt=receipts.abort(s,submissions.payloadHash(s.control().id()),"后台故障撤回，原领取失效，原记录保留");
            submissions.state(s.control().id(),"ABORTED",null,canonical(receipt));
        }
    }

    public record Work(UUID id,RecallRequests.Context context,OrderWriter.Head order,UUID assignment,UUID actor,String reason) { }
    private record ExistingRequest(UUID id,boolean unanswered) { }
}
