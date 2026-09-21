package com.acrylic.outbound.foundationmigration;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

record LegacyCopyRow(String table, Map<String, Object> values, List<String> primary) {
    static List<LegacyCopyRow> from(String table, JsonNode snapshot, JdbcTemplate jdbc) {
        var types = new HashMap<String, String>();
        var generated = new HashSet<String>();
        jdbc.query("SELECT COLUMN_NAME,DATA_TYPE,EXTRA FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?", rs -> {
            types.put(rs.getString(1), rs.getString(2));
            if (Set.of("VIRTUAL GENERATED", "STORED GENERATED").contains(rs.getString(3))) generated.add(rs.getString(1));
        }, table);
        var primary = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND INDEX_NAME='PRIMARY' ORDER BY SEQ_IN_INDEX", String.class, table);
        var result = new ArrayList<LegacyCopyRow>();
        for (var row : snapshot.path("rows")) {
            var values = new LinkedHashMap<String, Object>();
            for (int i = 0; i < row.size(); i++) {
                String column = snapshot.path("columns").get(i).asText();
                if (!generated.contains(column)) values.put(column, decode(row.get(i), types.get(column)));
            }
            result.add(new LegacyCopyRow(table, values, primary));
        }
        return result;
    }

    private static Object decode(JsonNode value, String type) {
        if (value.isNull()) return null;
        byte[] bytes = Base64.getDecoder().decode(value.asText());
        return Set.of("binary", "varbinary", "tinyblob", "blob", "mediumblob", "longblob", "bit").contains(type)
                ? bytes : new String(bytes, StandardCharsets.UTF_8);
    }

    boolean exists(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + LegacyRows.name(table) + " WHERE " + where(primary),
                Integer.class, primary.stream().map(values::get).toArray()) == 1;
    }

    void insert(JdbcTemplate jdbc, Map<String, Object> initial) {
        String columns = String.join(",", initial.keySet().stream().map(LegacyRows::name).toList());
        String placeholders = String.join(",", Collections.nCopies(initial.size(), "?"));
        jdbc.update("INSERT INTO " + LegacyRows.name(table) + "(" + columns + ") VALUES(" + placeholders + ")", initial.values().toArray());
    }

    void restore(JdbcTemplate jdbc) {
        var changed = values.keySet().stream().filter(column -> !primary.contains(column)).toList();
        if (changed.isEmpty()) return;
        var args = new ArrayList<Object>(); changed.forEach(column -> args.add(values.get(column)));
        primary.forEach(column -> args.add(values.get(column)));
        jdbc.update("UPDATE " + LegacyRows.name(table) + " SET " + String.join(",", changed.stream()
                .map(column -> LegacyRows.name(column) + "=?").toList()) + " WHERE " + where(primary), args.toArray());
    }

    static String where(List<String> columns) {
        return String.join(" AND ", columns.stream().map(column -> LegacyRows.name(column) + "=?").toList());
    }
}
