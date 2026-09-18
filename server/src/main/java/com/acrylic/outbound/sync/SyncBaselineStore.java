package com.acrylic.outbound.sync;

import com.acrylic.outbound.common.Ids;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SyncBaselineStore {
    private final JdbcTemplate jdbc;
    public SyncBaselineStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public UUID create(SyncCursor.Namespace namespace, long highWatermark) {
        UUID id = Ids.next();
        jdbc.update("""
            INSERT INTO sync_baseline VALUES(?,?,?,'ORDER',?,UTC_TIMESTAMP(3),TIMESTAMPADD(MINUTE,15,UTC_TIMESTAMP(3)))
            """, Ids.bytes(id), namespace.epoch(), Ids.bytes(namespace.warehouse()), highWatermark);
        jdbc.update("""
            INSERT INTO sync_baseline_order SELECT ?,order_id,status,row_version,updated_at
            FROM sync_order_projection WHERE recovery_epoch=? AND warehouse_id=?
            """, Ids.bytes(id), namespace.epoch(), Ids.bytes(namespace.warehouse()));
        return id;
    }
    public Meta meta(UUID id) {
        var rows = jdbc.query("""
            SELECT recovery_epoch,BIN_TO_UUID(warehouse_id),high_watermark,expires_at
            FROM sync_baseline WHERE id=?
            """, (rs, n) -> new Meta(new SyncCursor.Namespace(rs.getString(1), UUID.fromString(rs.getString(2))),
                rs.getLong(3), rs.getTimestamp(4).toInstant()), Ids.bytes(id));
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public List<Item> page(UUID baseline, UUID cursor, int limit) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(order_id),status,row_version,updated_at FROM sync_baseline_order
            WHERE baseline_id=? AND order_id>? ORDER BY order_id LIMIT ?
            """, (rs, n) -> new Item(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3), rs.getTimestamp(4).toInstant()),
            Ids.bytes(baseline), cursor == null ? new byte[16] : Ids.bytes(cursor), limit + 1);
    }
    public record Meta(SyncCursor.Namespace namespace, long highWatermark, Instant expiresAt) { }
    public record Item(UUID orderId, String status, long rowVersion, Instant updatedAt) { }
}
