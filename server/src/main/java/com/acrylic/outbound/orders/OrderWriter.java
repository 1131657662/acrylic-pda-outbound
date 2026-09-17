package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderWriter {
    private final JdbcTemplate jdbc;
    public OrderWriter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public UUID create(UUID warehouse, UUID actor) {
        UUID id=Ids.next();
        jdbc.update("""
            INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,created_by,created_at,updated_at)
            VALUES(?,?,?,'WEB','DRAFT',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(warehouse),"OB"+id.toString().replace("-",""),Ids.bytes(actor));
        return id;
    }
    public Head lock(UUID warehouse,UUID order) {
        var rows=jdbc.query("SELECT current_revision,row_version,status FROM outbound_order WHERE warehouse_id=? AND id=? FOR UPDATE",
            (rs,n)->new Head(order,rs.getInt(1),rs.getString(2),rs.getString(3)),Ids.bytes(warehouse),Ids.bytes(order));
        if(rows.isEmpty()) throw new BusinessException(404,"ORDER_NOT_FOUND","订单不存在或无权访问");
        return rows.getFirst();
    }
    public void revise(Work work,OrderCommands.Plan plan,List<OrderStock.Product> products) {
        UUID id=Ids.next();
        jdbc.update("""
            INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,
              vehicle_no,container_no,notes,planned_total,reason,created_by,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(work.order()),Ids.bytes(work.warehouse()),work.revision(),
            Ids.bytes(plan.expectedActiveSnapshot()),Long.parseLong(plan.expectedMappingRevision()),plan.vehicleNo().strip(),
            plan.containerNo().strip(),plan.notes().strip(),OrderCommands.total(plan.lines()),work.reason(),Ids.bytes(work.actor()));
        var byCode=new HashMap<String,OrderStock.Product>();
        products.forEach(p->byCode.put(p.deliveryCode(),p));
        jdbc.batchUpdate("""
            INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count)
            VALUES(?,?,?,?,?,?,?)
            """,plan.lines(),200,(ps,line)->{
                var p=byCode.get(line.deliveryCode());
                ps.setBytes(1,Ids.bytes(id)); ps.setString(2,line.deliveryCode()); ps.setBytes(3,Ids.bytes(UUID.fromString(p.mappingId())));
                ps.setBytes(4,Ids.bytes(UUID.fromString(p.productId()))); ps.setString(5,p.model()); ps.setString(6,p.name()); ps.setInt(7,line.plannedCount());
            });
        jdbc.update("UPDATE outbound_order SET current_revision=?,row_version=?,updated_at=UTC_TIMESTAMP(3) WHERE id=?",
            work.revision(),work.version(),Ids.bytes(work.order()));
    }
    public void cancel(Head head) {
        jdbc.update("UPDATE outbound_order SET status='CANCELLED',row_version=row_version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(head.id()));
    }
    public void advanceVersion(UUID order,long version) {
        jdbc.update("UPDATE outbound_order SET row_version=?,updated_at=UTC_TIMESTAMP(3) WHERE id=?",version,Ids.bytes(order));
    }
    public record Head(UUID id,int revision,String rowVersion,String status) {
        public void requireDraft(String expected) {
            if(!rowVersion.equals(expected)) throw new BusinessException(409,"ORDER_VERSION_CONFLICT","订单已被修改，请刷新后核对");
            if(!status.equals("DRAFT")) throw new BusinessException(409,"ORDER_STATE_CONFLICT","仅未发布草稿可执行本操作");
        }
        public void requireUnassigned(String expected) {
            if(!rowVersion.equals(expected)) throw new BusinessException(409,"ORDER_VERSION_CONFLICT","订单已被修改，请刷新后核对");
            if(!status.equals("DRAFT") && !status.equals("READY"))
                throw new BusinessException(409,"ORDER_STATE_CONFLICT","仅草稿或未领取订单可执行本操作；已领取需召回");
        }
    }
    public record Work(UUID warehouse,UUID actor,UUID order,int revision,long version,String reason) { }
}
