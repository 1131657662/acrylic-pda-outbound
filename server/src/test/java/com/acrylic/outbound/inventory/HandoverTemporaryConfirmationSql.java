package com.acrylic.outbound.inventory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.*;
import ch.qos.logback.core.read.ListAppender;
import com.acrylic.outbound.common.ApiErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;

/** Includes new confirmation and all handover rows in the inherited stock/accounting rollback snapshot. */
final class HandoverTemporaryConfirmationSql {
    private static final List<String> EXTRA = List.of("temporary_handover_confirmation", "handover_case", "handover_evidence",
        "handover_checklist", "handover_command_receipt", "temporary_authorization", "temporary_preparation_ack", "order_assignment",
        "temporary_source_continuation", "temporary_source_continuation_activation", "temporary_handover_activation_ticket",
        "temporary_handover_activation_owner", "temporary_handover_activation", "temporary_work_origin", "temporary_preparation_part",
        "pda_session");
    private final HandoverTemporaryConfirmationHttp http;
    private final TemporaryDispositionSqlChecks stock;
    HandoverTemporaryConfirmationSql(HandoverTemporaryConfirmationHttp http) throws Exception {
        this.http = http; stock = new TemporaryDispositionSqlChecks(http.f.source.jdbc, http.directory.resolve("stock-sql"));
    }

    Map<String, List<String>> snapshot(String label) throws Exception {
        var result = new TreeMap<>(stock.snapshot(label)); var mapper = new ObjectMapper();
        for (String table : EXTRA) result.put(table, http.f.source.jdbc.queryForList("SELECT * FROM " + table).stream()
            .map(row -> encode(mapper, row)).sorted().toList());
        Files.writeString(http.directory.resolve(label + ".json"), mapper.writeValueAsString(result), StandardOpenOption.CREATE_NEW);
        return result;
    }

    void rollback(com.fasterxml.jackson.databind.JsonNode command) throws Exception {
        var before = snapshot("last-write-before"); var jdbc = http.f.source.jdbc;
        var logger = (Logger) LoggerFactory.getLogger(ApiErrors.class); var events = new ListAppender<ILoggingEvent>();
        events.start(); logger.addAppender(events);
        jdbc.execute("CREATE TRIGGER d06_temp_confirmation_failure AFTER INSERT ON operation_event FOR EACH ROW "
            + "BEGIN IF NEW.event_type='HANDOVER_TEMP_TARGET_CONFIRMED' AND EXISTS(SELECT 1 FROM temporary_handover_confirmation) "
            + "THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D06_TEMP_CONFIRM_LAST_WRITE'; END IF; END");
        try { http.submit(command, 500, "INTERNAL_ERROR"); requireFault(events.list); }
        finally { jdbc.execute("DROP TRIGGER d06_temp_confirmation_failure"); logger.detachAppender(events); events.stop(); }
        assertThat(snapshot("last-write-after")).isEqualTo(before);
    }

    private static String encode(ObjectMapper mapper, Map<String, Object> row) {
        var typed = new TreeMap<String, Object>();
        row.forEach((key, value) -> typed.put(key, value == null ? null : Map.of("type", value.getClass().getName(),
            "value", value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes) : value.toString())));
        try { return mapper.writeValueAsString(typed); }
        catch (java.io.IOException invalid) { throw new IllegalStateException(invalid); }
    }

    private void requireFault(List<ILoggingEvent> events) throws Exception {
        boolean found = false;
        for (var event : events) {
            if (!(event.getThrowableProxy() instanceof ThrowableProxy proxy)) continue;
            for (Throwable cause = proxy.getThrowable(); cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "D06_TEMP_CONFIRM_LAST_WRITE".equals(sql.getMessage())) {
                    assertThat(sql.getSQLState()).isEqualTo("45000"); assertThat(sql.getErrorCode()).isEqualTo(1644); found = true;
                }
            }
        }
        assertThat(found).as("confirmation row exists before exact last SQL fault").isTrue();
        Files.writeString(http.directory.resolve("last-write-sql-fault.txt"), "D06_TEMP_CONFIRM_LAST_WRITE; SQLSTATE=45000; code=1644\n");
    }
}
