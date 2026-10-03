package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end HTTP stop intake; setup uses MockMvc with actual generated signing identities and durable packages. */
public final class HandoverStopChecks {
    private final HandoverStopFixture f;
    private final HandoverStopFacts facts;
    private byte[] original;

    public HandoverStopChecks(WebApplicationContext app, Path attempt) throws Exception {
        f = new HandoverStopFixture(app, attempt.resolve("stop-http")); facts = new HandoverStopFacts(f, f.jdbc);
    }

    public void verify() throws Exception {
        identities(); invalidBindings(); missingFile(); rollback(); concurrent();
        var before = facts.snapshot();
        assertThat(f.post(f.command, f.r.f.tokens, 200, null)).isEqualTo(original);
        f.post(f.command.deepCopy().put("auditThroughSeq", "99"), f.r.f.tokens, 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        f.post(f.command.deepCopy().put("commandId", UUID.randomUUID().toString()), f.r.f.tokens, 409, "IDEMPOTENCY_CONTENT_MISMATCH");
        facts.unchanged(before); facts.stopped();
        verifyOriginalResultRemainsBlocked();
    }

    private void identities() throws Exception {
        var other = f.r.f.pda.login(f.r.f.pda.register()); var before = facts.snapshot();
        f.post(f.command, null, 401, null);
        f.post(f.command, other, 404, "ASSIGNMENT_NOT_FOUND");
        f.postTo(UUID.randomUUID(), f.command, f.r.f.tokens, 404, "HANDOVER_CASE_NOT_FOUND");
        facts.unchanged(before);
    }

    private void invalidBindings() throws Exception {
        for (String field : List.of("expectedCaseVersion", "changeRequestId", "recoveryPackageId", "payloadSha256", "captureHighWatermark", "auditThroughSeq")) {
            var command = f.command.deepCopy();
            command.put(field, field.endsWith("Id") ? UUID.randomUUID().toString() : field.equals("payloadSha256") ? "0".repeat(64) : "99");
            var before = facts.snapshot();
            f.post(command, f.r.f.tokens, field.equals("changeRequestId") ? 404 : 409, null);
            facts.unchanged(before);
        }
    }

    private void missingFile() throws Exception {
        var path = f.directory.getParent().resolve("storage/submissions/" + f.command.path("payloadSha256").asText() + ".json");
        var retained = path.resolveSibling(path.getFileName() + ".retained-test"); Files.move(path, retained);
        try {
            var before = facts.snapshot(); f.post(f.command, f.r.f.tokens, 409, "HANDOVER_STOP_FILE_UNAVAILABLE"); facts.unchanged(before);
        } finally { Files.move(retained, path); }
    }

    private void rollback() throws Exception {
        f.jdbc.execute("CREATE TRIGGER test_stop_write_failure BEFORE INSERT ON handover_evidence FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected stop evidence write failure'");
        try {
            var before = facts.snapshot(); f.post(f.command, f.r.f.tokens, 500, null); facts.unchanged(before);
        } finally { f.jdbc.execute("DROP TRIGGER test_stop_write_failure"); }
    }

    private void concurrent() throws Exception {
        try (var connection = f.jdbc.getDataSource().getConnection(); var workers = Executors.newFixedThreadPool(2)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) { statement.executeQuery("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE").close(); }
            var first = workers.submit(() -> f.post(f.command, f.r.f.tokens, 200, null));
            var second = workers.submit(() -> f.post(f.command, f.r.f.tokens, 200, null));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); int waits = 0;
            while (System.nanoTime() < deadline && waits < 2) {
                waits = f.jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits WHERE REQUESTING_ENGINE_LOCK_ID IS NOT NULL", Integer.class);
                if (waits < 2) Thread.sleep(50);
            }
            assertThat(waits).as("both first-key HTTP requests waited for identity lock").isGreaterThanOrEqualTo(2);
            connection.commit(); original = first.get(30, TimeUnit.SECONDS);
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(original);
        }
        var receipt = SubmissionJson.parse(original); receipt.path("capabilities").forEach(value -> assertThat(value.asBoolean()).isFalse());
        Files.write(f.directory.resolve("original-receipt.json"), original);
    }

    private void verifyOriginalResultRemainsBlocked() throws Exception {
        var before = facts.snapshot();
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", "APPROVE")
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", f.r.f.tokens.path("warehouseId").asText())
            .put("caseId", f.kase.toString()).put("transportRecoveryEpoch", "1").put("originalResultState", "NONE_VERIFIED");
        body.putObject("expected").put("caseId", f.kase.toString()).put("caseVersion", "2").put("status", "EVIDENCE_PENDING").put("userAuthVersion", "1");
        f.r.f.web.send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/handover-commands")
            .contentType("application/json").content(body.toString()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("HANDOVER_ORIGINAL_RESULT_OPEN"));
        facts.unchanged(before);
    }

    public void restart(WebApplicationContext app) throws Exception {
        var current = new HandoverStopFacts(f, app.getBean(JdbcTemplate.class)); var before = current.snapshot();
        assertThat(f.post(f.command, f.r.f.tokens, 200, null)).isEqualTo(original); current.unchanged(before);
    }
}
