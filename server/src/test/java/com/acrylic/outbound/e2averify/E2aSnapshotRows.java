package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Captures every existing table column as driver bytes, preserving NULL and binary values. */
public final class E2aSnapshotRows {
    private final JdbcTemplate jdbc;
    public E2aSnapshotRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ObjectNode capture() {
        var tables = JsonNodeFactory.instance.objectNode();
        for (String table : jdbc.queryForList("SHOW TABLES", String.class)) {
            if (table.equals("flyway_schema_history")) continue;
            List<String> columns = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION", String.class, table);
            var snapshot = tables.putObject(table);
            var columnNode = snapshot.putArray("columns"); columns.forEach(columnNode::add);
            snapshot.set("rows", rows(table, columns));
        }
        return tables;
    }

    public ArrayNode rows(String table, List<String> columns) {
        List<String> primary = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS "
            + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME='PRIMARY' ORDER BY SEQ_IN_INDEX",
            String.class, table);
        if (primary.isEmpty()) throw new IllegalStateException("Missing primary key: " + table);
        var result = JsonNodeFactory.instance.arrayNode();
        jdbc.query("SELECT " + identifiers(columns) + " FROM " + identifier(table) + " ORDER BY " + identifiers(primary),
            (ResultSet rs) -> {
                var row = result.addArray();
                for (int index = 1; index <= columns.size(); index++) {
                    byte[] bytes = rs.getBytes(index);
                    if (bytes == null) row.addNull(); else row.add(Base64.getEncoder().encodeToString(bytes));
                }
            });
        return result;
    }

    static String identifier(String value) {
        if (!value.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Unexpected identifier: " + value);
        return "`" + value + "`";
    }

    private String identifiers(List<String> values) { return String.join(",", values.stream().map(E2aSnapshotRows::identifier).toList()); }
}
