package com.acrylic.outbound.foundationmigration;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

final class LegacyCopyRelations {
    private final JdbcTemplate jdbc;
    private final Map<String, List<Reference>> references = new HashMap<>();
    LegacyCopyRelations(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Map<String, Object> initial(LegacyCopyRow row) {
        var values = new LinkedHashMap<>(row.values());
        for (var ref : references.computeIfAbsent(row.table(), this::read)) {
            if (ref.columns().stream().anyMatch(column -> row.values().get(column) == null)) continue;
            Object[] args = ref.columns().stream().map(row.values()::get).toArray();
            int count = jdbc.queryForObject("SELECT COUNT(*) FROM " + LegacyRows.name(ref.table())
                    + " WHERE " + LegacyCopyRow.where(ref.targets()), Integer.class, args);
            if (count != 0) continue;
            if (ref.nullable().isEmpty()) return null;
            values.put(ref.nullable().getFirst(), null);
        }
        return values;
    }

    private List<Reference> read(String table) {
        var groups = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var key : jdbc.queryForList("SELECT * FROM information_schema.KEY_COLUMN_USAGE "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND REFERENCED_TABLE_NAME IS NOT NULL "
                + "ORDER BY CONSTRAINT_NAME,ORDINAL_POSITION", table))
            groups.computeIfAbsent(key.get("CONSTRAINT_NAME").toString(), ignored -> new ArrayList<>()).add(key);
        var nullable = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND IS_NULLABLE='YES'", String.class, table);
        var result = new ArrayList<Reference>();
        for (var keys : groups.values()) {
            var columns = keys.stream().map(key -> key.get("COLUMN_NAME").toString()).toList();
            var targets = keys.stream().map(key -> key.get("REFERENCED_COLUMN_NAME").toString()).toList();
            result.add(new Reference(keys.getFirst().get("REFERENCED_TABLE_NAME").toString(), columns, targets,
                    columns.stream().filter(nullable::contains).toList()));
        }
        return result;
    }

    record Reference(String table, List<String> columns, List<String> targets, List<String> nullable) { }
}
