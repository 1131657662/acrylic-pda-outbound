package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Explicitly synthetic blank formal target for the existing controlled link command. */
final class TemporaryRoundLinkedFixture {
    private final TemporaryRoundConfirmationHttpFixture f;
    final String originalOrder,target;
    TemporaryRoundLinkedFixture(TemporaryRoundConfirmationHttpFixture f) {
        this.f=f;originalOrder=f.http.database.seed.get("orderId");grant();target=create();
    }
    private void grant() {
        var j=f.http.database.jdbc;UUID role=Ids.next();
        j.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"D03_LINK_"+role.toString().substring(24),"隔离续作关联权限");
        j.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='temporary_order:link'",Ids.bytes(role));
        j.update("INSERT INTO user_role VALUES(UUID_TO_BIN(?),?)",f.http.database.seed.get("operatorId"),Ids.bytes(role));
    }
    private String create() {
        var j=f.http.database.jdbc;UUID order=Ids.next(),revision=Ids.next();
        j.update("INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,current_revision,created_by,created_at,updated_at) "
            +"VALUES(?,UUID_TO_BIN(?),?,'WEB','READY',NULL,UUID_TO_BIN(?),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",Ids.bytes(order),
            f.http.database.seed.get("warehouseId"),"D03-LINK-"+order.toString().substring(24),f.http.database.seed.get("operatorId"));
        j.update("""
            INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,
              notes,planned_total,reason,created_by,created_at)
            SELECT ?,?,warehouse_id,2,inventory_snapshot_id,mapping_revision,vehicle_no,'FORMAL-CONTAINER',notes,planned_total,
              'synthetic separately reviewed formal header',created_by,created_at FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1
            """,Ids.bytes(revision),Ids.bytes(order),originalOrder);
        j.update("INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count) "
            +"SELECT ?,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count FROM order_line "
            +"WHERE revision_id=(SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1)",Ids.bytes(revision),originalOrder);
        j.update("UPDATE outbound_order SET current_revision=2 WHERE id=?",Ids.bytes(order));return order.toString();
    }
    void changedPlan(int delta) {
        f.http.database.jdbc.update("UPDATE order_line SET planned_count=planned_count+? WHERE revision_id="
            +"(SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=2)",delta,target);
    }
    void effects() {
        var j=f.http.database.jdbc;String id=text(f.body,"submissionId");
        org.assertj.core.api.Assertions.assertThat(j.queryForObject("SELECT BIN_TO_UUID(order_id) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)",String.class,id)).isEqualTo(target);
        org.assertj.core.api.Assertions.assertThat(j.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?) AND state='DISPATCHED'",Integer.class,id,target)).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(j.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE order_id=UUID_TO_BIN(?)",Integer.class,originalOrder)).isZero();
        org.assertj.core.api.Assertions.assertThat(j.queryForObject("SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)",String.class,originalOrder)).isEqualTo("LINKED");
        org.assertj.core.api.Assertions.assertThat(j.queryForObject("SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)",String.class,target)).isEqualTo("COMPLETED");
    }
}
