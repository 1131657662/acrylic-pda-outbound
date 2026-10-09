package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Real source archive damage and exact final audit failure retain every durable fact. */
final class HandoverFormalFaultRefusals {
    private final HandoverFormalFaultSource f;
    HandoverFormalFaultRefusals(HandoverFormalFaultSource f) { this.f = f; }

    void beforeFence() throws Exception {
        for (String field : new String[]{"changeRequestId", "recoveryPackageId", "payloadSha256", "auditThroughSeq",
                "captureHighWatermark", "orderScanHighWatermark"}) {
            var changed = f.command.deepCopy(); var archive = (ObjectNode) changed.path("archivedEvidence");
            archive.put(field, field.equals("payloadSha256") ? "0".repeat(64)
                : field.endsWith("HighWatermark") || field.endsWith("Seq") ? "999" : UUID.randomUUID().toString());
            refused(changed, "HANDOVER_FAULT_ARCHIVE_INVALID", "wrong-" + field);
        }
        var cutoff = f.command.deepCopy(); ((ObjectNode) cutoff.path("physicalControl")).put("controlledAt", "2020-01-01T00:00:00Z");
        refused(cutoff, "HANDOVER_FAULT_ARCHIVE_INVALID", "old-cutoff");
        missing(f.command, "missing-before");
        damaged("corrupt-before");
        revoked();
        var source = new HandoverApprovalHttp(f.source.r.f.web.identity().user().username(), f.source.directory);
        var before = f.facts.snapshot("self-approval-before");
        source.command(f.command, 403, "HANDOVER_SELF_APPROVAL_DENIED");
        assertThat(f.facts.snapshot("self-approval-after")).isEqualTo(before);
        rollback();
    }

    void afterFence() throws Exception {
        missing(f.command, "missing-replay");
        damaged("corrupt-replay");
        var jdbc = f.source.jdbc; var before = f.facts.snapshot("immutable-before");
        assertThatThrownBy(() -> jdbc.update("UPDATE handover_formal_fault_fence SET input_high_watermark=0 WHERE case_id=UUID_TO_BIN(?)",
            f.source.kase.toString())).hasMessageContaining("Formal fault fence is immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE device SET status='ACTIVE' WHERE id=UUID_TO_BIN(?)",
            f.command.path("sourceDeviceId").asText())).hasMessageContaining("ordinary re-enable is forbidden");
        assertThatThrownBy(() -> jdbc.update("UPDATE order_change_request SET status='STOPPED' WHERE id=UUID_TO_BIN(?)",
            f.source.r.body.path("changeRequestId").asText())).hasMessageContaining("cannot become an original device acknowledgement");
        assertThatThrownBy(() -> jdbc.update("UPDATE recovery_package SET byte_count=byte_count+1 WHERE id=UUID_TO_BIN(?)",
            f.source.r.body.path("recoveryPackageId").asText())).hasMessageContaining("Formal fault original package is immutable");
        assertThat(f.facts.snapshot("immutable-after")).isEqualTo(before);
        f.workflow.web.command(f.command.deepCopy().put("sourceDeviceId", UUID.randomUUID().toString()), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        f.source.post(f.source.command, f.source.r.f.tokens, 401, null);
    }

    private void revoked() throws Exception {
        var jdbc = f.source.jdbc;
        var actor = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", byte[].class, f.workflow.web.username());
        var roles = jdbc.queryForList("SELECT role_id FROM user_role WHERE user_id=?", byte[].class, actor);
        assertThat(roles).isNotEmpty();
        jdbc.update("DELETE FROM user_role WHERE user_id=?", actor);
        try {
            var before = f.facts.snapshot("revoked-before");
            f.workflow.web.command(f.command, 403, "PERMISSION_DENIED");
            assertThat(f.facts.snapshot("revoked-after")).isEqualTo(before);
        } finally { for (var role : roles) jdbc.update("INSERT INTO user_role VALUES(?,?)", actor, role); }
    }

    private void damaged(String label) throws Exception {
        var path = f.source.directory.getParent().resolve("storage/submissions/" + f.source.r.ackBody().path("payloadSha256").asText() + ".json");
        var retained = path.resolveSibling(path.getFileName() + "." + label + ".retained");
        Files.move(path, retained);
        try {
            Files.writeString(path, "{}");
            refused(f.command, "HANDOVER_FAULT_ARCHIVE_INVALID", label);
            if (label.equals("corrupt-replay")) f.workflow.web.get(f.contextPath("APPROVE"), 409, "HANDOVER_FAULT_ARCHIVE_INVALID");
        } finally {
            Files.move(path, path.resolveSibling(path.getFileName() + "." + label + ".rejected"));
            Files.copy(retained, path);
        }
        assertThat(Files.readAllBytes(path)).isEqualTo(Files.readAllBytes(retained));
    }

    private void rollback() throws Exception {
        var jdbc = f.source.jdbc; var before = f.facts.snapshot("last-audit-before");
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(com.acrylic.outbound.common.ApiErrors.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); logs.start(); logger.addAppender(logs);
        jdbc.execute("CREATE TRIGGER test_formal_fault_final AFTER INSERT ON operation_event FOR EACH ROW BEGIN "
            + "IF NEW.event_type='HANDOVER_FAULT_FENCE' AND EXISTS(SELECT 1 FROM handover_formal_fault_fence) "
            + "AND EXISTS(SELECT 1 FROM handover_command_receipt WHERE id=NEW.entity_id AND command_type='FAULT_FENCE') "
            + "THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D07_FORMAL_FAULT_FINAL_SQL'; END IF; END");
        try {
            f.workflow.web.command(f.command, 500, "INTERNAL_ERROR");
            HandoverTemporaryIntakeChecks.requireFault(logs.list, "D07_FORMAL_FAULT_FINAL_SQL");
        } finally { jdbc.execute("DROP TRIGGER test_formal_fault_final"); logger.detachAppender(logs); logs.stop(); }
        assertThat(f.facts.snapshot("last-audit-after")).isEqualTo(before);
        Files.writeString(f.source.directory.resolve("formal-fault-final-sql.txt"), "D07_FORMAL_FAULT_FINAL_SQL; SQLSTATE=45000; code=1644; all durable tables rolled back\n");
    }

    private void missing(ObjectNode command, String label) throws Exception {
        var path = f.source.directory.getParent().resolve("storage/submissions/" + f.source.r.ackBody().path("payloadSha256").asText() + ".json");
        var retained = path.resolveSibling(path.getFileName() + "." + label + ".retained");
        Files.move(path, retained);
        try {
            refused(command, "HANDOVER_FAULT_ARCHIVE_INVALID", label);
            if (label.equals("missing-replay")) f.workflow.web.get(f.contextPath("APPROVE"), 409, "HANDOVER_FAULT_ARCHIVE_INVALID");
        } finally { Files.move(retained, path); }
    }

    private void refused(ObjectNode command, String code, String label) throws Exception {
        var before = f.facts.snapshot(label + "-before");
        f.workflow.web.command(command, 409, code);
        assertThat(f.facts.snapshot(label + "-after")).isEqualTo(before);
    }
}
