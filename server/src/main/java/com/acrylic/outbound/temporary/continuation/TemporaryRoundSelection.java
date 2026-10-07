package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.TemporarySubmissionPlan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.temporary.continuation.TemporaryRoundFacts.conflict;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Selects the registered round only inside the full payload/stock acceptance transaction. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class TemporaryRoundSelection {
    private final JdbcTemplate jdbc;private final TemporaryRoundLinkedTarget linked;
    public TemporaryRoundSelection(JdbcTemplate jdbc,TemporaryRoundLinkedTarget linked) { this.jdbc=jdbc;this.linked=linked; }
    public void select(SubmissionStore.Head head,TemporaryRoundFacts.Source source,TemporarySubmissionPlan.Plan plan) {
        if(head.order()!=null && !linked.requirePredecessor(head,source,plan))requireTarget(head,source,plan);
        int changed=jdbc.update("""
            UPDATE submission_scope SET current_submission_id=?,work_round=?
            WHERE id=? AND current_submission_id=? AND work_round=? AND source_order_id IS NULL
            """,Ids.bytes(head.control().id()),source.toRound(),Ids.bytes(source.scope()),Ids.bytes(source.previous()),source.fromRound());
        if(changed!=1)throw conflict("TEMP_CONTINUATION_PREDECESSOR_CHANGED");
        if(head.order()==null)return;
        changed=jdbc.update("""
            UPDATE order_submission_slot SET current_submission_id=?,work_round=?,phase='FROZEN',slot_version=slot_version+1
            WHERE order_id=? AND scope_id=? AND current_submission_id=? AND work_round=? AND phase='FROZEN'
            """,Ids.bytes(head.control().id()),source.toRound(),Ids.bytes(head.order()),Ids.bytes(source.scope()),
            Ids.bytes(source.previous()),source.fromRound());
        if(changed!=1)throw conflict("TEMP_CONTINUATION_TARGET_CHANGED");
    }
    public void requireTarget(SubmissionStore.Head head,TemporaryRoundFacts.Source source,TemporarySubmissionPlan.Plan plan) {
        int valid=jdbc.queryForObject("""
            SELECT COUNT(*) FROM submission_scope c JOIN outbound_order o ON o.id=c.canonical_order_id AND o.warehouse_id=c.warehouse_id
            JOIN temporary_source_mapping m ON m.scope_id=c.id AND m.mapping_version=c.mapping_version AND m.canonical_order_id=o.id
            JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision AND r.warehouse_id=c.warehouse_id
            WHERE c.id=? AND o.id=? AND o.current_result_version IS NULL
              AND c.mapping_version=1 AND o.source='PDA_TEMP' AND o.status='REVIEW_REQUIRED'
              AND r.revision=? AND r.inventory_snapshot_id=UUID_TO_BIN(?) AND r.mapping_revision=?
              AND r.vehicle_no=? AND r.container_no=? AND r.reason=? AND r.planned_total=?
              AND NOT EXISTS (SELECT 1 FROM order_change_request q WHERE q.order_id=o.id AND q.status!='CANCELLED')
            """,Integer.class,Ids.bytes(source.scope()),Ids.bytes(head.order()),number(plan.body(),"revision"),
            text(plan.manifest(),"inventorySnapshotId"),number(plan.manifest(),"mappingRevision"),
            text(plan.body(),"vehicleNo"),text(plan.body(),"containerNo"),text(plan.body(),"reason"),
            plan.lines().stream().mapToLong(TemporarySubmissionPlan.Line::count).reduce(0,Math::addExact));
        if(valid!=1)throw conflict("TEMP_CONTINUATION_TARGET_CHANGED");
        lines(head,plan);
    }
    private void lines(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan) {
        var actual=jdbc.query("""
            SELECT l.delivery_code,BIN_TO_UUID(l.mapping_id),BIN_TO_UUID(l.product_id),l.model_snapshot,l.name_snapshot,l.planned_count
            FROM order_line l JOIN order_revision r ON r.id=l.revision_id
            WHERE r.order_id=? AND r.revision=? ORDER BY l.delivery_code
            """,(rs,n)->object().put("code",rs.getString(1)).put("mapping",rs.getString(2)).put("product",rs.getString(3))
                .put("model",rs.getString(4)).put("name",rs.getString(5)).put("count",rs.getLong(6)),
            Ids.bytes(head.order()),number(plan.body(),"revision"));
        var expected=plan.lines().stream().map(l->object().put("code",l.code()).put("mapping",text(l.mapping(),"id"))
            .put("product",text(l.mapping(),"productId")).put("model",text(l.mapping(),"model"))
            .put("name",text(l.mapping(),"name")).put("count",l.count())).toList();
        if(!actual.equals(expected))throw conflict("TEMP_CONTINUATION_TARGET_CHANGED");
    }
}
