package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.policies.PolicyStore;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderPublicationStore {
    private final JdbcTemplate jdbc;
    private final JsonText json;
    public OrderPublicationStore(JdbcTemplate jdbc,JsonText json) { this.jdbc=jdbc;this.json=json; }
    public void markReady(UUID order) {
        jdbc.update("UPDATE outbound_order SET status='READY',updated_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(order));
    }
    public void save(OrderPublishing.Work work,PolicyStore.Policy policy,OrderViews.Detail detail) {
        UUID id=Ids.next();String snapshot=json.encode(new Snapshot(detail,policy,work.epoch()));
        jdbc.update("""
            INSERT INTO order_publication(id,warehouse_id,order_id,revision,row_version,policy_id,auto_deduct_inventory,
              recovery_epoch,snapshot_json,reason,published_by,published_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(work.warehouse()),Ids.bytes(work.order()),detail.plan().revision(),
            Long.parseLong(detail.order().rowVersion()),Ids.bytes(UUID.fromString(policy.id())),policy.autoDeductInventory(),
            Long.parseLong(work.epoch()),snapshot,work.reason(),Ids.bytes(work.actor()));
        supersede(work.order());
        jdbc.update("""
            INSERT INTO order_plan_document_task(id,publication_id,template_version,status,created_at,updated_at)
            VALUES(?,?,'loading-plan-v1','PENDING',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """,Ids.bytes(Ids.next()),Ids.bytes(id));
        event(work,detail,new Change("ORDER_PUBLISHED",id.toString(),snapshot));
    }
    public void cancelled(OrderPublishing.Work work,OrderViews.Detail detail) {
        supersede(work.order());
        event(work,detail,new Change("ORDER_CANCELLED",null,json.encode(detail)));
    }
    private void supersede(UUID order) {
        jdbc.update("""
            UPDATE order_plan_document_task t INNER JOIN order_publication p ON p.id=t.publication_id
            SET t.status='SUPERSEDED',t.updated_at=UTC_TIMESTAMP(3) WHERE p.order_id=? AND t.status='PENDING'
            """,Ids.bytes(order));
    }
    private void event(OrderPublishing.Work work,OrderViews.Detail detail,Change change) {
        jdbc.update("""
            INSERT INTO order_sync_outbox(id,recovery_epoch,warehouse_id,order_id,entity_version,event_type,payload,created_at)
            VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(Ids.next()),Long.parseLong(work.epoch()),Ids.bytes(work.warehouse()),Ids.bytes(work.order()),
            Long.parseLong(detail.order().rowVersion()),change.type(),json.encode(change));
    }
    public Page<Publication> history(UUID order,long before,int pageSize) {
        int limit=Page.checkedLimit(pageSize);
        if(before<=0) throw new BusinessException(400,"INVALID_CURSOR","发布版本游标无效");
        return Page.from(jdbc.query("""
            SELECT BIN_TO_UUID(p.id),p.revision,p.row_version,v.version,p.auto_deduct_inventory,p.recovery_epoch,
              p.reason,u.display_name,p.published_at,BIN_TO_UUID(t.id),t.status,t.template_version
            FROM order_publication p INNER JOIN policy_version v ON v.id=p.policy_id
            INNER JOIN app_user u ON u.id=p.published_by INNER JOIN order_plan_document_task t ON t.publication_id=p.id
            WHERE p.order_id=? AND p.revision<? ORDER BY p.revision DESC LIMIT ?
            """,(rs,n)->new Publication(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),
                rs.getString(6),rs.getString(7),rs.getString(8),rs.getTimestamp(9).toInstant().toString(),
                rs.getString(10),rs.getString(11),rs.getString(12)),Ids.bytes(order),before,limit+1),limit,p->Integer.toString(p.revision()));
    }
    private record Snapshot(OrderViews.Detail order,PolicyStore.Policy policy,String serverRecoveryEpoch) { }
    private record Change(String type,String publicationId,String snapshotJson) { }
    public record Publication(String id,int revision,String rowVersion,String policyVersion,boolean autoDeductInventory,
        String serverRecoveryEpoch,String reason,String publishedBy,String publishedAt,String documentTaskId,
        String documentStatus,String templateVersion) { }
}
