package com.acrylic.outbound.pc;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import com.acrylic.outbound.orders.*;
import com.acrylic.outbound.pda.AssignmentStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class PcWorkService {
    private final MutationAccess access;private final OrderWriter orders;private final PcStore store;
    private final PcReceipts receipts;private final JdbcTemplate jdbc;private final OrderReleaseService release;
    public PcWorkService(MutationAccess access,OrderWriter orders,PcStore store,PcReceipts receipts,JdbcTemplate jdbc,OrderReleaseService release) {
        this.access=access;this.orders=orders;this.store=store;this.receipts=receipts;this.jdbc=jdbc;this.release=release;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode start(MutationAccess.Context context,UUID order,PcCommands.Start command) {
        var actor=access.lock(context,"pc:operate").actor();var head=orders.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,actor,new PcReceipts.Payload("START",order,command));
        if(ticket.replay()!=null) return ticket.replay();
        if(store.find(order)!=null || !java.util.Set.of("DRAFT","READY").contains(head.status()))
            throw new BusinessException(409,"ORDER_ASSIGNED","订单已进入其他作业或处理阶段，请核对原作业归属");
        if(!head.rowVersion().equals(command.expectedVersion())) throw new BusinessException(409,"ORDER_VERSION_CONFLICT","订单版本已变化，请重新核对");
        if(head.status().equals("DRAFT")) {
            if(!command.policyVersion().matches("[1-9][0-9]{0,17}") || command.reason().isBlank())
                throw new BusinessException(400,"INVALID_REQUEST","请核对出库政策并填写发布说明");
            release.publish(context,order,new OrderReleaseService.Command(command.expectedVersion(),command.policyVersion(),command.reason()));
        }
        var publication=new AssignmentStore(jdbc).publication(order,head.revision());
        var baseline=access.lock(context,"pc:operate").baseline();
        if(!publication.recoveryEpoch().equals(context.epoch()) || !publication.snapshotId().equals(baseline.activeSnapshotId()) || !publication.mappingRevision().equals(baseline.mappingRevision()))
            throw new BusinessException(409,"ORDER_BASELINE_CHANGED","库存、映射或后台恢复代次已变化，请修订并重新发布订单");
        UUID id=Ids.next();jdbc.update("""
            INSERT INTO pc_work(id,warehouse_id,order_id,revision,publication_id,operator_id,recovery_epoch,status,writer_id,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,'ACTIVE',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(context.warehouse()),Ids.bytes(order),head.revision(),Ids.bytes(UUID.fromString(publication.id())),Ids.bytes(actor),Long.parseLong(context.epoch()),Ids.bytes(command.writerId()));
        var work=store.required(order);receipts.transition(work,"ASSIGNED",actor,context.requestId());
        return receipts.save(ticket,context,result(work));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode writer(MutationAccess.Context context,UUID order,PcCommands.Start command) {
        var actor=access.lock(context,"pc:operate").actor();orders.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,actor,new PcReceipts.Payload("WRITER",order,command));
        if(ticket.replay()!=null) return ticket.replay();
        var work=store.required(order);store.owner(work,actor);store.version(work,command.expectedVersion());
        if(!work.status().equals("ACTIVE") || !work.epoch().equals(context.epoch())) throw new BusinessException(409,"PC_WORK_FROZEN","本单当前不能继续录入，请先核对结单或恢复状态");
        jdbc.update("UPDATE pc_work SET writer_id=?,writer_fence=writer_fence+1,row_version=row_version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(command.writerId()),Ids.bytes(work.id()));
        return receipts.save(ticket,context,result(store.required(order)));
    }
    static JsonNode result(PcStore.Work work) {
        return object().put("workId",work.id().toString()).put("version",work.version()).put("status",work.status()).put("fence",work.fence());
    }
}
