package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.HandoverBusinessSource;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual recovery and stop handlers supply positive command prerequisites; list-only history stays synthetic. */
final class HandoverRollbackFixture {
    final JdbcTemplate jdbc;
    final UUID warehouse, order, device, operator, assignment, scope, submission, bale;
    final long cycle = 1;
    final SessionUser requester, approver;
    final HandoverBusinessSource source;
    UUID caseId;

    HandoverRollbackFixture(ConfigurableApplicationContext app) {
        jdbc = app.getBean(JdbcTemplate.class); source = new HandoverBusinessSource((WebApplicationContext) app);
        warehouse = source.warehouse; order = source.order; device = source.device; operator = source.operator;
        assignment = source.assignment; scope = source.scope; submission = source.submission; bale = source.bale;
        requester = supervisor("rollback-request-"); approver = supervisor("rollback-approve-");
    }

    /** Used only by the read-only list history fixture; this cannot provide a successful stop/approval chain. */
    HandoverRollbackFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc; source = null;
        var host = jdbc.queryForMap("SELECT BIN_TO_UUID(id) assignment_id,BIN_TO_UUID(order_id) order_id,"
            + "BIN_TO_UUID(warehouse_id) warehouse_id,BIN_TO_UUID(device_id) device_id,"
            + "BIN_TO_UUID(operator_id) operator_id FROM order_assignment WHERE status='ACTIVE' ORDER BY id LIMIT 1");
        warehouse = UUID.fromString(host.get("warehouse_id").toString()); order = UUID.fromString(host.get("order_id").toString());
        device = UUID.fromString(host.get("device_id").toString()); operator = UUID.fromString(host.get("operator_id").toString());
        assignment = UUID.fromString(host.get("assignment_id").toString()); scope = Ids.next(); submission = Ids.next(); bale = Ids.next();
        requester = supervisor("list-request-"); approver = supervisor("list-approve-");
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,current_submission_id) VALUES(?,?,?,1,NULL)",
            Ids.bytes(scope), Ids.bytes(warehouse), Ids.bytes(order));
        jdbc.update("INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,status,receipt_text,created_at,updated_at) "
            + "VALUES(?,?,?,?,?,1,1,'ABORTED','synthetic-list-history',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            Ids.bytes(submission), Ids.bytes(scope), Ids.bytes(assignment), Ids.bytes(device), Ids.bytes(operator));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?", Ids.bytes(submission), Ids.bytes(scope));
        jdbc.update("INSERT INTO order_submission_slot(order_id,scope_id,work_round,current_submission_id,phase,slot_version) VALUES(?,?,1,?,'FROZEN',1)",
            Ids.bytes(order), Ids.bytes(scope), Ids.bytes(submission));
    }

    private SessionUser supervisor(String prefix) {
        UUID id = Ids.next(); String username = prefix + id;
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(id), username, "rollback fixture", "unused-no-login");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(id), Ids.bytes(warehouse));
        assertThat(jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'", Ids.bytes(id))).isEqualTo(1);
        return new SessionUser(id, username, "rollback fixture", "unused-no-login", 1, true);
    }

    void stopped(UUID id) {
        if (source == null) throw new IllegalStateException("Synthetic list history cannot provide stop evidence");
        caseId = id; source.stopAndAbort(id);
    }
}
