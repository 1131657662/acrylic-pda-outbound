package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real source-qualified stop consumer, including last-write rollback and original receipt recovery. */
public final class HandoverTemporaryStopChecks {
    private final HandoverTemporaryStopFixture f;
    private final TemporaryDispositionSqlChecks sql;
    private byte[] original;

    public HandoverTemporaryStopChecks(WebApplicationContext app, Path attempt) throws Exception {
        f = new HandoverTemporaryStopFixture(app, attempt);
        sql = new TemporaryDispositionSqlChecks(f.jdbc, f.directory.resolve("sql"));
    }

    public void verify() throws Exception {
        var before = snapshot("nonterminal-before", f.jdbc);
        f.stop(f.command, 409, "HANDOVER_ORIGINAL_RESULT_OPEN");
        assertThat(snapshot("nonterminal-after", f.jdbc)).isEqualTo(before);
        f.abort(); invalidProofs(); permission(); missingFile(); rollback();
        var stopped = snapshot("accepted-before", f.jdbc);
        concurrent(); replay();
        var after = snapshot("accepted-after", f.jdbc);
        for (String table : stopped.keySet()) if (!Set.of("handover_case", "handover_evidence", "api_idempotency_request",
                "operation_event", "order_sync_outbox", "outbound_order").contains(table))
            assertThat(after.get(table)).as(table).isEqualTo(stopped.get(table));
        var receipt = SubmissionJson.parse(original);
        assertThat(receipt.has("recallStop")).isFalse();
        assertThat(receipt.path("source").path("assignmentId").isNull()).isTrue();
        assertThat(receipt.path("source").path("authorizationId").asText()).isEqualTo(f.head.control().temporary().authorization().toString());
        receipt.path("capabilities").forEach(value -> assertThat(value.asBoolean()).isFalse());
        assertThat(f.jdbc.queryForObject("SELECT status FROM submission WHERE id=?", String.class, Ids.bytes(f.head.control().id()))).isEqualTo("ABORTED");
        assertThat(f.jdbc.queryForObject("SELECT stop_state FROM handover_case WHERE id=?", String.class, Ids.bytes(f.kase))).isEqualTo("DEVICE_ACKED");
    }

    private void invalidProofs() throws Exception {
        var before = snapshot("invalid-before", f.jdbc);
        f.stop(f.command.deepCopy().put("setSha256", "0".repeat(64)), 409, "TEMP_CONTINUATION_STOP_PROOF_REQUIRED");
        f.stop(f.command.deepCopy().put("verificationId", UUID.randomUUID().toString()), 409, "TEMP_CONTINUATION_STOP_PROOF_REQUIRED");
        f.stop(f.command.deepCopy().put("captureHighWatermark", "999"), 409, "HANDOVER_STOP_CONTEXT_CHANGED");
        f.post("/handovers/" + f.kase + "/stop-ack", f.command, "invalid", 401);
        byte[] denied = f.post("/handovers/" + f.kase + "/stop-ack", f.command, f.otherDeviceToken, 404);
        assertThat(SubmissionJson.parse(denied).path("code").asText()).isEqualTo("HANDOVER_CASE_NOT_FOUND");
        assertThat(snapshot("invalid-after", f.jdbc)).isEqualTo(before);
    }

