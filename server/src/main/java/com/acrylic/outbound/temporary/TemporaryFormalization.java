package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.orders.OrderNumbers;
import com.acrylic.outbound.submissions.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class TemporaryFormalization {
    private final JdbcTemplate jdbc;private final OrderNumbers numbers;
    public TemporaryFormalization(JdbcTemplate jdbc,OrderNumbers numbers) { this.jdbc=jdbc;this.numbers=numbers; }
    public void create(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan) {
        require(head.order()==null,"临时来源已关联正式记录");UUID id=Ids.next(),revision=Ids.next();var body=plan.body();
        var at=jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",java.time.LocalDateTime.class);
        String number=numbers.next(at.toInstant(java.time.ZoneOffset.UTC));
        jdbc.update("INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,created_by,created_at,updated_at) VALUES(?,?,?,'PDA_TEMP','REVIEW_REQUIRED',?,?,?)",
            Ids.bytes(id),Ids.bytes(head.warehouse()),number,Ids.bytes(head.actor()),at,at);
        jdbc.update("""
            INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,notes,planned_total,reason,created_by,created_at)
            VALUES(?,?,?,?,UUID_TO_BIN(?),?,?,?,?,?,?,?,?)
            """,Ids.bytes(revision),Ids.bytes(id),Ids.bytes(head.warehouse()),number(body,"revision"),text(plan.manifest(),"inventorySnapshotId"),number(plan.manifest(),"mappingRevision"),
            text(body,"vehicleNo"),text(body,"containerNo"),"",plan.lines().stream().mapToLong(TemporarySubmissionPlan.Line::count).reduce(0,Math::addExact),text(body,"reason"),Ids.bytes(head.actor()),at);
        jdbc.batchUpdate("INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count) VALUES(?,?,?,?,?,?,?)",plan.lines(),200,(ps,line)->{
            var m=line.mapping();ps.setBytes(1,Ids.bytes(revision));ps.setString(2,line.code());ps.setBytes(3,Ids.bytes(uuid(m,"id")));ps.setBytes(4,Ids.bytes(uuid(m,"productId")));
            ps.setString(5,text(m,"model"));ps.setString(6,text(m,"name"));ps.setLong(7,line.count());
        });
        jdbc.update("UPDATE outbound_order SET current_revision=? WHERE id=?",number(body,"revision"),Ids.bytes(id));
        jdbc.update("UPDATE submission_scope SET canonical_order_id=? WHERE current_submission_id=? AND canonical_order_id IS NULL",Ids.bytes(id),Ids.bytes(head.control().id()));
        jdbc.update("INSERT INTO order_submission_slot(order_id,scope_id,work_round,current_submission_id,phase) SELECT ?,scope_id,work_round,id,'FROZEN' FROM submission WHERE id=?",Ids.bytes(id),Ids.bytes(head.control().id()));
    }
}
