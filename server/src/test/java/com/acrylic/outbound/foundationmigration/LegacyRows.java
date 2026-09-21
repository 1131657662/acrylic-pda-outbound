package com.acrylic.outbound.foundationmigration;

import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Original columns are read as driver bytes; binary, NULL and text cannot collapse. */
public final class LegacyRows {
    private final JdbcTemplate jdbc;
    public LegacyRows(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ObjectNode capture() {
        var tables = JsonNodeFactory.instance.objectNode();
        for (String table : jdbc.queryForList("SHOW TABLES", String.class)) {
            if (table.equals("flyway_schema_history")) continue;
            var columns = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION", String.class, table);
            var snapshot = tables.putObject(table);
            columns.forEach(snapshot.putArray("columns")::add);
            snapshot.set("rows", rows(table, columns));
        }
        return tables;
    }

    public ArrayNode rows(String table, List<String> columns) {
        var primary = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME='PRIMARY' ORDER BY SEQ_IN_INDEX",
                String.class, table);
        if (primary.isEmpty()) throw new IllegalStateException("Expected a primary key: " + table);
        var result = JsonNodeFactory.instance.arrayNode();
        jdbc.query("SELECT " + names(columns) + " FROM " + name(table) + " ORDER BY " + names(primary), rs -> {
            var row = result.addArray();
            for (int i = 1; i <= columns.size(); i++) {
                byte[] bytes = rs.getBytes(i);
                if (bytes == null) row.addNull(); else row.add(Base64.getEncoder().encodeToString(bytes));
            }
        });
        return result;
    }

    private String names(List<String> names) { return String.join(",", names.stream().map(LegacyRows::name).toList()); }
    static String name(String name) {
        if (!name.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Unexpected schema identifier");
        return "`" + name + "`";
    }
}
