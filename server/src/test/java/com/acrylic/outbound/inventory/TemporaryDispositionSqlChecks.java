package com.acrylic.outbound.inventory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.*;
import ch.qos.logback.core.read.ListAppender;
import com.acrylic.outbound.common.ApiErrors;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Full business-table rollback after the real command's effects and receipt have been inserted. */
final class TemporaryDispositionSqlChecks {
    private static final List<String> TABLES = List.of("temporary_physical_disposition", "temporary_physical_disposition_item",
        "temporary_command_receipt", "temporary_decision_event", "temporary_decision_state", "api_idempotency_request",
        "bale_commitment", "barcode_sync_pending", "inventory_position", "inventory_ledger", "submission", "submission_payload",
        "submission_scope", "order_submission_slot", "outbound_order", "order_result_version", "submitted_capture",
        "submitted_event", "source_evidence_set", "source_evidence_item", "source_evidence_verification",
        "source_evidence_intake_receipt", "pda_audit_event", "operation_event", "order_sync_outbox");
    private final JdbcTemplate jdbc;
    private final Path evidence;

    TemporaryDispositionSqlChecks(JdbcTemplate jdbc, Path evidence) throws Exception {
        this.jdbc = jdbc;
        this.evidence = Files.createDirectory(evidence);
    }

    Map<String, List<String>> snapshot(String label) throws Exception {
        var result = new TreeMap<String, List<String>>();
        var mapper = new ObjectMapper();
        for (String table : TABLES) result.put(table, jdbc.queryForList("SELECT * FROM " + table).stream()
            .map(row -> encode(mapper, row)).sorted().toList());
        Files.writeString(evidence.resolve(label + ".json"), mapper.writeValueAsString(result), StandardOpenOption.CREATE_NEW);
        return result;
    }

    void rollback(TemporaryDispositionHttp http, JsonNode command) throws Exception {
        String kind = command.path("commandType").asText();
        assertThat(kind).isIn("REJECT", "PHYSICAL_DISPOSITION", "CLOSE_DISPOSITION");
        String message = "DISPOSITION_AFTER_" + kind;
        var before = snapshot(kind + "-before-failure");
        var logger = (Logger) LoggerFactory.getLogger(ApiErrors.class);
        var events = new ListAppender<ILoggingEvent>(); events.start(); logger.addAppender(events);
        jdbc.execute("CREATE TRIGGER verified_disposition_failure AFTER INSERT ON operation_event FOR EACH ROW "
            + "BEGIN IF NEW.entity_type='TEMPORARY_COMMAND' AND NEW.event_type='TEMPORARY_" + kind + "' AND " + writtenEffects(kind)
            + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='" + message + "'; END IF; END");
        try {
            assertThat(SubmissionJson.parse(http.command(command, 500)).path("code").asText()).isEqualTo("INTERNAL_ERROR");
            requireSqlFault(events.list, message);
        } finally {
            jdbc.execute("DROP TRIGGER verified_disposition_failure");
            logger.detachAppender(events); events.stop();
        }
        assertThat(snapshot(kind + "-after-rollback")).isEqualTo(before);
        Files.writeString(evidence.resolve(kind + "-sql-fault.txt"), message + " SQLSTATE=45000 code=1644; "
            + TABLES.size() + " business tables unchanged\n", StandardOpenOption.CREATE_NEW);
    }

    private static String encode(ObjectMapper mapper, Map<String, Object> row) {
        var result = new TreeMap<String, Object>();
        row.forEach((key, value) -> result.put(key, value == null ? null : Map.of("type", value.getClass().getName(),
            "value", scalar(value))));
        try { return mapper.writeValueAsString(result); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Cannot encode SQL snapshot", failure); }
    }

    private static String scalar(Object value) {
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof java.sql.Time time) return time.toLocalTime().toString();
        return value.toString();
    }

    private static String writtenEffects(String kind) {
        if (kind.equals("REJECT")) return "EXISTS(SELECT 1 FROM submission s JOIN temporary_command_receipt r "
            + "ON r.submission_id=s.id WHERE r.id=NEW.entity_id AND s.status='REJECTED')";
        return "EXISTS(SELECT 1 FROM temporary_command_receipt r JOIN temporary_physical_disposition d "
            + "ON d.submission_id=r.submission_id JOIN temporary_physical_disposition_item i ON i.disposition_id=d.id "
            + "WHERE r.id=NEW.entity_id) AND NOT EXISTS(SELECT 1 FROM bale_commitment WHERE source_scope_id="
            + "(SELECT scope_id FROM temporary_command_receipt WHERE id=NEW.entity_id) AND state='REVIEW_RESERVED')";
    }

    private static void requireSqlFault(List<ILoggingEvent> events, String message) {
        boolean found = false;
        for (var event : events) {
            if (!(event.getThrowableProxy() instanceof ThrowableProxy proxy)) continue;
            for (Throwable cause = proxy.getThrowable(); cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && message.equals(sql.getMessage())) {
                    assertThat(sql.getSQLState()).isEqualTo("45000");
                    assertThat(sql.getErrorCode()).isEqualTo(1644); found = true;
                }
            }
        }
        assertThat(found).as("exact SQL fault after command effects: " + message).isTrue();
    }
}
