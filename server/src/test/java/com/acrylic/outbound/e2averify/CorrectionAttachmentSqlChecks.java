package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Full typed rows before/after migration, plus exact trigger attribution on nonempty evidence. */
final class CorrectionAttachmentSqlChecks {
    private final JdbcTemplate jdbc;
    private final Path attempt;
    CorrectionAttachmentSqlChecks(JdbcTemplate jdbc, Path attempt) { this.jdbc = jdbc; this.attempt = attempt; }

    List<String> tables() {
        return jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"
            + " AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME<>'flyway_schema_history' ORDER BY TABLE_NAME", String.class);
    }

    Map<String, List<String>> snapshot(List<String> tables, String label) throws Exception {
        var result = new TreeMap<String, List<String>>();
        for (String table : tables) {
            if (!table.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Unexpected table identifier");
            result.put(table, jdbc.queryForList("SELECT * FROM `" + table + "`").stream()
                .map(HandoverRollbackFacts::encodeRow).sorted().toList());
        }
        Files.writeString(attempt.resolve(label + ".json"), new ObjectMapper().writeValueAsString(result));
        return result;
    }

    void verifyGuards() throws Exception {
        var tables = List.of("correction_attachment", "correction_attachment_use");
        var before = snapshot(tables, "attachment-before-sql-denials");
        assertThat(before.get("correction_attachment")).isNotEmpty();
        assertThat(before.get("correction_attachment_use")).isNotEmpty();
        denied("UPDATE correction_attachment SET id=UUID_TO_BIN(UUID()) LIMIT 1", "CORRECTION_ATTACHMENT_IMMUTABLE");
        denied("UPDATE correction_attachment SET content_bytes=X'01' LIMIT 1", "CORRECTION_ATTACHMENT_IMMUTABLE");
        denied("DELETE FROM correction_attachment LIMIT 1", "CORRECTION_ATTACHMENT_IMMUTABLE");
        denied("UPDATE correction_attachment_use SET line_no=line_no+1 LIMIT 1", "CORRECTION_ATTACHMENT_USE_IMMUTABLE");
        denied("DELETE FROM correction_attachment_use LIMIT 1", "CORRECTION_ATTACHMENT_USE_IMMUTABLE");
        denied("INSERT INTO correction_attachment SELECT UUID_TO_BIN(UUID()),warehouse_id,uploaded_by,UUID_TO_BIN(UUID()),"
            + "REPEAT('0',64),1,X'01',UTC_TIMESTAMP(3) FROM correction_attachment LIMIT 1", "CORRECTION_ATTACHMENT_HASH_MISMATCH");
        assertThat(snapshot(tables, "attachment-after-sql-denials")).isEqualTo(before);
    }

    private void denied(String sql, String message) throws Exception {
        Throwable failure = catchThrowable(() -> jdbc.update(sql));
        assertThat(failure).isInstanceOf(DataAccessException.class);
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
        assertThat(cause).isInstanceOf(SQLException.class);
        var error = (SQLException) cause;
        assertThat(error.getSQLState()).isEqualTo("45000");
        assertThat(error.getErrorCode()).isEqualTo(1644);
        assertThat(error.getMessage()).isEqualTo(message);
        Files.writeString(attempt.resolve("attachment-sql-denials.log"), message + " SQLSTATE=45000 code=1644\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
