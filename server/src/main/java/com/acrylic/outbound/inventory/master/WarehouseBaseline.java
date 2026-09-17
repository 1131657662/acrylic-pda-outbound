package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WarehouseBaseline {
    private final JdbcTemplate jdbc;
    public WarehouseBaseline(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Baseline read(UUID warehouse, boolean lock) {
        var rows = jdbc.query("SELECT BIN_TO_UUID(active_inventory_snapshot_id),mapping_revision FROM warehouse WHERE id=?" +
            (lock ? " FOR UPDATE" : ""), (rs, n) -> new Baseline(rs.getString(1), rs.getString(2)), Ids.bytes(warehouse));
        if (rows.isEmpty()) throw new BusinessException(404, "WAREHOUSE_NOT_FOUND", "仓库不存在");
        return rows.getFirst();
    }
    public record Baseline(String activeSnapshotId, String mappingRevision) { }
}
