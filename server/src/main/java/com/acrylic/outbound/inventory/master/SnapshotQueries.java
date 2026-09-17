package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.Page;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SnapshotQueries {
    private final JdbcTemplate jdbc;
    public SnapshotQueries(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String SELECT = """
        SELECT BIN_TO_UUID(id),version,BIN_TO_UUID(import_id),row_count,total_weight_g,mapping_revision,manifest_sha256,published_at
        FROM inventory_snapshot WHERE warehouse_id=?
        """;
    public Receipt byId(UUID warehouse, UUID id) {
        var rows = jdbc.query(SELECT + " AND id=?", this::map, Ids.bytes(warehouse), Ids.bytes(id));
        if (rows.isEmpty()) throw new BusinessException(404, "SNAPSHOT_NOT_FOUND", "库存版本不存在或无权访问");
        return rows.getFirst();
    }
    public Page<Receipt> list(UUID warehouse, long beforeVersion, int size) {
        int limit = Page.checkedLimit(size);
        if (beforeVersion <= 0) throw new BusinessException(400, "INVALID_CURSOR", "版本游标无效");
        return Page.from(jdbc.query(SELECT + " AND version<? ORDER BY version DESC LIMIT ?", this::map,
            Ids.bytes(warehouse), beforeVersion, limit + 1), limit, Receipt::version);
    }
    private Receipt map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Receipt(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5),
            rs.getString(6), rs.getString(7), rs.getTimestamp(8).toInstant().toString());
    }
    public record Receipt(String id, String version, String importId, int rowCount, String totalWeightGrams,
                          String mappingRevision, String manifestSha256, String publishedAt) { }
}
