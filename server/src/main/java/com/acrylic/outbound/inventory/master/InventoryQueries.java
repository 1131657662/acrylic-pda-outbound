package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryQueries {
    private final NamedParameterJdbcTemplate jdbc;
    public InventoryQueries(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Page<Bale> bales(UUID warehouse, Filter filter) {
        int limit = Page.checkedLimit(filter.limit());
        if (!filter.presence().matches("all|present|missing")) throw new BusinessException(400, "INVALID_REQUEST", "库存来源状态无效");
        Map<String, Object> args = new HashMap<>(Map.of("warehouse", Ids.bytes(warehouse), "after", filter.afterBarcode(), "limit", limit + 1));
        String sql = """
            SELECT b.barcode,b.dispatch_cycle,s.delivery_code,s.model_snapshot,s.name_snapshot,s.weight_g,s.raw_weight,
              p.upstream_present,p.book_status,p.blocked_reason,BIN_TO_UUID(p.last_seen_snapshot_id),BIN_TO_UUID(p.latest_snapshot_id)
            FROM bale b INNER JOIN inventory_position p ON p.bale_id=b.id
              INNER JOIN inventory_snapshot_bale s ON s.snapshot_id=p.last_seen_snapshot_id AND s.bale_id=b.id
            WHERE b.warehouse_id=:warehouse AND b.barcode>:after
            """;
        if (!filter.barcode().isEmpty()) { sql += " AND b.barcode=:barcode"; args.put("barcode", filter.barcode()); }
        if (!filter.deliveryCode().isEmpty()) { sql += " AND s.delivery_code=:code"; args.put("code", filter.deliveryCode()); }
        if (!filter.presence().equals("all")) { sql += " AND p.upstream_present=:present"; args.put("present", filter.presence().equals("present")); }
        var rows = jdbc.query(sql + " ORDER BY b.barcode LIMIT :limit", args, (rs, n) -> new Bale(rs.getString(1), rs.getString(2),
            rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8),
            rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12)));
        return Page.from(rows, limit, Bale::barcode);
    }
    public Page<Discrepancy> discrepancies(UUID warehouse, UUID before, int size) {
        int limit = Page.checkedLimit(size);
        Map<String, Object> args = new HashMap<>(Map.of("warehouse", Ids.bytes(warehouse), "limit", limit + 1));
        String sql = """
            SELECT BIN_TO_UUID(d.id),b.barcode,d.type,d.status,BIN_TO_UUID(d.old_snapshot_id),BIN_TO_UUID(d.new_snapshot_id),
              d.old_values,d.new_values,d.created_at FROM inventory_discrepancy d INNER JOIN bale b ON b.id=d.bale_id
            WHERE d.warehouse_id=:warehouse AND d.status='OPEN'
            """;
        if (before != null) { sql += " AND d.id<:before"; args.put("before", Ids.bytes(before)); }
        return Page.from(jdbc.query(sql + " ORDER BY d.id DESC LIMIT :limit", args, (rs, n) -> new Discrepancy(rs.getString(1),
            rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
            rs.getTimestamp(9).toInstant().toString())), limit, Discrepancy::id);
    }
    public record Filter(String barcode, String deliveryCode, String presence, String afterBarcode, int limit) { }
    public record Bale(String barcode, String dispatchCycle, String deliveryCode, String model, String name,
                       String weightGrams, String rawWeight, boolean upstreamPresent, String bookStatus,
                       String blockedReason, String lastSeenSnapshotId, String evaluatedSnapshotId) { }
    public record Discrepancy(String id, String barcode, String type, String status, String oldSnapshotId,
                              String newSnapshotId, String oldValues, String newValues, String createdAt) { }
}
