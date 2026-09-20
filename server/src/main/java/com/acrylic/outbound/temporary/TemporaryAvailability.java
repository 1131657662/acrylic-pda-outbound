package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.Ids;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TemporaryAvailability {
    private final JdbcTemplate jdbc;
    public TemporaryAvailability(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Snapshot read(UUID warehouse,String epoch) {
        // Seed legacy stock before taking the cut so its next change advances beyond the packaged version.
        jdbc.update("""
            INSERT IGNORE INTO barcode_sync_pending(recovery_epoch,warehouse_id,bale_id,row_version)
            SELECT ?,warehouse_id,id,1 FROM bale WHERE warehouse_id=? ORDER BY id
            """,epoch,Ids.bytes(warehouse));
        // One consistent statement reads committed business state, change versions and stream watermark together.
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(b.id),b.barcode,b.dispatch_cycle,COALESCE(v.row_version,1),
              COALESCE(s.next_seq-1,0),CASE WHEN c.state='QUARANTINED' THEN 'QUARANTINED'
              WHEN c.state='DISPATCHED' OR p.book_status='DEDUCTED' THEN 'DISPATCHED'
              WHEN c.bale_id IS NOT NULL THEN 'RESERVED'
              WHEN p.bale_id IS NULL OR p.upstream_present=0 OR p.blocked_reason IS NOT NULL THEN 'UNAVAILABLE'
              ELSE 'AVAILABLE' END
            FROM bale b LEFT JOIN inventory_position p ON p.bale_id=b.id LEFT JOIN bale_commitment c ON c.bale_id=b.id
            LEFT JOIN barcode_sync_pending v ON v.bale_id=b.id AND v.warehouse_id=b.warehouse_id AND v.recovery_epoch=?
            LEFT JOIN barcode_sync_stream s ON s.warehouse_id=b.warehouse_id AND s.recovery_epoch=?
            WHERE b.warehouse_id=? ORDER BY b.barcode
            """,(rs,n)->new Row(new Item(rs.getString(1),rs.getString(2),rs.getString(6),rs.getString(3),rs.getString(4)),rs.getLong(5)),
            epoch,epoch,Ids.bytes(warehouse));
        long high=rows.isEmpty()?0:rows.getFirst().high();
        String cursor=Base64.getUrlEncoder().withoutPadding().encodeToString(("1|"+epoch+"|"+warehouse+"|BARCODE|"+high).getBytes(StandardCharsets.US_ASCII));
        return new Snapshot("BARCODE",epoch,warehouse.toString(),Long.toString(high),cursor,rows.stream().map(Row::item).toList());
    }
    private record Row(Item item,long high) { }
    public record Item(String baleId,String barcode,String status,String dispatchCycle,String rowVersion) { }
    public record Snapshot(String streamType,String recoveryEpoch,String warehouseId,String highWatermark,String nextCursor,List<Item> items) { }
}
