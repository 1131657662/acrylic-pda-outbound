package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ImportQueries {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String COLUMNS = """
        SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(warehouse_id) AS warehouse_id, original_filename,
        file_sha256,parser_version,status,total_rows,valid_rows,error_rows,duplicate_rows,ignored_rows,
        product_count,total_weight_g,created_at,BIN_TO_UUID(published_snapshot_id) AS published_snapshot_id FROM inventory_import
        """;
    public ImportQueries(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ImportSummary detail(UUID warehouse, UUID id) {
        var results = jdbc.query(COLUMNS + " WHERE warehouse_id=:warehouse AND id=:id",
            Map.of("warehouse", Ids.bytes(warehouse), "id", Ids.bytes(id)), this::map);
        if (results.isEmpty()) throw new BusinessException(404, "IMPORT_NOT_FOUND", "导入记录不存在或无权访问");
        return results.getFirst();
    }

    public Page<ImportSummary> list(UUID warehouse, String cursor, int size) {
        int limit = Page.checkedLimit(size);
        var params = new MapSqlParameterSource("warehouse", Ids.bytes(warehouse)).addValue("limit", limit + 1);
        String after = "";
        if (cursor != null && !cursor.isEmpty()) {
            ImportCursor point = ImportCursor.decode(cursor);
            params.addValue("time", Timestamp.from(point.createdAt())).addValue("id", Ids.bytes(point.id()));
            after = " AND (created_at < :time OR (created_at = :time AND id < :id))";
        }
        var rows = jdbc.query(COLUMNS + " WHERE warehouse_id=:warehouse" + after +
            " ORDER BY created_at DESC,id DESC LIMIT :limit", params, this::map);
        return Page.from(rows, limit, ImportCursor::encode);
    }

    private ImportSummary map(ResultSet rs, int index) throws SQLException {
        return new ImportSummary(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("warehouse_id")),
            rs.getString("original_filename"), rs.getString("file_sha256"), rs.getString("parser_version"),
            rs.getString("status"), rs.getInt("total_rows"), rs.getInt("valid_rows"), rs.getInt("error_rows"),
            rs.getInt("duplicate_rows"), rs.getInt("ignored_rows"), rs.getInt("product_count"),
            rs.getString("total_weight_g"), rs.getTimestamp("created_at").toInstant(), rs.getString("published_snapshot_id"));
    }
}
