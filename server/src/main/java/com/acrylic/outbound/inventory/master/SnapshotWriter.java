package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.inventory.parser.ParsedBale;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SnapshotWriter {
    private final JdbcTemplate jdbc;
    private final BaleWriter bales;
    private final DiscrepancyWriter discrepancies;
    private final JsonText json;
    public SnapshotWriter(JdbcTemplate jdbc, BaleWriter bales, DiscrepancyWriter discrepancies, JsonText json) {
        this.jdbc = jdbc; this.bales = bales; this.discrepancies = discrepancies; this.json = json;
    }
    public UUID create(Data data) {
        UUID snapshot = Ids.next();
        Map<String, UUID> baleIds = bales.ensure(data.warehouse(), data.rows());
        header(snapshot, data);
        jdbc.batchUpdate("""
            INSERT INTO inventory_snapshot_bale(snapshot_id,bale_id,delivery_code,product_id,model_snapshot,name_snapshot,weight_g,raw_weight)
            VALUES(?,?,?,?,?,?,?,?)
            """, data.rows(), 500, (s, row) -> {
            s.setBytes(1, Ids.bytes(snapshot)); s.setBytes(2, Ids.bytes(baleIds.get(row.barcode()))); s.setString(3, row.deliveryCode());
            s.setBytes(4, Ids.bytes(data.products().get(row.deliveryCode()))); s.setString(5, row.model()); s.setString(6, row.name());
            s.setLong(7, Long.parseLong(row.weightGrams())); s.setString(8, row.rawWeight());
        });
        discrepancies.write(new DiscrepancyWriter.Changes(data.warehouse(), snapshot, data.preview()));
        positions(snapshot, data.warehouse());
        activate(snapshot, data);
        return snapshot;
    }
    private void header(UUID id, Data data) {
        long previous = jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM inventory_snapshot WHERE warehouse_id=?", Long.class, Ids.bytes(data.warehouse()));
        String manifest = json.digest(data.rows().stream().sorted(Comparator.comparing(ParsedBale::barcode)).toList());
        jdbc.update("""
            INSERT INTO inventory_snapshot(id,warehouse_id,version,import_id,status,row_count,total_weight_g,mapping_revision,manifest_sha256,published_by,published_at)
            VALUES(?,?,?,?,'ACTIVE',?,?,(SELECT mapping_revision FROM warehouse WHERE id=?),?,?,UTC_TIMESTAMP(3))
            """, Ids.bytes(id), Ids.bytes(data.warehouse()), Math.addExact(previous, 1), Ids.bytes(data.preview().imported().id()),
            data.rows().size(), Long.parseLong(data.preview().imported().totalWeightGrams()), Ids.bytes(data.warehouse()), manifest, Ids.bytes(data.actor()));
    }
    private void positions(UUID snapshot, UUID warehouse) {
        jdbc.update("""
            UPDATE inventory_position p INNER JOIN bale b ON b.id=p.bale_id
            SET p.upstream_present=0,p.latest_snapshot_id=?,p.row_version=p.row_version+1
            WHERE b.warehouse_id=? AND p.upstream_present=1
              AND NOT EXISTS(SELECT 1 FROM inventory_snapshot_bale s WHERE s.snapshot_id=? AND s.bale_id=p.bale_id)
            """, Ids.bytes(snapshot), Ids.bytes(warehouse), Ids.bytes(snapshot));
        jdbc.update("""
            INSERT INTO inventory_position(bale_id,latest_snapshot_id,last_seen_snapshot_id,upstream_present)
            SELECT bale_id,snapshot_id,snapshot_id,1 FROM inventory_snapshot_bale WHERE snapshot_id=?
            ON DUPLICATE KEY UPDATE latest_snapshot_id=VALUES(latest_snapshot_id),last_seen_snapshot_id=VALUES(last_seen_snapshot_id),
              upstream_present=1,row_version=row_version+1
            """, Ids.bytes(snapshot));
    }
    private void activate(UUID snapshot, Data data) {
        String previous = data.preview().baseline().activeSnapshotId();
        if (previous != null) jdbc.update("UPDATE inventory_snapshot SET status='SUPERSEDED' WHERE id=?", Ids.bytes(UUID.fromString(previous)));
        jdbc.update("UPDATE warehouse SET active_inventory_snapshot_id=? WHERE id=?", Ids.bytes(snapshot), Ids.bytes(data.warehouse()));
        jdbc.update("UPDATE inventory_import SET published_snapshot_id=? WHERE id=?", Ids.bytes(snapshot), Ids.bytes(data.preview().imported().id()));
    }
    public record Data(UUID warehouse, UUID actor, PublicationPreview.Preview preview,
                       List<ParsedBale> rows, Map<String, UUID> products) { }
}
