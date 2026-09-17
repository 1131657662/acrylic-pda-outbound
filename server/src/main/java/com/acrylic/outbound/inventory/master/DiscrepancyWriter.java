package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.JsonText;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DiscrepancyWriter {
    private final JdbcTemplate jdbc;
    private final JsonText json;
    public DiscrepancyWriter(JdbcTemplate jdbc, JsonText json) { this.jdbc = jdbc; this.json = json; }
    public void write(Changes input) {
        var changes = input.preview().differences().changes().stream().filter(c -> !c.type().equals("ADDED")).toList();
        jdbc.batchUpdate("""
            INSERT INTO inventory_discrepancy(id,warehouse_id,bale_id,old_snapshot_id,new_snapshot_id,type,old_values,new_values,created_at)
            VALUES(?,?,(SELECT id FROM bale WHERE barcode=?),?,?,?,?,?,UTC_TIMESTAMP(3))
            """, changes, 500, (s, change) -> {
            s.setBytes(1, Ids.bytes(Ids.next())); s.setBytes(2, Ids.bytes(input.warehouse())); s.setString(3, change.barcode());
            s.setBytes(4, input.preview().baseline().activeSnapshotId() == null ? null : Ids.bytes(UUID.fromString(input.preview().baseline().activeSnapshotId())));
            s.setBytes(5, Ids.bytes(input.snapshot())); s.setString(6, change.type());
            s.setString(7, json.encode(change.before())); s.setString(8, json.encode(change.after()));
        });
        bookConflicts(input);
    }
    private void bookConflicts(Changes input) {
        var held = jdbc.query("""
            SELECT p.bale_id,p.latest_snapshot_id FROM inventory_position p
            INNER JOIN inventory_snapshot_bale s ON s.bale_id=p.bale_id
            WHERE s.snapshot_id=? AND p.book_status='DEDUCTED'
            """, (rs, n) -> new Held(rs.getBytes(1), rs.getBytes(2)), Ids.bytes(input.snapshot()));
        jdbc.batchUpdate("""
            INSERT INTO inventory_discrepancy(id,warehouse_id,bale_id,old_snapshot_id,new_snapshot_id,type,old_values,new_values,created_at)
            VALUES(?,?,?,?,?,'UPSTREAM_BOOK_CONFLICT','{"bookStatus":"DEDUCTED"}','{"upstreamPresent":true}',UTC_TIMESTAMP(3))
            """, held, 500, (s, row) -> {
            s.setBytes(1, Ids.bytes(Ids.next())); s.setBytes(2, Ids.bytes(input.warehouse())); s.setBytes(3, row.bale());
            s.setBytes(4, row.previous()); s.setBytes(5, Ids.bytes(input.snapshot()));
        });
    }
    private record Held(byte[] bale, byte[] previous) { }
    public record Changes(UUID warehouse, UUID snapshot, PublicationPreview.Preview preview) { }
}
