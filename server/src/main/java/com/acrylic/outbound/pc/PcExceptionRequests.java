package com.acrylic.outbound.pc;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import com.acrylic.outbound.orders.OrderWriter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.pc.PcExceptionChecks.*;

@Service
public class PcExceptionRequests {
    private final MutationAccess access;private final OrderWriter orders;private final PcStore works;private final PcReceipts receipts;
    private final PcExceptionStore store;private final PcExceptionChecks checks;private final JdbcTemplate jdbc;private final JsonText json;private final PcExceptionStock stock;
    public PcExceptionRequests(MutationAccess access,OrderWriter orders,PcStore works,PcReceipts receipts,
        PcExceptionStore store,PcExceptionChecks checks,JdbcTemplate jdbc,JsonText json,PcExceptionStock stock) {
        this.access=access;this.orders=orders;this.works=works;this.receipts=receipts;this.store=store;this.checks=checks;this.jdbc=jdbc;this.json=json;this.stock=stock;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode stop(MutationAccess.Context context,UUID order,PcExceptionCommands.Stop command) {
        var actor=access.lock(context,"order:recall").actor();orders.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,actor,new PcReceipts.Payload("EXCEPTION_STOP",order,command));if(ticket.replay()!=null)return ticket.replay();
        var work=works.required(order);checks.epoch(work,context);works.version(work,command.expectedVersion());physical(command.physicalConfirmed());
        check(Set.of("ACTIVE","REVIEW_REQUIRED","REJECTED").contains(work.status()),"PC_EXCEPTION_STATE","只能停止尚未完成的PC作业，请核对原处置记录");
        UUID id=Ids.next();store.saveCase(work,new PcExceptionStore.Stopped(id,checks.snapshot(work),command.reason()),actor);
        jdbc.update("UPDATE pc_submission SET status='ABORTED' WHERE work_id=? AND status='REVIEW_REQUIRED'",Ids.bytes(work.id()));
        jdbc.update("UPDATE pc_work SET writer_fence=writer_fence+1,writer_id=? WHERE id=?",Ids.bytes(Ids.next()),Ids.bytes(work.id()));
        works.advance(work,"STOPPED");receipts.transition(work,"RECALL_PENDING",actor,context.requestId());
        return receipts.save(ticket,context,object().put("caseId",id.toString()).put("status","STOPPED"));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode propose(MutationAccess.Context context,UUID order,PcExceptionCommands.Plan command) {
        var actor=access.lock(context,"order:resume").actor();orders.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,actor,new PcReceipts.Payload("EXCEPTION_PLAN",order,command));if(ticket.replay()!=null)return ticket.replay();
        var work=works.required(order);checks.epoch(work,context);works.version(work,command.expectedVersion());physical(command.physicalConfirmed());
        var stopped=checks.stopped(work,command.caseId());var previous=store.current(stopped);
        check(previous==null || previous.status().equals("REJECTED"),"PC_PLAN_PENDING","已有待处理或已批准方案，请先核对或退回原方案");
        check(stopped.snapshotSha256().equals(command.snapshotSha256()),"PC_STOP_HASH_MISMATCH","停止记录不一致，请重新核对");
        checks.lines(work,command);var target=checks.target(work,command.targetUsername());
        var body=object().put("caseId",stopped.id().toString()).put("snapshotSha256",stopped.snapshotSha256())
            .put("targetUserId",target.id().toString()).put("targetUsername",target.username()).put("targetName",target.name())
            .put("outcome",command.outcome()).put("reason",command.reason().strip());body.set("lines",parse(json.encode(command.lines())));
        UUID id=savePlan(stopped,body,actor);works.advance(work,"STOPPED");
        return receipts.save(ticket,context,object().put("planId",id.toString()).put("payloadSha256",sha(body)).put("status","PENDING"));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode review(MutationAccess.Context context,UUID order,PcExceptionCommands.Decision command) {
        var actor=access.lock(context,"order:resume").actor();orders.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,actor,new PcReceipts.Payload("EXCEPTION_REVIEW",order,command));if(ticket.replay()!=null)return ticket.replay();
        var work=works.required(order);checks.epoch(work,context);works.version(work,command.expectedVersion());physical(command.physicalConfirmed());
        var plan=checks.plan(work,command.planId(),command.payloadSha256());boolean approve=command.decision().equals("APPROVE");
        check(plan.status().equals("PENDING") || (!approve && plan.status().equals("APPROVED")),"PC_PLAN_REVIEW_STATE","此方案不能重复审批，请核对原决定");
        if(approve) { checks.approver(work,actor,plan.body());checks.operator(work,uuid(plan.body(),"targetUserId"));stock.validate(work,plan); }
        String status=approve?"APPROVED":"REJECTED";
        jdbc.update("UPDATE pc_exception_plan SET status=?,approved_by=? WHERE id=?",status,approve?Ids.bytes(actor):null,Ids.bytes(plan.id()));
        long permissionVersion=jdbc.queryForObject("SELECT auth_version FROM app_user WHERE id=?",Long.class,Ids.bytes(actor));
        jdbc.update("INSERT INTO pc_exception_review VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(Ids.next()),Ids.bytes(plan.id()),command.decision(),command.reason().strip(),Ids.bytes(actor),permissionVersion);
        works.advance(work,"STOPPED");return receipts.save(ticket,context,object().put("planId",plan.id().toString()).put("status",status));
    }
    private UUID savePlan(PcExceptionStore.Case stopped,JsonNode body,UUID actor) {
        UUID id=Ids.next();jdbc.update("""
            INSERT INTO pc_exception_plan(id,case_id,target_user_id,outcome,status,reason,payload_text,payload_sha256,requested_by,requested_at)
            VALUES(?,?,?,?,'PENDING',?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(stopped.id()),Ids.bytes(uuid(body,"targetUserId")),text(body,"outcome"),text(body,"reason"),canonical(body),sha(body),Ids.bytes(actor));return id;
    }
}
