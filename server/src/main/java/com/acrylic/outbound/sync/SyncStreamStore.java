package com.acrylic.outbound.sync;

import com.acrylic.outbound.common.Ids;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SyncStreamStore {
    private final JdbcTemplate jdbc;
    private final SyncOrderStatus statuses;
    public SyncStreamStore(JdbcTemplate jdbc, SyncOrderStatus statuses) { this.jdbc = jdbc; this.statuses = statuses; }

    // Caller holds the server-state share lock in a READ_COMMITTED transaction.
    public Published publish(SyncCursor.Namespace namespace) {
        var warehouse = Ids.bytes(namespace.warehouse());
        jdbc.update("""
            INSERT INTO sync_stream VALUES(?,?,'ORDER',1) ON DUPLICATE KEY UPDATE next_seq=next_seq
            """, namespace.epoch(), warehouse);
        long next = jdbc.queryForObject("""
            SELECT next_seq FROM sync_stream WHERE recovery_epoch=? AND warehouse_id=? AND stream_type='ORDER' FOR UPDATE
            """, Long.class, namespace.epoch(), warehouse);
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(id),BIN_TO_UUID(order_id),entity_version,event_type,payload,created_at
            FROM order_sync_outbox WHERE recovery_epoch=? AND warehouse_id=? AND published_seq IS NULL
            ORDER BY created_at,id LIMIT 200 FOR UPDATE SKIP LOCKED
            """, (rs, n) -> new Event(UUID.fromString(rs.getString(1)), 0, UUID.fromString(rs.getString(2)), rs.getLong(3),
                rs.getString(4), statuses.from(rs.getString(4), rs.getString(5)), rs.getTimestamp(6).toInstant()), namespace.epoch(), warehouse);
        for (var row : rows) {
            project(namespace, row);
            jdbc.update("UPDATE order_sync_outbox SET published_seq=? WHERE id=? AND published_seq IS NULL", next++, Ids.bytes(row.id()));
        }
        jdbc.update("UPDATE sync_stream SET next_seq=? WHERE recovery_epoch=? AND warehouse_id=? AND stream_type='ORDER'",
            next, namespace.epoch(), warehouse);
        return new Published(next - 1, rows.size() == 200);
    }
    private void project(SyncCursor.Namespace namespace, Event row) {
        jdbc.update("""
            INSERT INTO sync_order_projection VALUES(?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE status=IF(VALUES(row_version)>row_version,VALUES(status),status),
              updated_at=IF(VALUES(row_version)>row_version,VALUES(updated_at),updated_at),
              row_version=GREATEST(row_version,VALUES(row_version))
            """, namespace.epoch(), Ids.bytes(namespace.warehouse()), Ids.bytes(row.orderId()), row.status(),
            row.entityVersion(), java.sql.Timestamp.from(row.createdAt()));
    }
    public List<Event> eventsAfter(SyncCursor.Namespace namespace, long cursor, int limit) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(id),published_seq,BIN_TO_UUID(order_id),entity_version,event_type,payload,created_at
            FROM order_sync_outbox WHERE recovery_epoch=? AND warehouse_id=? AND published_seq>?
            ORDER BY published_seq LIMIT ?
            """, (rs, n) -> new Event(UUID.fromString(rs.getString(1)), rs.getLong(2), UUID.fromString(rs.getString(3)),
                rs.getLong(4), rs.getString(5), statuses.from(rs.getString(5), rs.getString(6)), rs.getTimestamp(7).toInstant()),
            namespace.epoch(), Ids.bytes(namespace.warehouse()), cursor, limit + 1);
    }
    public record Published(long highWatermark, boolean pending) { }
    public record Event(UUID id, long sequence, UUID orderId, long entityVersion, String eventType, String status, Instant createdAt) { }
}
