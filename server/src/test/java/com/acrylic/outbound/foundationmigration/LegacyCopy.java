package com.acrylic.outbound.foundationmigration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Copies only the freshly generated synthetic snapshot, with every FK and CHECK continuously enabled. */
final class LegacyCopy {
    private final JdbcTemplate jdbc;
    LegacyCopy(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void restore() throws Exception {
        if (FoundationEnvironment.database().equals(FoundationEnvironment.DATABASE))
            throw new IllegalStateException("Never restore or reseed the retained source");
        var tables = new ObjectMapper().readTree(FoundationEnvironment.evidence("v33-original.json").toFile()).path("tables");
        var rows = new ArrayList<LegacyCopyRow>();
        tables.fields().forEachRemaining(table -> rows.addAll(LegacyCopyRow.from(table.getKey(), table.getValue(), jdbc)));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        tx.executeWithoutResult(status -> copy(rows));
    }

    private void copy(List<LegacyCopyRow> rows) {
        var pending = new ArrayList<>(rows);
        var references = new LegacyCopyRelations(jdbc);
        int passes = 0;
        while (!pending.isEmpty()) {
            int before = pending.size();
            pending.removeIf(row -> insert(row, references));
            if (pending.size() == before || ++passes > 100)
                throw new IllegalStateException("Synthetic copy has unresolved relations: "
                        + pending.stream().map(LegacyCopyRow::table).distinct().toList());
        }
        for (var row : rows) row.restore(jdbc);
    }

    private boolean insert(LegacyCopyRow row, LegacyCopyRelations references) {
        if (row.exists(jdbc)) return true;
        var initial = references.initial(row);
        if (initial == null) return false;
        try { row.insert(jdbc, initial); return true; }
        catch (DataIntegrityViolationException deferredNullableCheck) {
            if (initial.equals(row.values())) throw deferredNullableCheck;
            return false;
        }
    }
}
