package com.acrylic.outbound.inventory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.*;
import ch.qos.logback.core.read.ListAppender;
import com.acrylic.outbound.common.ApiErrors;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.sql.SQLException;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.*;

/** Last outbox/audit failures must roll back credential, owners, rounds and evidence together. */
final class HandoverTemporaryActivationSql {
    private final HandoverTemporaryConfirmationChecks confirmation;
    private final HandoverTemporaryTargetFixture f;
    private final Path directory;

    HandoverTemporaryActivationSql(HandoverTemporaryConfirmationChecks confirmation, Path directory) {
        this.confirmation = confirmation; this.f = confirmation.fixture(); this.directory = directory;
    }

    void rollback(JsonNode command, String table) throws Exception {
        assertThat(table).isIn("order_sync_outbox", "operation_event");
        var before = confirmation.snapshot("activation-" + table + "-before");
        var logger = (Logger) LoggerFactory.getLogger(ApiErrors.class); var events = new ListAppender<ILoggingEvent>();
        events.start(); logger.addAppender(events);
        String message = "D06_TEMP_ACTIVATE_" + table;
        String predicate = table.equals("operation_event") ? "NEW.event_type='HANDOVER_ACTIVATE'" : "NEW.event_type='HANDOVER_TEMP_ACTIVATED'";
        f.source.jdbc.execute("CREATE TRIGGER d06_temp_activation_fault AFTER INSERT ON " + table + " FOR EACH ROW BEGIN IF "
            + predicate + " AND EXISTS(SELECT 1 FROM temporary_handover_activation) AND EXISTS(SELECT 1 FROM bale_commitment "
            + "WHERE state='TEMP_HANDOVER_RESERVED') AND EXISTS(SELECT 1 FROM submission_scope WHERE work_round=2 AND current_submission_id IS NULL) "
            + "THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='" + message + "'; END IF; END");
        try { f.context.reviewer().command(command, 500, "INTERNAL_ERROR"); requireFault(events, message); }
        finally { f.source.jdbc.execute("DROP TRIGGER d06_temp_activation_fault"); logger.detachAppender(events); events.stop(); }
        assertThat(confirmation.snapshot("activation-" + table + "-after")).isEqualTo(before);
    }

    private void requireFault(ListAppender<ILoggingEvent> events, String message) throws Exception {
        boolean found = false;
        for (var event : events.list) {
            if (!(event.getThrowableProxy() instanceof ThrowableProxy proxy)) continue;
            for (Throwable cause = proxy.getThrowable(); cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && message.equals(sql.getMessage())) {
                    assertThat(sql.getSQLState()).isEqualTo("45000"); assertThat(sql.getErrorCode()).isEqualTo(1644); found = true;
                }
            }
        }
        assertThat(found).as("actual effects exist before exact final SQL fault").isTrue();
        Files.writeString(directory.resolve(message + ".txt"), message + " SQLSTATE=45000 code=1644\n");
    }
}
