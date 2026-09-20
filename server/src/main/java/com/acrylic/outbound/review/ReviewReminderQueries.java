package com.acrylic.outbound.review;

import com.acrylic.outbound.common.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewReminderQueries {
    private final JdbcTemplate jdbc;
    public ReviewReminderQueries(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Page<Item> current(UUID warehouse, String cursor, int requestedLimit) {
        int limit = Page.checkedLimit(requestedLimit);
        var sql = new StringBuilder("""
            SELECT BIN_TO_UUID(r.id),BIN_TO_UUID(r.submission_id),BIN_TO_UUID(r.order_id),o.order_no,
              v.vehicle_no,s.created_at,r.due_at,r.reminded_at
            FROM submission_review_reminder r
            INNER JOIN submission s ON s.id=r.submission_id
            INNER JOIN submission_scope c ON c.id=s.scope_id AND c.current_submission_id=s.id
              AND c.warehouse_id=r.warehouse_id AND c.source_order_id=r.order_id
            INNER JOIN outbound_order o ON o.id=r.order_id AND o.warehouse_id=r.warehouse_id
            INNER JOIN order_revision v ON v.order_id=o.id AND v.revision=o.current_revision
            WHERE r.warehouse_id=? AND s.status='REVIEW_REQUIRED'
            """);
        var args = new ArrayList<Object>(); args.add(Ids.bytes(warehouse));
        if (!cursor.isEmpty()) {
            var point = ReviewReminderCursor.parse(cursor, warehouse);
            sql.append(" AND (r.reminded_at<? OR (r.reminded_at=? AND r.id<?))");
            args.add(point.remindedAt()); args.add(point.remindedAt()); args.add(Ids.bytes(point.id()));
        }
        sql.append(" ORDER BY r.reminded_at DESC,r.id DESC LIMIT ?"); args.add(limit + 1);
        var rows = jdbc.query(sql.toString(), (rs, n) -> new Item(rs.getString(1), rs.getString(2), rs.getString(3),
            rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant(),
            rs.getTimestamp(8).toInstant()), args.toArray());
        return Page.from(rows, limit, row -> ReviewReminderCursor.encode(warehouse, row));
    }

    public record Item(String id, String submissionId, String orderId, String orderNo, String vehicleNo,
                       Instant submittedAt, Instant dueAt, Instant remindedAt) { }
}
