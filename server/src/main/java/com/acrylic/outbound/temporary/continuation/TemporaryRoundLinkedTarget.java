package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.TemporarySubmissionPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Rechecks the formal target selected by the existing manually reviewed mapping, without rewriting TEMP provenance. */
@Component
public class TemporaryRoundLinkedTarget {
    private final JdbcTemplate jdbc;
    public TemporaryRoundLinkedTarget(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean require(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan) {
        long version=jdbc.queryForObject("SELECT mapping_version FROM submission_scope WHERE current_submission_id=?",
            Long.class,Ids.bytes(head.control().id()));
        if(version<=1)return false;
        target(head,plan);lines(head,plan);return true;
    }
    private void target(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan) {
        int valid=jdbc.queryForObject("""
            SELECT COUNT(*) FROM submission_scope c
            JOIN temporary_source_mapping m ON m.scope_id=c.id AND m.mapping_version=c.mapping_version
              AND m.canonical_order_id=c.canonical_order_id AND m.origin='CONTROLLED_LINK'
            JOIN outbound_order o ON o.id=m.canonical_order_id AND o.warehouse_id=c.warehouse_id
            JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision AND r.warehouse_id=c.warehouse_id
            JOIN order_submission_slot l ON l.order_id=o.id AND l.scope_id=c.id
              AND l.current_submission_id=c.current_submission_id AND l.work_round=c.work_round AND l.phase='FROZEN'
            WHERE c.current_submission_id=? AND c.work_round=? AND c.source_order_id IS NULL
              AND c.mapping_version>1 AND o.id=? AND o.source='WEB' AND o.status='READY'
              AND o.current_result_version IS NULL AND r.inventory_snapshot_id=UUID_TO_BIN(?)
              AND NOT EXISTS(SELECT 1 FROM order_assignment a WHERE a.order_id=o.id)
              AND NOT EXISTS(SELECT 1 FROM pc_work p WHERE p.order_id=o.id)
              AND NOT EXISTS(SELECT 1 FROM submission_scope x WHERE x.id<>c.id AND (x.canonical_order_id=o.id OR x.source_order_id=o.id))
              AND NOT EXISTS(SELECT 1 FROM order_result_version v WHERE v.order_id=o.id)
              AND NOT EXISTS(SELECT 1 FROM order_change_request q WHERE q.order_id=o.id AND q.status!='CANCELLED')
            """,Integer.class,Ids.bytes(head.control().id()),head.control().round(),Ids.bytes(head.order()),
            text(plan.manifest(),"inventorySnapshotId"));
        if(valid!=1)throw TemporaryRoundFacts.conflict("TEMP_CONTINUATION_TARGET_CHANGED");
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
        if(!actual.equals(expected))throw new BusinessException(409,"TEMPORARY_LINK_TARGET_PLAN_DIFFERS","关联正式单计划已变化，请重新核对");
    }
}
