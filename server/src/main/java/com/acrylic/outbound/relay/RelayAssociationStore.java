package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class RelayAssociationStore {
    private final JdbcTemplate jdbc;
    public RelayAssociationStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void lockTarget(UUID warehouse,UUID target) {
        var ids=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM outbound_order WHERE id=? AND warehouse_id=? FOR UPDATE",
            String.class,Ids.bytes(target),Ids.bytes(warehouse));
        if(ids.size()!=1)throw missing();
        int current=jdbc.queryForObject("""
            SELECT COUNT(*) FROM outbound_order o INNER JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision
              INNER JOIN warehouse w ON w.id=o.warehouse_id
            WHERE o.id=? AND r.inventory_snapshot_id=w.active_inventory_snapshot_id AND r.mapping_revision=w.mapping_revision
              AND NOT EXISTS(SELECT 1 FROM order_line l INNER JOIN delivery_mapping m ON m.id=l.mapping_id
                INNER JOIN product p ON p.id=l.product_id WHERE l.revision_id=r.id
                  AND (m.is_active=0 OR m.enabled=0 OR m.product_id<>l.product_id OR m.delivery_code<>l.delivery_code OR p.status<>'ACTIVE'))
            """,Integer.class,Ids.bytes(target));
        if(current!=1)throw RelayAssociationPolicy.blocked("目标库存或产品映射已变化，请先修订正式单");
    }
    public UUID byTask(UUID warehouse,UUID device,UUID task) {
        var ids=jdbc.queryForList("""
            SELECT BIN_TO_UUID(id) FROM independent_association WHERE warehouse_id=? AND device_id=? AND task_id=?
            UNION ALL SELECT BIN_TO_UUID(association_id) FROM independent_association_member WHERE warehouse_id=? AND device_id=? AND task_id=?
            """,String.class,Ids.bytes(warehouse),Ids.bytes(device),Ids.bytes(task),Ids.bytes(warehouse),Ids.bytes(device),Ids.bytes(task));
        if(ids.size()>1)throw corrupt();
        return ids.isEmpty()?null:UUID.fromString(ids.getFirst());
    }
    public UUID byOriginal(UUID warehouse,UUID relay) {
        var ids=jdbc.queryForList("""
            SELECT BIN_TO_UUID(a.id) FROM original_relay r INNER JOIN independent_association a
              ON a.warehouse_id=r.warehouse_id AND a.device_id=r.original_device_id AND a.task_id=r.local_order_id
            WHERE r.id=? AND r.warehouse_id=? AND r.source_kind='INDEPENDENT'
            UNION ALL SELECT BIN_TO_UUID(a.association_id) FROM original_relay r INNER JOIN independent_association_member a
              ON a.warehouse_id=r.warehouse_id AND a.device_id=r.original_device_id AND a.task_id=r.local_order_id
            WHERE r.id=? AND r.warehouse_id=? AND r.source_kind='INDEPENDENT'
            """,String.class,Ids.bytes(relay),Ids.bytes(warehouse),Ids.bytes(relay),Ids.bytes(warehouse));
        if(ids.size()!=1)throw missing();return UUID.fromString(ids.getFirst());
    }
    public void available(Scope scope) {
        if(byTask(scope.warehouse(),scope.device(),scope.task())!=null)throw RelayAssociationPolicy.blocked("该原任务已有人工关联，请读取原记录");
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM independent_association WHERE warehouse_id=? AND target_order_id=?",
            Integer.class,Ids.bytes(scope.warehouse()),Ids.bytes(scope.target()));
        if(count!=0)throw RelayAssociationPolicy.blocked("目标已有独立来源关联，不得重复完整计划");
    }
    public void insert(Row row) {
        var s=row.scope();jdbc.update("INSERT INTO independent_association VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",Ids.bytes(row.id()),Ids.bytes(s.warehouse()),
            Ids.bytes(s.relay()),Ids.bytes(s.device()),Ids.bytes(s.task()),Ids.bytes(s.target()),Ids.bytes(s.actor()),Ids.bytes(s.physical()),
            sha(row.request()),sha(row.context()),canonical(row.request()),canonical(row.context()),canonical(row.receipt()),
            Timestamp.from(row.at()),text(row.context().path("original"),"archiveSha256"));
    }
    public Row read(UUID warehouse,UUID id) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(relay_id),BIN_TO_UUID(device_id),BIN_TO_UUID(task_id),BIN_TO_UUID(target_order_id),
              BIN_TO_UUID(supervisor_id),BIN_TO_UUID(physical_check_id),request_text,context_text,receipt_text,recorded_at,
              request_sha256,context_sha256,archive_sha256 FROM independent_association WHERE id=? AND warehouse_id=?
            """,(rs,n)->{
                var request=parse(rs.getString(7));var context=parse(rs.getString(8));
                if(!sha(request).equals(rs.getString(11)) || !sha(context).equals(rs.getString(12))
                    || !text(context.path("original"),"archiveSha256").equals(rs.getString(13)))throw corrupt();
                return new Row(id,new Scope(warehouse,UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                    UUID.fromString(rs.getString(3)),UUID.fromString(rs.getString(4)),UUID.fromString(rs.getString(5)),UUID.fromString(rs.getString(6))),
                    request,context,parse(rs.getString(9)),rs.getTimestamp(10).toInstant());
            },Ids.bytes(id),Ids.bytes(warehouse));
        if(rows.size()!=1)throw missing();return rows.getFirst();
    }
    static BusinessException missing() { return new BusinessException(404,"INDEPENDENT_ASSOCIATION_NOT_FOUND","人工关联不存在或无权访问"); }
    static BusinessException corrupt() { return new BusinessException(409,"INDEPENDENT_ASSOCIATION_INTEGRITY","原人工关联不可核验，请保全核对"); }
    public record Scope(UUID warehouse,UUID relay,UUID device,UUID task,UUID target,UUID actor,UUID physical) { }
    public record Row(UUID id,Scope scope,JsonNode request,JsonNode context,JsonNode receipt,Instant at) { }
}
