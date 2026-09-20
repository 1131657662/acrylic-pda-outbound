package com.acrylic.outbound.sync;

import com.acrylic.outbound.common.Ids;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BarcodeStreamStore {
    private final JdbcTemplate jdbc;
    public BarcodeStreamStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    // Caller holds recovery share lock; READ COMMITTED reads only committed business state.
    public Published publish(BarcodeCursor.Namespace ns) {
        initialize(ns);
        byte[] warehouse=Ids.bytes(ns.warehouse());
        long next=jdbc.queryForObject("SELECT next_seq FROM barcode_sync_stream WHERE recovery_epoch=? AND warehouse_id=? FOR UPDATE",
            Long.class,ns.epoch(),warehouse);
        var pending=jdbc.query("""
            SELECT BIN_TO_UUID(bale_id),row_version FROM barcode_sync_pending
            WHERE recovery_epoch=? AND warehouse_id=? AND pending=1 ORDER BY bale_id LIMIT 200 FOR UPDATE SKIP LOCKED
            """,(rs,n)->new Pending(UUID.fromString(rs.getString(1)),rs.getLong(2)),ns.epoch(),warehouse);
        for(var change:pending) publishRow(ns,change,next++);
        jdbc.update("UPDATE barcode_sync_stream SET next_seq=? WHERE recovery_epoch=? AND warehouse_id=?",next,ns.epoch(),warehouse);
        boolean more=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM barcode_sync_pending WHERE recovery_epoch=? AND warehouse_id=? AND pending=1)",
            Boolean.class,ns.epoch(),warehouse);
        return new Published(next-1,more);
    }
    private void initialize(BarcodeCursor.Namespace ns) {
        jdbc.update("INSERT INTO barcode_sync_stream VALUES(?,?,1,0) ON DUPLICATE KEY UPDATE next_seq=next_seq",ns.epoch(),Ids.bytes(ns.warehouse()));
        boolean seeded=jdbc.queryForObject("SELECT seeded FROM barcode_sync_stream WHERE recovery_epoch=? AND warehouse_id=? FOR UPDATE",
            Boolean.class,ns.epoch(),Ids.bytes(ns.warehouse()));
        if(seeded) return;
        jdbc.update("""
            INSERT IGNORE INTO barcode_sync_pending(recovery_epoch,warehouse_id,bale_id,row_version)
            SELECT ?,warehouse_id,id,1 FROM bale WHERE warehouse_id=? ORDER BY id
            """,ns.epoch(),Ids.bytes(ns.warehouse()));
        jdbc.update("UPDATE barcode_sync_stream SET seeded=1 WHERE recovery_epoch=? AND warehouse_id=?",ns.epoch(),Ids.bytes(ns.warehouse()));
    }
    private void publishRow(BarcodeCursor.Namespace ns, Pending pending, long sequence) {
        var row=current(pending);var timestamp=java.sql.Timestamp.from(row.updatedAt());
        jdbc.update("INSERT INTO barcode_sync_event VALUES(?,?,?,?,?,?,?,?,?,?)",ns.epoch(),Ids.bytes(ns.warehouse()),sequence,
            Ids.bytes(Ids.next()),Ids.bytes(row.baleId()),row.barcode(),row.status(),row.dispatchCycle(),row.rowVersion(),timestamp);
        jdbc.update("""
            INSERT INTO barcode_sync_projection VALUES(?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE status=VALUES(status),dispatch_cycle=VALUES(dispatch_cycle),
                row_version=VALUES(row_version),updated_at=VALUES(updated_at)
            """,ns.epoch(),Ids.bytes(ns.warehouse()),Ids.bytes(row.baleId()),row.barcode(),row.status(),row.dispatchCycle(),row.rowVersion(),timestamp);
        jdbc.update("UPDATE barcode_sync_pending SET published_version=? WHERE recovery_epoch=? AND warehouse_id=? AND bale_id=?",
            pending.version(),ns.epoch(),Ids.bytes(ns.warehouse()),Ids.bytes(pending.bale()));
    }
    private Item current(Pending pending) {
        return jdbc.queryForObject("""
            SELECT b.barcode,b.dispatch_cycle,CASE
              WHEN c.state='QUARANTINED' THEN 'QUARANTINED'
              WHEN c.state='DISPATCHED' OR p.book_status='DEDUCTED' THEN 'DISPATCHED'
              WHEN c.bale_id IS NOT NULL THEN 'RESERVED'
              WHEN p.bale_id IS NULL OR p.upstream_present=0 OR p.blocked_reason IS NOT NULL THEN 'UNAVAILABLE'
              ELSE 'AVAILABLE' END
            FROM bale b LEFT JOIN inventory_position p ON p.bale_id=b.id LEFT JOIN bale_commitment c ON c.bale_id=b.id WHERE b.id=?
            """,(rs,n)->new Item(pending.bale(),rs.getString(1),rs.getString(3),rs.getLong(2),pending.version(),Instant.now()),Ids.bytes(pending.bale()));
    }
    public List<Event> eventsAfter(BarcodeCursor.Namespace ns,long cursor,int limit) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(id),sequence_no,BIN_TO_UUID(bale_id),barcode,status,dispatch_cycle,row_version,created_at
            FROM barcode_sync_event WHERE recovery_epoch=? AND warehouse_id=? AND sequence_no>? ORDER BY sequence_no LIMIT ?
            """,(rs,n)->new Event(rs.getString(1),rs.getLong(2),new Item(UUID.fromString(rs.getString(3)),rs.getString(4),rs.getString(5),
                rs.getLong(6),rs.getLong(7),rs.getTimestamp(8).toInstant())),ns.epoch(),Ids.bytes(ns.warehouse()),cursor,limit+1);
    }
    public record Published(long highWatermark,boolean pending) { }
    private record Pending(UUID bale,long version) { }
    public record Item(UUID baleId,String barcode,String status,long dispatchCycle,long rowVersion,Instant updatedAt) { }
    public record Event(String id,long sequence,Item item) { }
}
