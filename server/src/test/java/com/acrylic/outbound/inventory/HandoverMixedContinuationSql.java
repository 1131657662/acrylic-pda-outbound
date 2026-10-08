package com.acrylic.outbound.inventory;

import com.acrylic.outbound.e2averify.E2aSnapshotRows;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.canonical;

/** Captures the mixed chain's ticket, activation, retained records and closure before a tail fault. */
final class HandoverMixedContinuationSql {
    private static final List<String> TABLES = List.of(
        "temporary_source_continuation", "temporary_source_continuation_bale",
        "temporary_source_continuation_activation", "temporary_source_continuation_submission",
        "temporary_source_continuation_closure", "temporary_physical_disposition_history",
        "temporary_work_origin", "temporary_authorization");
    private final JdbcTemplate jdbc;
    private final Path directory;

    HandoverMixedContinuationSql(JdbcTemplate jdbc, Path directory) {
        this.jdbc = jdbc;
        this.directory = directory;
    }

    ObjectNode snapshot(String label) throws Exception {
        var result = JsonNodeFactory.instance.objectNode();
        var rows = new E2aSnapshotRows(jdbc);
        for (String table : TABLES) {
            var columns = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION", String.class, table);
            result.set(table, rows.rows(table, columns));
        }
        Files.writeString(directory.resolve(label + "-continuation.json"), canonical(result), StandardOpenOption.CREATE_NEW);
        return result;
    }
}
