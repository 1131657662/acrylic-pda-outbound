package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Typed snapshots of every pre-upgrade column, including original ledgers and result bytes. */
final class CorrectionAccountingMigrationOriginals {
    private final JdbcTemplate jdbc;
    private final Path attempt;
    private final Map<String, String> projections = new TreeMap<>();

    CorrectionAccountingMigrationOriginals(JdbcTemplate jdbc, Path attempt) {
        this.jdbc = jdbc; this.attempt = attempt;
        var tables = jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"
            + " AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME", String.class);
        for (String table : tables) {
            var columns = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                + " AND TABLE_NAME=? ORDER BY ORDINAL_POSITION", String.class, table);
            projections.put(identifier(table), String.join(",", columns.stream().map(CorrectionAccountingMigrationOriginals::identifier).toList()));
        }
    }

    Map<String, List<String>> snapshot(String label) throws Exception {
        var result = new TreeMap<String, List<String>>();
        for (var entry : projections.entrySet()) {
            result.put(entry.getKey(), jdbc.queryForList("SELECT " + entry.getValue() + " FROM " + entry.getKey()).stream()
                .map(HandoverRollbackFacts::encodeRow).sorted().toList());
        }
        Files.writeString(attempt.resolve(label + ".json"), new ObjectMapper().writeValueAsString(result));
        return result;
    }

    private static String identifier(String value) {
        if (!value.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Unexpected schema identifier");
        return "`" + value + "`";
    }
}
