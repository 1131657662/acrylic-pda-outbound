package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TemporaryReviewQueries {
    private final JdbcTemplate jdbc;
    public TemporaryReviewQueries(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Page<Item> current(UUID warehouse, String cursor, int requestedLimit) {
        int limit = Page.checkedLimit(requestedLimit);
        var sql = new StringBuilder("""
            SELECT BIN_TO_UUID(s.id),BIN_TO_UUID(o.id),o.order_no,v.vehicle_no,p.received_at,
              p.expected_count,p.expected_weight_g,s.status
            FROM submission s INNER JOIN submission_scope c ON c.id=s.scope_id AND c.current_submission_id=s.id
            INNER JOIN submission_payload p ON p.submission_id=s.id
            INNER JOIN outbound_order o ON o.id=c.canonical_order_id AND o.warehouse_id=c.warehouse_id
            INNER JOIN order_revision v ON v.order_id=o.id AND v.revision=o.current_revision
            INNER JOIN order_submission_slot t ON t.scope_id=c.id AND t.current_submission_id=s.id AND t.work_round=c.work_round
            WHERE c.warehouse_id=? AND c.creator_device_id IS NOT NULL AND s.status='REVIEW_REQUIRED' AND s.work_round=c.work_round
            """);
        var args = new ArrayList<Object>(); args.add(Ids.bytes(warehouse));
        if (!cursor.isEmpty()) {
            var point = TemporaryReviewCursor.parse(cursor, warehouse);
            sql.append(" AND (p.received_at<? OR (p.received_at=? AND s.id<?))");
            args.add(point.time()); args.add(point.time()); args.add(Ids.bytes(point.id()));
        }
        sql.append(" ORDER BY p.received_at DESC,s.id DESC LIMIT ?"); args.add(limit + 1);
        var rows = jdbc.query(sql.toString(), (rs,n) -> new Item(rs.getString(1),rs.getString(2),rs.getString(3),
            rs.getString(4),rs.getTimestamp(5).toInstant(),rs.getString(6),rs.getString(7),rs.getString(8)),args.toArray());
        return Page.from(rows, limit, row -> TemporaryReviewCursor.encode(warehouse, row));
    }

    public Metadata metadata(UUID submission) {
        return jdbc.queryForObject("""
            SELECT o.order_no,s.created_at,p.received_at,BIN_TO_UUID(c.current_submission_id),
              (c.current_submission_id=s.id AND c.work_round=s.work_round AND t.current_submission_id=s.id AND t.work_round=c.work_round),
              u.display_name,d.name
            FROM submission s INNER JOIN submission_scope c ON c.id=s.scope_id
            INNER JOIN submission_payload p ON p.submission_id=s.id
            INNER JOIN outbound_order o ON o.id=c.canonical_order_id
            LEFT JOIN order_submission_slot t ON t.scope_id=c.id AND t.order_id=o.id
            INNER JOIN app_user u ON u.id=s.operator_id INNER JOIN device d ON d.id=s.freeze_device_id WHERE s.id=?
            """, (rs,n) -> new Metadata(rs.getString(1),rs.getTimestamp(2).toInstant(),rs.getTimestamp(3).toInstant(),
                rs.getString(4),rs.getBoolean(5),rs.getString(6),rs.getString(7)),Ids.bytes(submission));
    }
    public record Item(String submissionId,String orderId,String orderNo,String vehicleNo,Instant receivedAt,
                       String expectedCount,String expectedWeightGrams,String status) { }
    public record Metadata(String orderNo,Instant registeredAt,Instant receivedAt,String currentSubmissionId,
                           boolean current,String operatorName,String deviceName) { }
}
