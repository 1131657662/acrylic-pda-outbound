package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.Page;
import com.acrylic.outbound.inventory.parser.ImportRow;
import com.acrylic.outbound.inventory.parser.ParsedBale;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ImportRowStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ImportRowStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public void insert(UUID id, List<ImportRow> rows) {
        jdbc.batchUpdate("""
            INSERT INTO inventory_import_row(import_id,line_no,raw_text,parsed_fields,error_code,error_detail)
            VALUES(?,?,?,?,?,?)
            """, rows, 500, (statement, row) -> {
            statement.setBytes(1, Ids.bytes(id));
            statement.setInt(2, row.lineNo());
            statement.setString(3, row.rawText());
            statement.setString(4, encode(row.parsedFields()));
            statement.setString(5, row.errorCode());
            statement.setString(6, row.errorDetail());
        });
    }

    public Page<ImportRow> list(UUID id, RowPage page) {
        int limit = Page.checkedLimit(page.limit());
        if (page.afterLine() < 0) throw new BusinessException(400, "INVALID_CURSOR", "行号游标不能为负数");
        String filter = page.errorsOnly() ? " AND error_code IS NOT NULL" : "";
        var rows = jdbc.query("""
            SELECT line_no,raw_text,parsed_fields,error_code,error_detail FROM inventory_import_row
            WHERE import_id=? AND line_no>?
            """ + filter + " ORDER BY line_no LIMIT ?", (rs, n) -> new ImportRow(rs.getInt(1),
            rs.getString(2), decode(rs.getString(3)), rs.getString(4), rs.getString(5)),
            Ids.bytes(id), page.afterLine(), limit + 1);
        return Page.from(rows, limit, row -> Integer.toString(row.lineNo()));
    }

    private String encode(ParsedBale bale) {
        if (bale == null) return null;
        try { return json.writeValueAsString(bale); }
        catch (JsonProcessingException error) { throw new IllegalStateException(error); }
    }
    private ParsedBale decode(String value) {
        if (value == null) return null;
        try { return json.readValue(value, ParsedBale.class); }
        catch (JsonProcessingException error) { throw new IllegalStateException(error); }
    }
    public record RowPage(int afterLine, boolean errorsOnly, int limit) { }
}
