package com.acrylic.outbound.e2averify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.acrylic.outbound.common.ApiErrors;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;

/** Test-only SQL faults after actual inserts; HTTP and business transactions remain real. */
final class E2aIntakeSqlRollback {
    private static final List<String> TABLES = List.of(
        "source_evidence_object", "source_evidence_page_item_stage", "source_evidence_intake_receipt",
        "source_evidence_set", "source_evidence_item", "source_evidence_verification",
        "bale_commitment", "barcode_sync_pending", "inventory_position", "inventory_ledger",
        "submission", "submission_scope", "order_submission_slot", "temporary_decision_state",
        "pda_audit_event", "operation_event", "outbound_order", "order_sync_outbox");
    private final E2aCrossSourceHttp http;
    private final CorrectionAttachmentSqlChecks snapshots;

    E2aIntakeSqlRollback(E2aCrossSourceHttp http) {
        this.http = http;
        snapshots = new CorrectionAttachmentSqlChecks(http.wire.fixture.jdbc, http.evidence);
    }

    byte[] receipt(String operation, UUID key, JsonNode body) throws Exception {
        String kind = switch (operation) {
            case "open" -> "OPEN"; case "page" -> "PAGE";
            case "stop" -> "STOP"; case "verify" -> "VERIFY";
            default -> throw new IllegalArgumentException("Unsupported rollback probe");
        };
        String trigger = "CREATE TRIGGER intake_sql_failure AFTER INSERT ON source_evidence_intake_receipt "
            + "FOR EACH ROW BEGIN IF NEW.operation_kind='" + kind + "'" + writtenEffects(kind)
            + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INTAKE_AFTER_" + kind + "'; END IF; END";
        fails(kind, trigger, () -> http.send(operation, key, body));
        byte[] original = http.post(operation, key, body);
        var committed = snapshots.snapshot(TABLES, kind + "-committed");
        assertThat(http.post(operation, key, body)).containsExactly(original);
        http.original(key, original);
        assertThat(snapshots.snapshot(TABLES, kind + "-replayed")).isEqualTo(committed);
        return original;
    }

    void raw(E2aCrossSourceRawHttp raw) throws Exception {
        String trigger = "CREATE TRIGGER intake_sql_failure AFTER INSERT ON source_evidence_object "
            + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INTAKE_AFTER_RAW'";
        fails("RAW", trigger, raw::first);
    }

    private void fails(String operation, String trigger, Request request) throws Exception {
        var before = snapshots.snapshot(TABLES, operation + "-before-failure");
        var logger = (Logger) LoggerFactory.getLogger(ApiErrors.class);
        var events = new ListAppender<ILoggingEvent>(); events.start(); logger.addAppender(events);
        var jdbc = http.wire.fixture.jdbc;
        jdbc.execute(trigger);
        try {
            var response = request.send();
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("INTERNAL_ERROR");
            requireSqlFault(events.list, "INTAKE_AFTER_" + operation);
        } finally {
            jdbc.execute("DROP TRIGGER intake_sql_failure");
            logger.detachAppender(events); events.stop();
        }
        assertThat(snapshots.snapshot(TABLES, operation + "-after-rollback")).isEqualTo(before);
        Files.writeString(http.evidence.resolve("sql-rollback.log"), operation
            + " SQLSTATE=45000 code=1644; all " + TABLES.size() + " tables unchanged\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
        assertThat(found).as("exact SQL fault was reached after inserts: " + message).isTrue();
    }

    private static String writtenEffects(String operation) {
        return switch (operation) {
            case "OPEN" -> "";
            case "PAGE" -> " AND EXISTS(SELECT 1 FROM source_evidence_page_item_stage WHERE page_object_id=NEW.object_id)";
            case "STOP" -> " AND EXISTS(SELECT 1 FROM source_evidence_object WHERE id=NEW.object_id AND kind='STOP_PROOF')";
            case "VERIFY" -> " AND EXISTS(SELECT 1 FROM source_evidence_verification WHERE id=NEW.evidence_verification_id)"
                + " AND EXISTS(SELECT 1 FROM source_evidence_item WHERE set_id=NEW.evidence_set_id)"
                + " AND EXISTS(SELECT 1 FROM bale_commitment WHERE source_evidence_set_id=NEW.evidence_set_id AND state='SOURCE_RESERVED')";
            default -> throw new IllegalArgumentException("Unsupported rollback probe");
        };
    }

    @FunctionalInterface private interface Request { HttpResponse<byte[]> send() throws Exception; }
}
