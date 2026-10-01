package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic FORMAL source for transaction tests; no device stop or field identity claim. */
final class HandoverRollbackFixture {
    final JdbcTemplate jdbc;
    final UUID warehouse, order, device, operator, assignment, scope = Ids.next(), submission = Ids.next();
    final UUID bale = Ids.next();
    final long cycle = 1;
    final SessionUser requester, approver;
    UUID caseId;

    HandoverRollbackFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        var host = jdbc.queryForMap("SELECT BIN_TO_UUID(id) assignment_id,BIN_TO_UUID(order_id) order_id,"
            + "BIN_TO_UUID(warehouse_id) warehouse_id,BIN_TO_UUID(device_id) device_id,"
            + "BIN_TO_UUID(operator_id) operator_id FROM order_assignment WHERE status='ACTIVE' ORDER BY id LIMIT 1");
        warehouse = UUID.fromString(host.get("warehouse_id").toString());
        order = UUID.fromString(host.get("order_id").toString());
        device = UUID.fromString(host.get("device_id").toString());
        operator = UUID.fromString(host.get("operator_id").toString());
        assignment = UUID.fromString(host.get("assignment_id").toString());
        requester = supervisor("rollback-request-");
        approver = supervisor("rollback-approve-");
        source(); reserve(); alignPublication();
    }

    private SessionUser supervisor(String prefix) {
        UUID id = Ids.next(); String username = prefix + id;
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(id), username, "rollback fixture", "unused-no-login");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(id), Ids.bytes(warehouse));
        assertThat(jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'", Ids.bytes(id)))
            .as("the migrated role must exist; never add permissions to production roles in this fixture").isEqualTo(1);
        return new SessionUser(id, username, "rollback fixture", "unused-no-login", 1, true);
    }

    private void source() {
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,current_submission_id) "
            + "VALUES(?,?,?,1,NULL)", Ids.bytes(scope), Ids.bytes(warehouse), Ids.bytes(order));
        jdbc.update("INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,"
            + "source_recovery_epoch,status,receipt_text,created_at,updated_at) "
            + "VALUES(?,?,?,?,?,1,1,'ABORTED','synthetic-original-abort',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            Ids.bytes(submission), Ids.bytes(scope), Ids.bytes(assignment), Ids.bytes(device), Ids.bytes(operator));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?", Ids.bytes(submission), Ids.bytes(scope));
        jdbc.update("INSERT INTO order_submission_slot(order_id,scope_id,work_round,current_submission_id,phase,slot_version) "
            + "VALUES(?,?,1,?,'FROZEN',1)", Ids.bytes(order), Ids.bytes(scope), Ids.bytes(submission));
    }

    private void reserve() {
        // The migration seed's only inventory bale is already SOURCE_RESERVED. Keep it untouched and
        // add a separate synthetic commitment owner; this test does not claim inventory/scan verification.
        jdbc.update("INSERT INTO bale(id,barcode,warehouse_id,dispatch_cycle,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(bale), "RBK" + bale.toString().substring(0, 8), Ids.bytes(warehouse), cycle);
        jdbc.update("INSERT INTO bale_commitment(bale_id,order_id,submission_id,dispatch_cycle,state,created_at) "
            + "VALUES(?,?,?,?,'REVIEW_RESERVED',UTC_TIMESTAMP(3))",
            Ids.bytes(bale), Ids.bytes(order), Ids.bytes(submission), cycle);
    }

    private void alignPublication() {
        assertThat(jdbc.update("UPDATE warehouse w JOIN outbound_order o ON o.warehouse_id=w.id "
            + "JOIN order_revision r ON r.order_id=o.id AND r.revision=o.current_revision "
            + "SET w.mapping_revision=r.mapping_revision WHERE o.id=?", Ids.bytes(order))).isEqualTo(1);
    }

    void stopped(UUID id) {
        caseId = id;
        jdbc.update("INSERT INTO handover_evidence(id,case_id,kind,device_id,payload_sha256,high_watermark,receipt_bytes,recorded_at) "
            + "VALUES(?,?,'STOP_PROOF',?,?,7,?,UTC_TIMESTAMP(3))", Ids.bytes(Ids.next()), Ids.bytes(id), Ids.bytes(device),
            java.util.HexFormat.of().parseHex("c".repeat(64)), "synthetic-stop-proof".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(jdbc.update("UPDATE handover_case SET stop_state='DEVICE_ACKED' WHERE id=?", Ids.bytes(id))).isEqualTo(1);
    }
}
