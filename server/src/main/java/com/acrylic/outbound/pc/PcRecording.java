package com.acrylic.outbound.pc;

import com.acrylic.outbound.sync.BarcodeChanges;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import com.acrylic.outbound.orders.OrderWriter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class PcRecording {
    private final MutationAccess access;private final OrderWriter orders;private final PcStore store;private final PcReceipts receipts;
    private final PcEntries entries;private final PcStock stock;private final JdbcTemplate jdbc;
    public PcRecording(MutationAccess access,OrderWriter orders,PcStore store,PcReceipts receipts,PcEntries entries,PcStock stock,JdbcTemplate jdbc) {
        this.access=access;this.orders=orders;this.store=store;this.receipts=receipts;this.entries=entries;this.stock=stock;this.jdbc=jdbc;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode record(MutationAccess.Context context,UUID order,Input input) {
        var actor=access.lock(context,"pc:operate").actor();orders.lock(context.warehouse(),order);
        var ticket=receipts.prepare(context,actor,new PcReceipts.Payload(input.action(),order,input.command()));
        if(ticket.replay()!=null) return ticket.replay();
        var work=store.required(order);store.writer(work,actor,input.command());
        if(!work.epoch().equals(context.epoch())) throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","原作业来自旧恢复代次，需保留记录并对账");
        UUID entry=input.action().equals("ADD")?add(work,actor,input.command()):remove(work,actor,input.command());
        store.advance(work,"ACTIVE");var result=(com.fasterxml.jackson.databind.node.ObjectNode)PcWorkService.result(store.required(order));
        result.put("entryId",entry.toString());return receipts.save(ticket,context,result);
    }
    private UUID add(PcStore.Work work,UUID actor,PcCommands.Write command) {
        if(!command.physicalConfirmed()) throw new BusinessException(400,"PC_PHYSICAL_CHECK_REQUIRED","请核对实物后确认计入本包");
        var bale=stock.fixed(work,command.barcode());String conflict=stock.conflict(work,bale,false);
        if(conflict!=null) throw new BusinessException(409,"PC_BALE_UNAVAILABLE","本包未计入："+conflict);
        var product=entries.products(work).stream().filter(p->p.deliveryCode().equals(bale.code())).findFirst().orElseThrow();
        if(product.actualCount()>=product.plannedCount()) throw new BusinessException(409,"PC_PLAN_FULL","本包未计入，该产品已达到计划数量");
        UUID id=Ids.next();jdbc.update("""
            INSERT INTO pc_entry(id,work_id,bale_id,sequence_no,dispatch_cycle,status,delivery_code,weight_g,raw_weight,model_snapshot,name_snapshot,created_by,operator_name,created_at)
            VALUES(?,?,?,?,?,'ACTIVE',?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(work.id()),Ids.bytes(bale.id()),work.nextSequence(),bale.cycle(),bale.code(),bale.weight(),bale.rawWeight(),bale.model(),bale.name(),Ids.bytes(actor),work.operatorName());
        jdbc.update("INSERT INTO bale_commitment(bale_id,order_id,pc_work_id,dispatch_cycle,state,created_at) VALUES(?,?,?,?,'PC_RESERVED',UTC_TIMESTAMP(3))",
            Ids.bytes(bale.id()),Ids.bytes(work.order()),Ids.bytes(work.id()),bale.cycle());
        BarcodeChanges.bale(jdbc,bale.id());
        jdbc.update("UPDATE pc_work SET next_sequence=next_sequence+1 WHERE id=?",Ids.bytes(work.id()));return id;
    }
    private UUID remove(PcStore.Work work,UUID actor,PcCommands.Write command) {
        if(!command.physicalConfirmed() || command.reason().isBlank()) throw new BusinessException(400,"PC_PHYSICAL_CHECK_REQUIRED","请核对本包实物并填写撤销原因");
        var entry=entries.required(work,command.entryId());if(!entry.status().equals("ACTIVE")) throw new BusinessException(409,"PC_ENTRY_VOIDED","此记录已撤销，请核对原结果");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM pc_submission_entry WHERE entry_id=? AND frozen_status='ACTIVE'",Integer.class,Ids.bytes(command.entryId()))>0)
            throw new BusinessException(409,"PC_FROZEN_ENTRY","此包属于原冻结提交，仍须保留；不能通过普通撤销办理卸货");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM pc_retained_entry WHERE entry_id=?",Integer.class,Ids.bytes(command.entryId()))>0)
            throw new BusinessException(409,"PC_RETAINED_ENTRY","此包已经实物交接保留，不能普通撤销；请由主管核验卸货处置");
        jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE",Long.class,Ids.bytes(UUID.fromString(entry.baleId())));
        int removed=jdbc.update("DELETE FROM bale_commitment WHERE bale_id=? AND pc_work_id=? AND state='PC_RESERVED' AND dispatch_cycle=?",
            Ids.bytes(UUID.fromString(entry.baleId())),Ids.bytes(work.id()),entry.cycle());
        if(removed!=1) throw new BusinessException(409,"PC_COMMITMENT_CONFLICT","原占用已变化，未撤销，请联系主管核对");
        jdbc.update("UPDATE pc_entry SET status='VOIDED',voided_by=?,voided_at=UTC_TIMESTAMP(3),void_reason=? WHERE id=?",Ids.bytes(actor),command.reason().strip(),Ids.bytes(command.entryId()));
        BarcodeChanges.bale(jdbc,UUID.fromString(entry.baleId()));
        return command.entryId();
    }
    public record Input(String action,PcCommands.Write command) { }
}
