package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Creates an empty operation while retaining the old publication and submission facts. */
@Component
public class RecallFaultReset {
    private final JdbcTemplate jdbc;
    public RecallFaultReset(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public void prepare(RecallFaultState.Work w) {
        UUID revision=Ids.next();int next=Math.addExact(w.order().revision(),1);
        jdbc.update("""
            INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,
              vehicle_no,container_no,notes,planned_total,reason,created_by,created_at)
            SELECT ?,order_id,warehouse_id,?,inventory_snapshot_id,mapping_revision,
              vehicle_no,container_no,notes,planned_total,?,?,UTC_TIMESTAMP(3)
            FROM order_revision WHERE order_id=? AND revision=?
            """,Ids.bytes(revision),next,w.reason(),Ids.bytes(w.actor()),Ids.bytes(w.order().id()),w.order().revision());
        jdbc.update("""
            INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count)
            SELECT ?,l.delivery_code,l.mapping_id,l.product_id,l.model_snapshot,l.name_snapshot,l.planned_count
            FROM order_line l INNER JOIN order_revision r ON r.id=l.revision_id WHERE r.order_id=? AND r.revision=?
            """,Ids.bytes(revision),Ids.bytes(w.order().id()),w.order().revision());
        jdbc.update("UPDATE outbound_order SET current_revision=? WHERE id=?",next,Ids.bytes(w.order().id()));
        jdbc.update("UPDATE submission_scope SET current_submission_id=NULL,work_round=work_round+1 WHERE source_order_id=?",Ids.bytes(w.order().id()));
        jdbc.update("""
            UPDATE order_submission_slot t INNER JOIN submission_scope s ON s.id=t.scope_id
            SET t.current_submission_id=NULL,t.work_round=s.work_round,t.phase='CLOSED',t.slot_version=t.slot_version+1
            WHERE t.order_id=?
            """,Ids.bytes(w.order().id()));
    }
}
