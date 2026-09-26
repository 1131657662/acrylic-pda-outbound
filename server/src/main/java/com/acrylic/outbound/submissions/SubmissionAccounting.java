package com.acrylic.outbound.submissions;

import com.acrylic.outbound.sync.BarcodeChanges;
import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
@Transactional(propagation=Propagation.MANDATORY)
public class SubmissionAccounting {
    private final JdbcTemplate jdbc;private final SubmissionReceipts receipts;private final SubmissionStore store;private final SubmissionEvents events;
    public SubmissionAccounting(JdbcTemplate jdbc,SubmissionReceipts receipts,SubmissionStore store,SubmissionEvents events) {
        this.jdbc=jdbc;this.receipts=receipts;this.store=store;this.events=events;
    }
    /** Caller owns identity/source/stock locks, fresh eligibility and decisions; this primitive grants no permission. */
    public JsonNode commit(SubmissionStore.Head head,JsonNode body,SubmissionSnapshot.Validated value,UUID actor) {
        require(head.order()!=null && !value.scans().isEmpty() && value.weight()>0,"记账须有当前目标及非空有效明细");
        store.current(head);
        UUID order=head.order();boolean deduct=SubmissionAccountingPolicy.require(jdbc,head,body);
        var receipt=receipts.success(head,body,value,deduct); // Signing must succeed before any business commit.
        for(var scan:value.scans()) { dispatch(head,scan,deduct); BarcodeChanges.bale(jdbc,scan.bale().id()); }
        jdbc.update("INSERT INTO order_result_version(order_id,result_version,submission_id,actual_count,weight_g,inventory_deducted,header_snapshot,created_at) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(order),1,Ids.bytes(head.control().id()),value.scans().size(),value.weight(),deduct,canonical(receipt));
        jdbc.batchUpdate("INSERT INTO order_effective_line VALUES(?,1,?,?,?)",value.products(),200,(ps,p)->{
            ps.setBytes(1,Ids.bytes(order));ps.setString(2,p.code());ps.setLong(3,p.count());ps.setLong(4,p.weight());
        });
        require(jdbc.update("UPDATE outbound_order SET current_result_version=1 WHERE id=? AND current_result_version IS NULL",Ids.bytes(order))==1,"订单已有结果，不能重复记账");
        require(jdbc.update("UPDATE order_submission_slot SET phase='COMPLETED',slot_version=slot_version+1 WHERE order_id=? AND current_submission_id=? AND work_round=? AND phase='FROZEN'",
            Ids.bytes(order),Ids.bytes(head.control().id()),head.control().round())==1,"记账提交槽已变化");
        if(head.control().assignment()!=null)jdbc.update("UPDATE order_assignment SET status='CLOSED' WHERE id=?",Ids.bytes(head.control().assignment()));
        if(head.control().continuation()!=null)jdbc.update("UPDATE order_continuation SET status='COMPLETED' WHERE id=?",Ids.bytes(head.control().continuation()));
        store.state(head.control().id(),"SUCCEEDED",null,canonical(receipt));events.transition(head,"COMPLETED",actor);return receipt;
    }
    private void dispatch(SubmissionStore.Head head,SubmissionSnapshot.Scan scan,boolean deduct) {
        var bale=scan.bale();UUID dispatch=Ids.next();
        int changed=jdbc.update("UPDATE bale_commitment SET state='DISPATCHED' WHERE bale_id=? AND order_id=? AND submission_id=? AND dispatch_cycle=? AND state='REVIEW_RESERVED'",
            Ids.bytes(bale.id()),Ids.bytes(head.order()),Ids.bytes(head.control().id()),scan.cycle());
        if(changed!=1) throw new BusinessException(409,"BALE_COMMITMENT_CONFLICT","包占用不属于当前批次");
        jdbc.update("INSERT INTO dispatch_record(id,submission_id,order_id,capture_id,bale_id,dispatch_cycle,order_scan_seq,delivery_code,weight_g,raw_weight,model_snapshot,name_snapshot,confirmed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(dispatch),Ids.bytes(head.control().id()),Ids.bytes(head.order()),
            Ids.bytes(scan.capture()),Ids.bytes(bale.id()),scan.cycle(),scan.sequence(),bale.code(),bale.weight(),bale.rawWeight(),bale.model(),bale.name());
        if(deduct) {
            changed=jdbc.update("UPDATE inventory_position SET book_status='DEDUCTED',row_version=row_version+1 WHERE bale_id=? AND book_status='NOT_DEDUCTED'",Ids.bytes(bale.id()));
            if(changed!=1) throw new BusinessException(409,"INVENTORY_ALREADY_DEDUCTED","库存账务冲突");
            jdbc.update("INSERT INTO inventory_ledger VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(Ids.next()),Ids.bytes(dispatch),Ids.bytes(bale.id()),-1,-bale.weight());
        }
    }
}
