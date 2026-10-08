package com.acrylic.outbound.inventory;

import com.acrylic.outbound.e2averify.E2aSnapshotRows;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.canonical;

/** Preserves the pending transport and immutable handover/E2A roots around the LOCAL stop fault. */
final class HandoverTemporaryLocalSql {
    private static final List<String> TABLES=List.of("submission_upload","submission_chunk","handover_case","handover_evidence",
        "handover_command_receipt","temporary_handover_activation_ticket","temporary_handover_activation_owner",
        "temporary_handover_activation","temporary_handover_submission","temporary_handover_completion",
        "source_evidence_object","source_evidence_page_item_stage");
    private final JdbcTemplate jdbc;private final Path directory;
    HandoverTemporaryLocalSql(JdbcTemplate jdbc,Path directory) {this.jdbc=jdbc;this.directory=directory;}
    ObjectNode snapshot(String label) throws Exception {
        var result=JsonNodeFactory.instance.objectNode();var rows=new E2aSnapshotRows(jdbc);
        for(String table:TABLES) {
            var columns=jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION",String.class,table);
            result.set(table,rows.rows(table,columns));
        }
        Files.writeString(directory.resolve(label+"-local.json"),canonical(result),StandardOpenOption.CREATE_NEW);return result;
    }
}
