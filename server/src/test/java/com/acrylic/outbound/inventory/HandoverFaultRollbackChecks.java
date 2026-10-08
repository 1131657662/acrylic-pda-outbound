package com.acrylic.outbound.inventory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.*;
import ch.qos.logback.core.read.ListAppender;
import com.acrylic.outbound.common.ApiErrors;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;

/** Injects the final audit write after device exclusion, outbox and original signed command were written. */
final class HandoverFaultRollbackChecks {
    static void verify(HandoverTemporaryFaultFenceChecks f, ObjectNode command, HandoverFaultFacts facts) throws Exception {
        var before = facts.snapshot("fault-last-write-before");
        var logger = (Logger) LoggerFactory.getLogger(ApiErrors.class);
        var logs = new ListAppender<ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        f.source.jdbc.execute("CREATE TRIGGER d07_fault_last_write AFTER INSERT ON operation_event FOR EACH ROW "
            + "BEGIN IF NEW.entity_type='HANDOVER_COMMAND' AND NEW.event_type='HANDOVER_FAULT_FENCE' "
            + "AND EXISTS(SELECT 1 FROM handover_fault_fence WHERE case_id=UUID_TO_BIN('" + f.source.kase + "')) "
            + "AND EXISTS(SELECT 1 FROM handover_command_receipt WHERE id=NEW.entity_id AND command_type='FAULT_FENCE') "
            + "THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D07_FAULT_FINAL_SQL'; END IF; END");
        try {
            f.faultReviewer.command(command, 500, "INTERNAL_ERROR");
            HandoverTemporaryIntakeChecks.requireFault(logs.list, "D07_FAULT_FINAL_SQL");
        } finally {
            f.source.jdbc.execute("DROP TRIGGER d07_fault_last_write"); logger.detachAppender(logs); logs.stop();
        }
        assertThat(facts.snapshot("fault-last-write-after")).isEqualTo(before);
        java.nio.file.Files.writeString(f.directory.resolve("fault-last-write.txt"),
            "D07_FAULT_FINAL_SQL SQLSTATE=45000 code=1644; all durable tables, source device/session and signed receipt rolled back\n");
    }
}
