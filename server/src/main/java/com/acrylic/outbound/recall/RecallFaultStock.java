package com.acrylic.outbound.recall;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.sync.BarcodeChanges;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RecallFaultStock {
    private final JdbcTemplate jdbc;
    private final Access permissions;
    public RecallFaultStock(JdbcTemplate jdbc,Access permissions) { this.jdbc=jdbc;this.permissions=permissions; }

    public Review review(RecallFaultState.Work w,JsonNode command) {
        var ids=jdbc.queryForList("SELECT BIN_TO_UUID(bale_id) FROM bale_commitment WHERE order_id=? ORDER BY bale_id",String.class,Ids.bytes(w.order().id()));
        if(ids.isEmpty())return new Review(List.of(),"");
        permissions.require("order:dispose",w.context().warehouse());
        String physical=command.path("physicalEvidence").asText("").strip();
        if(!command.path("physicalChecked").isBoolean() || !command.path("physicalChecked").asBoolean() || physical.isBlank() || physical.length()>1000)
            throw new BusinessException(409,"FAULT_WITHDRAWAL_STOCK_REQUIRED","已存包占用须有权人员完成卸货/实物核验并填写凭据，原事实保留");
        var items=new ArrayList<Item>();
        for(String id:ids)items.add(lock(w,UUID.fromString(id)));
        return new Review(List.copyOf(items),physical);
    }

    private Item lock(RecallFaultState.Work w,UUID bale) {
        long cycle=jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE",Long.class,Ids.bytes(bale));
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(c.submission_id),c.dispatch_cycle FROM bale_commitment c
            INNER JOIN submission s ON s.id=c.submission_id
            WHERE c.bale_id=? AND c.order_id=? AND c.state='REVIEW_RESERVED'
              AND s.assignment_id=? AND s.status!='SUCCEEDED'
              AND c.source_scope_id IS NULL AND c.recovery_package_id IS NULL AND c.continuation_id IS NULL
            FOR UPDATE
            """,(rs,n)->new Item(bale,UUID.fromString(rs.getString(1)),rs.getLong(2)),Ids.bytes(bale),Ids.bytes(w.order().id()),Ids.bytes(w.assignment()));
        if(rows.size()!=1 || rows.getFirst().cycle()!=cycle)
            throw new BusinessException(409,"FAULT_WITHDRAWAL_STOCK_CONFLICT","占用来源/周期或实物处置状态不符，须保留并先对账");
        return rows.getFirst();
    }

    public void release(RecallFaultState.Work w,Review review) {
        for(var item:review.items()) {
            jdbc.update("""
                INSERT INTO fault_withdrawal_stock(request_id,bale_id,submission_id,dispatch_cycle,original_state,
                  physical_evidence,verified_by,released_at) VALUES(?,?,?,?,'REVIEW_RESERVED',?,?,UTC_TIMESTAMP(3))
                """,Ids.bytes(w.id()),Ids.bytes(item.bale()),Ids.bytes(item.submission()),item.cycle(),review.physical(),Ids.bytes(w.actor()));
            int changed=jdbc.update("""
                DELETE FROM bale_commitment WHERE bale_id=? AND order_id=? AND submission_id=?
                  AND dispatch_cycle=? AND state='REVIEW_RESERVED'
                """,Ids.bytes(item.bale()),Ids.bytes(w.order().id()),Ids.bytes(item.submission()),item.cycle());
            if(changed!=1)throw new BusinessException(409,"FAULT_WITHDRAWAL_STOCK_CONFLICT","占用已变化，撤回与释放全部回滚");
            BarcodeChanges.bale(jdbc,item.bale());
        }
    }

    public record Item(UUID bale,UUID submission,long cycle) { }
    public record Review(List<Item> items,String physical) {
        public ArrayNode manifest() {
            var result=object().putArray("items");
            for(var i:items)result.add(object().put("baleId",i.bale().toString()).put("submissionId",i.submission().toString()).put("dispatchCycle",Long.toString(i.cycle())));
            return result;
        }
    }
}
