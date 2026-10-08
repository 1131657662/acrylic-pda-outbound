package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.TemporarySubmissionPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Rechecks the fixed target header and every product before acceptance or accounting. */
@Component
final class TemporaryHandoverTarget {
    private final JdbcTemplate jdbc;
    TemporaryHandoverTarget(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    boolean require(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan,boolean accepted) {
        var rows=jdbc.queryForList("""
            SELECT c.mapping_version,r.vehicle_no,r.container_no,r.reason,r.revision
            FROM outbound_order o JOIN order_submission_slot l ON l.order_id=o.id
            JOIN submission_scope c ON c.id=l.scope_id AND c.canonical_order_id=o.id
            JOIN temporary_source_mapping m ON m.scope_id=c.id AND m.mapping_version=c.mapping_version
              AND m.canonical_order_id=o.id
            JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision AND r.warehouse_id=o.warehouse_id
            WHERE o.id=? AND o.warehouse_id=? AND o.current_result_version IS NULL
              AND c.source_order_id IS NULL AND c.work_round=? AND l.work_round=c.work_round
              AND ((? AND c.current_submission_id=? AND l.current_submission_id=c.current_submission_id AND l.phase='FROZEN'
                    AND o.status='REVIEW_REQUIRED') OR
                   (NOT ? AND c.current_submission_id IS NULL AND l.current_submission_id IS NULL
                    AND l.phase='CONTINUING' AND o.status='CONTINUING'))
              AND ((c.mapping_version=1 AND o.source='PDA_TEMP' AND m.origin<>'CONTROLLED_LINK') OR
                   (c.mapping_version>1 AND o.source='WEB' AND m.origin='CONTROLLED_LINK'))
              AND r.inventory_snapshot_id=UUID_TO_BIN(?)
            """,Ids.bytes(head.order()),Ids.bytes(head.warehouse()),head.control().round(),accepted,
            Ids.bytes(head.control().id()),accepted,text(plan.manifest(),"inventorySnapshotId"));
        if(rows.size()!=1)throw TemporaryHandoverIntakeFacts.changed();var row=rows.getFirst();
        boolean linked=((Number)row.get("mapping_version")).longValue()>1;
        if(!linked)header(row,plan);lines(head,plan);return linked;
    }
    private void header(java.util.Map<String,Object> row,TemporarySubmissionPlan.Plan plan) {
        equal(((Number)row.get("revision")).longValue(),number(plan.body(),"revision"),"交接目标版本已变化");
        for(String field:java.util.List.of("vehicleNo","containerNo","reason")) {
            String column=switch(field) { case "vehicleNo" -> "vehicle_no"; case "containerNo" -> "container_no"; default -> "reason"; };
            equal(row.get(column),text(plan.body(),field),"交接目标抬头已变化");
        }
    }
    private void lines(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan) {
        var actual=jdbc.query("""
            SELECT l.delivery_code,BIN_TO_UUID(l.product_id),l.model_snapshot,l.name_snapshot,l.planned_count
            FROM order_line l JOIN order_revision r ON r.id=l.revision_id
            JOIN outbound_order o ON o.id=r.order_id AND o.current_revision=r.revision
            WHERE o.id=? ORDER BY l.delivery_code
            """,(rs,n)->object().put("code",rs.getString(1)).put("product",rs.getString(2))
                .put("model",rs.getString(3)).put("name",rs.getString(4)).put("count",rs.getLong(5)),Ids.bytes(head.order()));
        var expected=plan.lines().stream().map(l->object().put("code",l.code()).put("product",text(l.mapping(),"productId"))
            .put("model",text(l.mapping(),"model")).put("name",text(l.mapping(),"name")).put("count",l.count())).toList();
        equal(actual,expected,"交接目标逐产品计划已变化");
    }
}
