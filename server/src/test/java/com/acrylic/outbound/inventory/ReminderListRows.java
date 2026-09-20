package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import java.util.UUID;

/** Query-only list rows; use the original SubmissionFixture for real approval/stock assertions. */
record ReminderListRows(SubmissionFixture source) {
    String add() throws Exception {
        var orders = new OrderTestDriver(source.web);
        String order = orders.create(orders.plan()).path("orderId").asText();
        UUID scope = Ids.next(), submission = Ids.next();
        var jdbc = source.jdbc;
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id) VALUES(?,?,?)",
            Ids.bytes(scope), Ids.bytes(source.web.identity().warehouse()), Ids.bytes(UUID.fromString(order)));
        jdbc.update("""
            INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,
              source_recovery_epoch,status,created_at,updated_at)
            SELECT ?,?,assignment_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,
              'REVIEW_REQUIRED',UTC_TIMESTAMP(3)-INTERVAL 31 MINUTE,UTC_TIMESTAMP(3) FROM submission WHERE id=?
            """, Ids.bytes(submission), Ids.bytes(scope), Ids.bytes(UUID.fromString(source.id())));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?", Ids.bytes(submission), Ids.bytes(scope));
        jdbc.update("""
            INSERT INTO submission_review_reminder(id,submission_id,warehouse_id,order_id,due_at,reminded_at)
            VALUES(?,?,?,?,UTC_TIMESTAMP(3)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(3))
            """, Ids.bytes(Ids.next()), Ids.bytes(submission), Ids.bytes(source.web.identity().warehouse()), Ids.bytes(UUID.fromString(order)));
        return submission.toString();
    }
}
