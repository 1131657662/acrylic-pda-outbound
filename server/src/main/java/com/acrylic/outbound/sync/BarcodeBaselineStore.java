package com.acrylic.outbound.sync;

import com.acrylic.outbound.common.Ids;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BarcodeBaselineStore {
    private final JdbcTemplate jdbc;
    public BarcodeBaselineStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public UUID create(BarcodeCursor.Namespace ns,long high) {
        UUID id=Ids.next();
        jdbc.update("INSERT INTO barcode_sync_baseline VALUES(?,?,?,?,UTC_TIMESTAMP(3),TIMESTAMPADD(MINUTE,15,UTC_TIMESTAMP(3)))",
            Ids.bytes(id),ns.epoch(),Ids.bytes(ns.warehouse()),high);
        jdbc.update("""
            INSERT INTO barcode_sync_baseline_row SELECT ?,bale_id,barcode,status,dispatch_cycle,row_version,updated_at
            FROM barcode_sync_projection WHERE recovery_epoch=? AND warehouse_id=?
            """,Ids.bytes(id),ns.epoch(),Ids.bytes(ns.warehouse()));
        return id;
    }
    public Meta meta(UUID id) {
        var rows=jdbc.query("SELECT recovery_epoch,BIN_TO_UUID(warehouse_id),high_watermark,expires_at FROM barcode_sync_baseline WHERE id=?",
            (rs,n)->new Meta(new BarcodeCursor.Namespace(rs.getString(1),UUID.fromString(rs.getString(2))),rs.getLong(3),rs.getTimestamp(4).toInstant()),Ids.bytes(id));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public List<BarcodeStreamStore.Item> page(UUID baseline,UUID cursor,int limit) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(bale_id),barcode,status,dispatch_cycle,row_version,updated_at FROM barcode_sync_baseline_row
            WHERE baseline_id=? AND bale_id>? ORDER BY bale_id LIMIT ?
            """,(rs,n)->new BarcodeStreamStore.Item(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getLong(5),rs.getTimestamp(6).toInstant()),
            Ids.bytes(baseline),cursor==null?new byte[16]:Ids.bytes(cursor),limit+1);
    }
    public record Meta(BarcodeCursor.Namespace namespace,long highWatermark,Instant expiresAt) { }
}
