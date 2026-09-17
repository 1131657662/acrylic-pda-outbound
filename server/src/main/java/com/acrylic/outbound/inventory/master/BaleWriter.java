package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.parser.ParsedBale;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BaleWriter {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public BaleWriter(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) { this.jdbc = jdbc; this.named = named; }
    public Map<String, UUID> ensure(UUID warehouse, List<ParsedBale> incoming) {
        var ordered = incoming.stream().sorted(Comparator.comparing(ParsedBale::barcode)).toList();
        jdbc.batchUpdate("""
            INSERT INTO bale(id,barcode,warehouse_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE id=id
            """, ordered, 500, (s, bale) -> {
            s.setBytes(1, Ids.bytes(Ids.next())); s.setString(2, bale.barcode()); s.setBytes(3, Ids.bytes(warehouse));
        });
        Map<String, UUID> result = new HashMap<>();
        for (int i = 0; i < ordered.size(); i += 1000) {
            var codes = ordered.subList(i, Math.min(i + 1000, ordered.size())).stream().map(ParsedBale::barcode).toList();
            named.query("SELECT BIN_TO_UUID(id),barcode,BIN_TO_UUID(warehouse_id) FROM bale WHERE barcode IN (:codes)",
                Map.of("codes", codes), rs -> {
                if (!warehouse.toString().equals(rs.getString(3)))
                    throw new BusinessException(409, "BARCODE_WAREHOUSE_CONFLICT", "文件含其他仓库的条码，不能通过导入转移归属");
                result.put(rs.getString(2), UUID.fromString(rs.getString(1)));
            });
        }
        if (result.size() != incoming.size()) throw new IllegalStateException("Bale identity count mismatch");
        return result;
    }
}
