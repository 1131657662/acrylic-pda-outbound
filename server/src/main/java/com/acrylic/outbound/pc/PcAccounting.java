package com.acrylic.outbound.pc;

import com.acrylic.outbound.sync.BarcodeChanges;
import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class PcAccounting {
    private final JdbcTemplate jdbc;private final PcStore store;private final PcReceipts receipts;
    public PcAccounting(JdbcTemplate jdbc,PcStore store,PcReceipts receipts) { this.jdbc=jdbc;this.store=store;this.receipts=receipts; }
    public JsonNode commit(PcStore.Work work,Posting posting,UUID actor) {
        var rows=posting.entries().stream().filter(e->e.status().equals("ACTIVE")).toList();
        long weight=rows.stream().mapToLong(e->Long.parseLong(e.weightGrams())).reduce(0,Math::addExact);
        var receipt=object().put("channel","PC").put("orderId",work.order().toString()).put("workId",work.id().toString())
            .put("submissionId",posting.submission().toString()).put("status","SUCCEEDED").put("actualCount",Integer.toString(rows.size()))
            .put("weightGrams",Long.toString(weight)).put("inventoryDeducted",work.deduct()).put("payloadSha256",posting.hash()).put("resultVersion","1");
        for(var entry:rows) { dispatch(work,posting.submission(),entry); BarcodeChanges.bale(jdbc,UUID.fromString(entry.baleId())); }
        jdbc.update("INSERT INTO order_result_version(order_id,result_version,pc_submission_id,actual_count,weight_g,inventory_deducted,header_snapshot,created_at) VALUES(?,1,?,?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(work.order()),Ids.bytes(posting.submission()),rows.size(),weight,work.deduct(),canonical(receipt));
        jdbc.batchUpdate("INSERT INTO order_effective_line VALUES(?,1,?,?,?)",posting.products(),200,(ps,p)->{
            ps.setBytes(1,Ids.bytes(work.order()));ps.setString(2,p.deliveryCode());ps.setLong(3,p.actualCount());ps.setLong(4,Long.parseLong(p.weightGrams()));
        });
        jdbc.update("UPDATE outbound_order SET current_result_version=1 WHERE id=?",Ids.bytes(work.order()));
        jdbc.update("UPDATE pc_submission SET status='SUCCEEDED',issues_json=JSON_ARRAY(),receipt_json=? WHERE id=?",canonical(receipt),Ids.bytes(posting.submission()));
        store.advance(work,"SUCCEEDED");receipts.transition(work,"COMPLETED",actor,posting.submission().toString());return receipt;
    }
    private void dispatch(PcStore.Work work,UUID submission,PcEntries.Entry entry) {
        UUID bale=UUID.fromString(entry.baleId()),dispatch=Ids.next();
        int changed=jdbc.update("UPDATE bale_commitment SET state='DISPATCHED' WHERE bale_id=? AND pc_work_id=? AND dispatch_cycle=? AND state='PC_RESERVED'",Ids.bytes(bale),Ids.bytes(work.id()),entry.cycle());
        if(changed!=1) throw new BusinessException(409,"PC_COMMITMENT_CONFLICT","包占用不属于当前PC作业");
        jdbc.update("""
            INSERT INTO dispatch_record(id,pc_submission_id,order_id,pc_entry_id,bale_id,dispatch_cycle,order_scan_seq,delivery_code,weight_g,raw_weight,model_snapshot,name_snapshot,confirmed_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(dispatch),Ids.bytes(submission),Ids.bytes(work.order()),Ids.bytes(UUID.fromString(entry.id())),Ids.bytes(bale),entry.cycle(),Long.parseLong(entry.sequence()),entry.deliveryCode(),Long.parseLong(entry.weightGrams()),entry.rawWeight(),entry.model(),entry.name());
        if(work.deduct()) {
            int updated=jdbc.update("UPDATE inventory_position SET book_status='DEDUCTED',row_version=row_version+1 WHERE bale_id=? AND book_status='NOT_DEDUCTED'",Ids.bytes(bale));
            if(updated!=1) throw new BusinessException(409,"INVENTORY_ALREADY_DEDUCTED","库存账务冲突");
            jdbc.update("INSERT INTO inventory_ledger VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(Ids.next()),Ids.bytes(dispatch),Ids.bytes(bale),-1,-Long.parseLong(entry.weightGrams()));
        }
    }
    public record Posting(UUID submission,String hash,List<PcEntries.Entry> entries,List<PcEntries.Product> products) { }
}
