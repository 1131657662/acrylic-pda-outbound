package com.acrylic.outbound.pc;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import com.acrylic.outbound.orders.OrderWriter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class PcFinalization {
    private final MutationAccess access;private final OrderWriter orders;private final PcStore store;private final PcReceipts receipts;
    private final PcEntries entries;private final PcStock stock;private final PcSubmissions submissions;private final PcAccounting accounting;
    private final JdbcTemplate jdbc;private final JsonText json;private final PcIdentityLocks identities;
    public PcFinalization(MutationAccess access,OrderWriter orders,PcStore store,PcReceipts receipts,PcEntries entries,PcStock stock,
        PcSubmissions submissions,PcAccounting accounting,JdbcTemplate jdbc,JsonText json,PcIdentityLocks identities) {
        this.access=access;this.orders=orders;this.store=store;this.receipts=receipts;this.entries=entries;this.stock=stock;
        this.submissions=submissions;this.accounting=accounting;this.jdbc=jdbc;this.json=json;this.identities=identities;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode freeze(MutationAccess.Context context,UUID order,PcCommands.Write command) {
        var actor=access.lock(context,"pc:operate").actor();orders.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,actor,new PcReceipts.Payload("SUBMIT",order,command));if(ticket.replay()!=null)return ticket.replay();
        var work=store.required(order);store.writer(work,actor,command);epoch(work,context);
        if(!command.physicalConfirmed()) throw new BusinessException(400,"PC_PHYSICAL_CHECK_REQUIRED","请核对本单实物后提交结单");
        var rows=entries.all(work);var products=entries.products(work);long count=products.stream().mapToLong(PcEntries.Product::actualCount).sum();
        if(count==0) throw new BusinessException(409,"PC_EMPTY_SUBMISSION","本单没有有效录入，不能提交出库");
        if(products.stream().anyMatch(p->p.actualCount()>p.plannedCount())) throw new BusinessException(409,"PC_PLAN_EXCEEDED","产品数量超计划，不能结单");
        boolean shortShipment=products.stream().anyMatch(p->p.actualCount()<p.plannedCount());
        if(shortShipment && command.reason().isBlank()) throw new BusinessException(400,"PC_SHORT_REASON_REQUIRED","请填写短装原因，提交后需主管审核");
        var issues=stock.review(work,rows);if(shortShipment) issues.add(object().put("code","SHORT_SHIPMENT_APPROVAL_REQUIRED").put("reason",command.reason().strip()));
        var payload=new Frozen(work.id(),work.order(),work.snapshot(),work.publication(),work.revision(),work.round(),rows,products,command.reason().strip(),new PcExceptionStore(jdbc).dispositions(work));
        String encoded=json.encode(payload);UUID id=Ids.next();String hash=sha(parse(encoded));
        long weight=products.stream().mapToLong(p->Long.parseLong(p.weightGrams())).reduce(0,Math::addExact);
        submissions.save(work,new PcSubmissions.Input(id,hash,encoded,count,weight,command.reason().strip(),issues),actor);
        jdbc.batchUpdate("INSERT INTO pc_submission_entry VALUES(?,?,?,?)",rows,200,(ps,e)->{
            ps.setBytes(1,Ids.bytes(id));ps.setBytes(2,Ids.bytes(UUID.fromString(e.id())));ps.setString(3,e.status());ps.setString(4,json.digest(e));
        });
        store.advance(work,"REVIEW_REQUIRED");
        if(issues.isEmpty()) accounting.commit(work,new PcAccounting.Posting(id,hash,rows,products),actor);
        else receipts.transition(work,"REVIEW_REQUIRED",actor,context.requestId());
        return receipts.save(ticket,context,parse(json.encode(submissions.current(work))));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode review(MutationAccess.Context context,UUID order,PcCommands.Review command) {
        var identity=identities.operator(context,"submission:review",order);var actor=identity.actor();
        var ticket=receipts.prepare(context,actor,new PcReceipts.Payload("REVIEW",order,command));if(ticket.replay()!=null)return ticket.replay();
        identity.recheck();
        var work=store.required(order);epoch(work,context);var current=submissions.current(work);
        if(current==null || !current.status().equals("REVIEW_REQUIRED") || !work.status().equals("REVIEW_REQUIRED")) throw new BusinessException(409,"PC_REVIEW_STATE","当前没有可审批的PC提交");
        if(!command.payloadSha256().equals(current.payloadSha256())) throw new BusinessException(409,"PC_REVIEW_HASH","提交资料已变化，请重新核对当前批次");
        UUID id=UUID.fromString(current.id());var body=parse(submissions.payload(id));var rows=entries.all(work);
        if(!sha(body).equals(current.payloadSha256()) || !body.get("entries").equals(parse(json.encode(rows)))) throw new BusinessException(409,"PC_EVIDENCE_CHANGED","原冻结记录不一致，不能审批");
        var dispositions=new PcExceptionStore(jdbc).dispositions(work);
        if(body.has("dispositions")?!sha(body.get("dispositions")).equals(sha(dispositions)):!dispositions.isEmpty())
            throw new BusinessException(409,"PC_EVIDENCE_CHANGED","原冻结处置证据不一致，不能审批");
        if(command.decision().equals("APPROVE")) {
            requireOperator(work);var issues=stock.review(work,rows);
            if(!issues.isEmpty()) throw new BusinessException(409,"PC_STILL_CONFLICTED","库存或占用冲突尚未解决，不能批准出库");
            accounting.commit(work,new PcAccounting.Posting(id,current.payloadSha256(),rows,entries.products(work)),actor);
        } else {
            jdbc.update("UPDATE pc_submission SET status='REJECTED' WHERE id=?",Ids.bytes(id));store.advance(work,"REJECTED");receipts.transition(work,"REVIEW_REQUIRED",actor,context.requestId());
        }
        long auth=jdbc.queryForObject("SELECT auth_version FROM app_user WHERE id=?",Long.class,Ids.bytes(actor));
        jdbc.update("INSERT INTO pc_review VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),command.decision(),command.reason().strip(),Ids.bytes(actor),auth);
        return receipts.save(ticket,context,parse(json.encode(submissions.current(work))));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode resume(MutationAccess.Context context,UUID order,PcCommands.Resume command) {
        var identity=identities.operator(context,"order:resume",order);var actor=identity.actor();
        var ticket=receipts.prepare(context,actor,new PcReceipts.Payload("RESUME",order,command));if(ticket.replay()!=null)return ticket.replay();
        identity.recheck();
        var work=store.required(order);epoch(work,context);store.version(work,command.expectedVersion());requireOperator(work);
        if(!work.status().equals("REJECTED")) throw new BusinessException(409,"PC_RESUME_STATE","仅主管已退回的PC作业可核验恢复");
        jdbc.update("UPDATE pc_work SET status='ACTIVE',work_round=work_round+1,writer_fence=writer_fence+1,writer_id=?,row_version=row_version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(Ids.next()),Ids.bytes(work.id()));
        receipts.transition(work,"ASSIGNED",actor,context.requestId());
        var result=(com.fasterxml.jackson.databind.node.ObjectNode)PcWorkService.result(store.required(order));result.put("physicalEvidence",command.physicalEvidence().strip());
        return receipts.save(ticket,context,result);
    }
    private void epoch(PcStore.Work work,MutationAccess.Context context) {
        if(!work.epoch().equals(context.epoch())) throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","原作业来自旧恢复代次，请保留记录并先对账");
    }
    private void requireOperator(PcStore.Work work) {
        var identities=new IdentityStore(jdbc);
        boolean active=jdbc.queryForObject("SELECT status='ACTIVE' FROM app_user WHERE id=?",Boolean.class,Ids.bytes(work.operator()));
        if(!active || !identities.permissions(work.operator()).contains("pc:operate") || identities.warehouses(work.operator()).stream().noneMatch(w->w.id().equals(work.warehouse())))
            throw new BusinessException(409,"PC_OPERATOR_UNAVAILABLE","原作业人员授权已变化，请先核对人员与仓库权限");
    }
    private record Frozen(UUID workId,UUID orderId,UUID snapshotId,UUID publicationId,int revision,long workRound,
        List<PcEntries.Entry> entries,List<PcEntries.Product> products,String shortReason,JsonNode dispositions) { }
}
