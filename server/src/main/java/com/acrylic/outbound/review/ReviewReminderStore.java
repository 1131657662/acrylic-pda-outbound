package com.acrylic.outbound.review;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
public class ReviewReminderStore {
    private final JdbcTemplate jdbc;
    private final JsonText json;
    private final AuditWriter audit;

    public ReviewReminderStore(JdbcTemplate jdbc, JsonText json, AuditWriter audit) {
        this.jdbc = jdbc; this.json = json; this.audit = audit;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int createDueReminders() {
        return createDueReminders(null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int createDueReminders(UUID warehouse) {
        var candidates = jdbc.query("""
            SELECT BIN_TO_UUID(s.id),BIN_TO_UUID(c.warehouse_id),BIN_TO_UUID(c.source_order_id),s.created_at
            FROM submission s INNER JOIN submission_scope c ON c.current_submission_id=s.id
            LEFT JOIN submission_review_reminder r ON r.submission_id=s.id
            WHERE s.status='REVIEW_REQUIRED' AND s.created_at<=TIMESTAMPADD(MINUTE,-30,UTC_TIMESTAMP(3))
              AND r.id IS NULL AND (? IS NULL OR c.warehouse_id=?)
            ORDER BY s.created_at,s.id LIMIT 100 FOR UPDATE SKIP LOCKED
            """, (rs,n)->new Candidate(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)),rs.getTimestamp(4).toInstant()),
            warehouse == null ? null : Ids.bytes(warehouse), warehouse == null ? null : Ids.bytes(warehouse));
        int created = 0;
        for (Candidate candidate : candidates) if (insert(candidate)) created++;
        return created;
    }

    public List<Reminder> list(UUID warehouse, int limit) {
        int size = Page.checkedLimit(limit);
        return jdbc.query("""
            SELECT BIN_TO_UUID(r.id),BIN_TO_UUID(r.submission_id),BIN_TO_UUID(r.order_id),r.due_at,r.reminded_at
            FROM submission_review_reminder r WHERE r.warehouse_id=? ORDER BY r.reminded_at DESC,r.id DESC LIMIT ?
            """, (rs,n)->new Reminder(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)),rs.getTimestamp(4).toInstant(),rs.getTimestamp(5).toInstant()),Ids.bytes(warehouse),size);
    }

    private boolean insert(Candidate candidate) {
        Instant due = candidate.createdAt().plus(Duration.ofMinutes(30));
        int changed = jdbc.update("""
            INSERT IGNORE INTO submission_review_reminder(id,submission_id,warehouse_id,order_id,due_at,reminded_at)
            VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))
            """, Ids.bytes(Ids.next()),Ids.bytes(candidate.submission()),Ids.bytes(candidate.warehouse()),
            Ids.bytes(candidate.order()),Timestamp.from(due));
        if (changed != 1) return false;
        audit.append(new AuditWriter.Event(null,"SUBMISSION_REVIEW_REMINDER","ORDER",candidate.order(),
            json.encode(new ReminderEvent(candidate.submission(),due))),"worker:review-reminder");
        return true;
    }

    private record Candidate(UUID submission,UUID warehouse,UUID order,Instant createdAt) { }
    private record ReminderEvent(UUID submissionId,Instant dueAt) { }
    public record Reminder(UUID id,UUID submissionId,UUID orderId,Instant dueAt,Instant remindedAt) { }
}
