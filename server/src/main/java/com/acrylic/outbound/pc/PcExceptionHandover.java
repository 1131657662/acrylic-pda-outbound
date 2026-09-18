package com.acrylic.outbound.pc;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import com.acrylic.outbound.orders.OrderWriter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.pc.PcExceptionChecks.*;

@Service
public class PcExceptionHandover {
    private final MutationAccess access;private final OrderWriter orders;private final PcStore works;private final PcReceipts receipts;
    private final PcExceptionChecks checks;private final PcExceptionStock stock;private final JdbcTemplate jdbc;
    public PcExceptionHandover(MutationAccess access,OrderWriter orders,PcStore works,PcReceipts receipts,PcExceptionChecks checks,PcExceptionStock stock,JdbcTemplate jdbc) {
        this.access=access;this.orders=orders;this.works=works;this.receipts=receipts;this.checks=checks;this.stock=stock;this.jdbc=jdbc;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode apply(MutationAccess.Context context,UUID order,PcExceptionCommands.Apply command) {
        var actor=access.lock(context,"pc:operate").actor();orders.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,actor,new PcReceipts.Payload("EXCEPTION_APPLY",order,command));if(ticket.replay()!=null)return ticket.replay();
        var work=works.required(order);checks.epoch(work,context);works.version(work,command.expectedVersion());physical(command.physicalConfirmed());
        var plan=checks.plan(work,command.planId(),command.payloadSha256());
        check(plan.status().equals("APPROVED"),"PC_PLAN_NOT_APPROVED","请先取得主管批准，再由指定人员确认交接");
        check(uuid(plan.body(),"targetUserId").equals(actor),"PC_HANDOVER_WRONG_OPERATOR","只有方案指定的接续人员可确认交接");
        checks.operator(work,actor);checks.approver(work,UUID.fromString(plan.approvedBy()),plan.body());
        stock.apply(work,plan);boolean cancel=text(plan.body(),"outcome").equals("CANCEL");
        jdbc.update("""
            UPDATE pc_exception_plan SET status='APPLIED',applied_by=?,applied_at=UTC_TIMESTAMP(3),handover_evidence=? WHERE id=?
            """,Ids.bytes(actor),command.physicalEvidence().strip(),Ids.bytes(plan.id()));
        jdbc.update("UPDATE pc_exception_case SET status='APPLIED' WHERE id=?",Ids.bytes(uuid(plan.body(),"caseId")));
        jdbc.update("UPDATE pc_work SET operator_id=?,work_round=work_round+1,writer_fence=writer_fence+1,writer_id=? WHERE id=?",Ids.bytes(actor),Ids.bytes(Ids.next()),Ids.bytes(work.id()));
        works.advance(work,cancel?"CANCELLED":"ACTIVE");receipts.transition(work,cancel?"CANCELLED":"ASSIGNED",actor,context.requestId());
        return receipts.save(ticket,context,object().put("planId",plan.id().toString()).put("status","APPLIED").put("workStatus",cancel?"CANCELLED":"ACTIVE"));
    }
}
