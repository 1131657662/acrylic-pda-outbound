package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.parser.ParsedBale;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MasterDataReader {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public MasterDataReader(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public List<ParsedBale> imported(UUID id) {
        return jdbc.query("SELECT parsed_fields FROM inventory_import_row WHERE import_id=? AND error_code IS NULL ORDER BY line_no",
            (rs, n) -> decode(rs.getString(1)), Ids.bytes(id));
    }
    public List<ParsedBale> snapshot(String id) {
        if (id == null) return List.of();
        return jdbc.query("""
            SELECT s.model_snapshot,s.name_snapshot,b.barcode,s.delivery_code,s.raw_weight,s.weight_g
            FROM inventory_snapshot_bale s INNER JOIN bale b ON b.id=s.bale_id WHERE s.snapshot_id=? ORDER BY b.barcode
            """, (rs, n) -> new ParsedBale(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getString(5), rs.getString(6)), Ids.bytes(UUID.fromString(id)));
    }
    private ParsedBale decode(String text) {
        try { return json.readValue(text, ParsedBale.class); }
        catch (java.io.IOException error) { throw new IllegalStateException("Corrupt import staging row", error); }
    }
}