    private void permission() throws Exception {
        var role = UUID.randomUUID();
        f.jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "D06_" + role, "isolated login only");
        f.jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='pda:login'", Ids.bytes(role));
        var before = snapshot("permission-before", f.jdbc);
        var roles = f.jdbc.queryForList("SELECT role_id FROM user_role WHERE user_id=?", byte[].class, Ids.bytes(f.head.actor()));
        f.jdbc.update("DELETE FROM user_role WHERE user_id=?", Ids.bytes(f.head.actor()));
        f.jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(f.head.actor()), Ids.bytes(role));
        try {
            f.stop(f.command, 403, "PERMISSION_DENIED");
            f.jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=?", Ids.bytes(f.head.actor()), Ids.bytes(role));
            f.stop(f.command, 401, "AUTH_EXPIRED");
        } finally {
            f.jdbc.update("DELETE FROM user_role WHERE user_id=? AND role_id=?", Ids.bytes(f.head.actor()), Ids.bytes(role));
            for (var originalRole : roles) f.jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(f.head.actor()), originalRole);
        }
        assertThat(snapshot("permission-after", f.jdbc)).isEqualTo(before);
    }

    private void missingFile() throws Exception {
        String key = f.jdbc.queryForObject("SELECT o.file_key FROM source_evidence_set e JOIN source_evidence_object o "
            + "ON o.id=e.stop_object_id WHERE e.id=UUID_TO_BIN(?)", String.class, f.command.path("sourceEvidenceSetId").asText());
        Path path = f.directory.getParent().resolve("storage").resolve(key);
        Path saved = path.resolveSibling(path.getFileName() + ".d06-retained");
        var before = snapshot("missing-before", f.jdbc);
        Files.move(path, saved);
        try { f.stop(f.command, 409, "TEMP_CONTINUATION_STOP_PROOF_UNAVAILABLE"); }
        finally { Files.move(saved, path); }
        assertThat(snapshot("missing-after", f.jdbc)).isEqualTo(before);
    }

    private void rollback() throws Exception {
        var before = snapshot("rollback-before", f.jdbc);
        f.jdbc.execute("CREATE TRIGGER d06_temp_stop_failure AFTER INSERT ON order_sync_outbox FOR EACH ROW "
            + "BEGIN IF NEW.event_type='HANDOVER_STOPPED' AND (SELECT COUNT(*) FROM handover_evidence WHERE case_id=UUID_TO_BIN('"
            + f.kase + "'))=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D06_TEMP_STOP_LAST_WRITE'; END IF; END");
        try { f.stop(f.command, 500, "INTERNAL_ERROR"); }
        finally { f.jdbc.execute("DROP TRIGGER d06_temp_stop_failure"); }
        assertThat(snapshot("rollback-after", f.jdbc)).isEqualTo(before);
    }

    private void concurrent() throws Exception {
        try (var connection = f.jdbc.getDataSource().getConnection(); var workers = Executors.newFixedThreadPool(2)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) { statement.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close(); }
            var first = workers.submit(() -> f.stop(f.command, 200, null));
            var second = workers.submit(() -> f.stop(f.command, 200, null));
            int waits = 0; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (waits < 2 && System.nanoTime() < deadline) {
                waits = f.jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
                if (waits < 2) Thread.sleep(50);
            }
            assertThat(waits).isGreaterThanOrEqualTo(2); connection.commit();
            original = first.get(30, TimeUnit.SECONDS); assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(original);
        }
        Files.write(f.directory.resolve("original-receipt.json"), original);
    }

    private void replay() throws Exception {
        var before = snapshot("replay-before", f.jdbc);
        assertThat(f.stop(f.command, 200, null)).isEqualTo(original);
        f.stop(f.command.deepCopy().put("auditThroughSeq", "999"), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        f.stop(f.command.deepCopy().put("commandId", UUID.randomUUID().toString()), 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        assertThat(snapshot("replay-after", f.jdbc)).isEqualTo(before);
    }

    public void restart(WebApplicationContext app) throws Exception {
        var jdbc = app.getBean(JdbcTemplate.class);
        var before = handover(jdbc);
        assertThat(f.stop(f.command, 200, null)).isEqualTo(original);
        assertThat(handover(jdbc)).isEqualTo(before);
    }

    private Map<String, List<String>> snapshot(String label, JdbcTemplate jdbc) throws Exception {
        var rows = sql.snapshot(label); rows.putAll(handover(jdbc));
        Files.writeString(f.directory.resolve(label + ".json"), new ObjectMapper().writeValueAsString(rows));
        return rows;
    }

    private Map<String, List<String>> handover(JdbcTemplate jdbc) throws Exception {
        var rows = new TreeMap<String, List<String>>(); var mapper = new ObjectMapper();
        for (String table : List.of("handover_case", "handover_evidence", "handover_command_receipt", "temporary_authorization")) {
            var values = new ArrayList<String>();
            for (var row : jdbc.queryForList("SELECT * FROM " + table)) {
                var encoded = new TreeMap<String, String>();
                row.forEach((key, value) -> encoded.put(key, value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes)
                    : value == null ? null : value.toString()));
                values.add(mapper.writeValueAsString(encoded));
            }
            values.sort(String::compareTo); rows.put(table, values);
        }
        return rows;
    }
}
