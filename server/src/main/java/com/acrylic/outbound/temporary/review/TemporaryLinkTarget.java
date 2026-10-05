package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.temporary.review.TemporaryDuplicateModels.*;

/**
 * Fresh recheck of the single reviewed formal order inside the write lock. The reviewed candidate
 * page, its digest and any browser page are only hints: every condition is read again here.
 */
@Component
public class TemporaryLinkTarget {
    private final JdbcTemplate jdbc;
    public TemporaryLinkTarget(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void lockAndVerify(UUID warehouse,JsonNode context,Head head) {
        UUID target=UUID.fromString(head.id());
        var rows=jdbc.queryForList("""
            SELECT o.status,o.current_revision,o.row_version,BIN_TO_UUID(r.inventory_snapshot_id) inventory_snapshot_id,
             EXISTS(SELECT 1 FROM order_assignment a WHERE a.order_id=o.id) has_assignment,
             EXISTS(SELECT 1 FROM pc_work w WHERE w.order_id=o.id) has_pc_work,
             EXISTS(SELECT 1 FROM submission_scope c WHERE c.canonical_order_id=o.id OR c.source_order_id=o.id) has_submission,
             EXISTS(SELECT 1 FROM order_result_version v WHERE v.order_id=o.id) has_result
            FROM outbound_order o JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision
            WHERE o.id=? AND o.warehouse_id=? AND o.source='WEB' FOR UPDATE
            """,Ids.bytes(target),Ids.bytes(warehouse));
        if(rows.size()!=1)throw reject("TEMPORARY_LINK_TARGET_NOT_FOUND","关联目标正式单不存在或不属于当前仓库");
        var row=rows.getFirst();
        if(!head.status().equals("READY") || !"READY".equals(row.get("status").toString()))
            throw reject("TEMPORARY_LINK_TARGET_NOT_READY","目标正式单不是可关联的空白待领取状态");
        if(head.revision()!=((Number)row.get("current_revision")).intValue()
            || !head.rowVersion().equals(row.get("row_version").toString()))
            throw reject("TEMPORARY_LINK_TARGET_CHANGED","目标正式单在人工核对后已变化，请重新核对候选");
        if(truth(row.get("has_assignment")) || truth(row.get("has_pc_work")) || truth(row.get("has_submission")) || truth(row.get("has_result")))
            throw reject("TEMPORARY_LINK_TARGET_HAS_WORK","目标正式单已有领取、PC或提交作业，禁止关联并保留双方证据");
        if(!head.inventorySnapshotId().equals(row.get("inventory_snapshot_id").toString())
            || !head.inventorySnapshotId().equals(context.path("inventorySnapshotId").asText()))
            throw reject("TEMPORARY_LINK_TARGET_INVENTORY_DIFFERS","目标正式单与原库存版本不一致");
        if(!new HashSet<>(lines(head.revisionId())).equals(new HashSet<>(expected(context))))
            throw reject("TEMPORARY_LINK_TARGET_PLAN_DIFFERS","目标正式单计划与原临时计划不一致");
    }
    /** The linked source's own scope is allowed; any other current or historical work prevents relinking. */
    public void lockAndVerifyHistory(UUID warehouse,UUID scope,String newTarget) {
        var rows=jdbc.queryForList("""
            SELECT BIN_TO_UUID(o.id) id,
             EXISTS(SELECT 1 FROM order_assignment a WHERE a.order_id=o.id) has_assignment,
             EXISTS(SELECT 1 FROM pc_work w WHERE w.order_id=o.id) has_pc_work,
             EXISTS(SELECT 1 FROM submission_scope c WHERE c.id<>?
               AND (c.canonical_order_id=o.id OR c.source_order_id=o.id)) has_submission,
             EXISTS(SELECT 1 FROM order_result_version v WHERE v.order_id=o.id) has_result
            FROM outbound_order o WHERE o.warehouse_id=? AND o.id IN
              (SELECT m.canonical_order_id FROM temporary_source_mapping m WHERE m.scope_id=? AND m.origin='CONTROLLED_LINK')
            ORDER BY o.id FOR UPDATE
            """,Ids.bytes(scope),Ids.bytes(warehouse),Ids.bytes(scope));
        for(var row:rows) {
            if(truth(row.get("has_assignment")) || truth(row.get("has_pc_work")) || truth(row.get("has_submission")) || truth(row.get("has_result")))
                throw reject("TEMPORARY_LINK_TARGET_HAS_WORK","现关联或历史关联正式单已有作业，禁止重关联并保留证据");
            if(newTarget.equals(row.get("id")))
                throw reject("TEMPORARY_LINK_TARGET_ALREADY_USED","重关联必须选择新的空白正式单，不能回到历史目标");
        }
    }
    private List<Product> lines(String revisionId) {
        return jdbc.query("""
            SELECT delivery_code,BIN_TO_UUID(product_id),model_snapshot,name_snapshot,CAST(planned_count AS CHAR)
            FROM order_line WHERE revision_id=? ORDER BY delivery_code
            """,(rs,n)->new Product(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),Ids.bytes(UUID.fromString(revisionId)));
    }
    private List<Product> expected(JsonNode context) {
        var result=new ArrayList<Product>();
        context.path("products").forEach(p->result.add(new Product(p.path("deliveryCode").asText(),p.path("productId").asText(),
            p.path("model").asText(),p.path("name").asText(),p.path("plannedCount").asText())));
        return result;
    }
    private boolean truth(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()==1; }
    private static BusinessException reject(String code,String message) { return new BusinessException(409,code,message); }
}
