package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** No SQL substitution for rejection, stop verification, or source reservations. */
public final class TemporaryVerifiedDispositionChecks {
    private final JdbcTemplate jdbc;
    private final Path attempt;
    private final String submission;
    private final String warehouse;
    private final TemporaryDispositionHttp http;
    private final TemporaryDispositionHttp concurrent;
    private final TemporaryDispositionAccessChecks access;
    private final TemporaryDispositionSqlChecks sql;

    private TemporaryVerifiedDispositionChecks(WebApplicationContext context, Path attempt) throws Exception {
        this.jdbc = context.getBean(JdbcTemplate.class);
        this.attempt = attempt;
        submission = SubmissionJson.parse(Files.readString(attempt.resolve("android-result.json")))
            .path("submissionId").asText();
        warehouse = jdbc.queryForObject("SELECT BIN_TO_UUID(c.warehouse_id) FROM submission s "
            + "JOIN submission_scope c ON c.id=s.scope_id WHERE s.id=UUID_TO_BIN(?)", String.class, submission);
        http = new TemporaryDispositionHttp(attempt.resolve("disposition-http"));
        var supervisor = IdentityFixture.create(jdbc, context.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(supervisor.user().id()), warehouse);
        grantSupervisor(supervisor.user().id());
        http.login(supervisor.user().username());
        concurrent = new TemporaryDispositionHttp(attempt.resolve("disposition-concurrent"));
        concurrent.login(supervisor.user().username());
        access = new TemporaryDispositionAccessChecks(context, attempt, submission);
        sql = new TemporaryDispositionSqlChecks(jdbc, attempt.resolve("disposition-sql"));
    }

    private void grantSupervisor(UUID user) {
        UUID role = UUID.randomUUID();
        jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "VERIFIED_" + role.toString().substring(20),
            "isolated disposition supervisor");
        assertThat(jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission "
            + "WHERE code IN ('temporary_order:confirm','submission:review','order:dispose','order:resume')", Ids.bytes(role))).isEqualTo(4);
        jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(user), Ids.bytes(role));
    }

    public static void verify(WebApplicationContext context, Path attempt, boolean close) throws Exception {
        new TemporaryVerifiedDispositionChecks(context, attempt).release(close);
    }

    private void release(boolean close) throws Exception {
        requireTrustedStop();
        var retained = reservations();
        assertThat(retained).isNotEmpty();
        retained.forEach(row -> assertThat(row.get("state")).isEqualTo("REVIEW_RESERVED"));
        var reject = command("REJECT");
        String candidate = http.data("/temporary-reviews/" + submission + "/duplicate-candidates?warehouseId=" + warehouse)
            .path("commandContextSha256").asText();
        ((ObjectNode) reject.path("expected")).put("candidateContextSha256", candidate);
        var baseline = sql.snapshot("baseline");
        access.rejection(reject);
        assertThat(sql.snapshot("denied-rejection")).isEqualTo(baseline);
        sql.rollback(http, reject);
        byte[] rejected = http.command(reject, 200);
        assertThat(reservations()).isEqualTo(retained);
        assertThat(http.command(reject, 200)).isEqualTo(rejected);
        var disposition = disposition(close);
        var receipts = new TemporaryDispositionReceiptChecks(jdbc, http, sql);
        receipts.unsupportedReopen(disposition);
        access.noPermission(disposition);
        sql.rollback(http, disposition);
        byte[] receipt = concurrentDisposition(disposition);
        assertThat(reservations()).isEmpty();
        committed(disposition, retained, close);
        originals(reject, rejected);
        originals(disposition, receipt);
        receipts.corruptCycle(reject, rejected);
        unchangedFacts(baseline);
        requireTrustedStop();
        Files.writeString(attempt.resolve("verified-disposition-completed.json"), SubmissionJson.canonical(
            SubmissionJson.object().put("submissionId", submission).put("disposedCount", retained.size()).put("close", close)
                .put("originalRejectionReplay", true).put("localDeletionAllowed", false)));
    }

    private void committed(ObjectNode command, List<Map<String, Object>> retained, boolean close) {
        for (var row : retained) {
            var states = jdbc.queryForList("SELECT state FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",
                String.class, row.get("bale_id"));
            if (close) assertThat(states).containsExactly("QUARANTINED"); else assertThat(states).isEmpty();
        }
        if (!close) return;
        var slot = jdbc.queryForMap("SELECT phase,current_submission_id FROM order_submission_slot WHERE order_id=UUID_TO_BIN(?)",
            command.path("expected").path("canonicalOrderId").asText());
        assertThat(slot.get("phase")).isEqualTo("CLOSED");
        assertThat(slot.get("current_submission_id")).isNull();
    }

    private byte[] concurrentDisposition(ObjectNode command) throws Exception {
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            java.util.concurrent.Callable<byte[]> first = () -> { barrier.await(); return http.command(command, 200); };
            java.util.concurrent.Callable<byte[]> second = () -> { barrier.await(); return concurrent.command(command, 200); };
            var results = pool.invokeAll(List.of(first, second), 60, java.util.concurrent.TimeUnit.SECONDS);
            byte[] receipt = results.getFirst().get();
            assertThat(results.getLast().get()).isEqualTo(receipt);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_physical_disposition WHERE submission_id=UUID_TO_BIN(?)",
                Integer.class, submission)).isEqualTo(1);
            return receipt;
        }
    }

    private void originals(ObjectNode command, byte[] receipt) throws Exception {
        String kind = command.path("commandType").asText();
        var before = sql.snapshot(kind + "-before-replay");
        assertThat(http.command(command, 200)).isEqualTo(receipt);
        assertThat(http.get("/temporary-command-receipts/" + command.path("commandId").asText()
            + "/original?warehouseId=" + warehouse)).isEqualTo(receipt);
        access.differentActorCannotRecover(command);
        var different = command.deepCopy().put("reason", "changed request must not reuse its key");
        assertThat(SubmissionJson.parse(http.command(different, 409)).path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        assertThat(sql.snapshot(kind + "-after-replay")).isEqualTo(before);
        var capabilities = SubmissionJson.parse(receipt).path("capabilities");
        capabilities.forEach(value -> assertThat(value.asBoolean()).isFalse());
    }

    private void unchangedFacts(Map<String, List<String>> baseline) throws Exception {
        var after = sql.snapshot("complete");
        for (String table : List.of("inventory_position", "inventory_ledger", "submission_payload", "submitted_capture",
            "submitted_event", "source_evidence_set", "source_evidence_item", "source_evidence_verification",
            "source_evidence_intake_receipt", "pda_audit_event", "order_result_version"))
            assertThat(after.get(table)).as("original facts and accounting: " + table).isEqualTo(baseline.get(table));
    }

    private void requireTrustedStop() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_verification "
            + "WHERE submission_id=UUID_TO_BIN(?) AND result='VERIFIED_COMPLETE_AT_STOP'", Integer.class, submission)).isEqualTo(1);
        var result = SubmissionJson.parse(Files.readString(attempt.resolve("android-result.json")));
        var rows = jdbc.queryForList("SELECT BIN_TO_UUID(original_key) original_key,original_response_bytes "
            + "FROM source_evidence_intake_receipt WHERE submission_id=UUID_TO_BIN(?)", submission);
        assertThat(rows).hasSize(result.path("receipts").size());
        for (var row : rows) assertThat((byte[]) row.get("original_response_bytes")).isEqualTo(
            Base64.getDecoder().decode(result.path("receipts").path((String) row.get("original_key")).asText()));
    }

    private List<Map<String, Object>> reservations() {
        return jdbc.queryForList("SELECT BIN_TO_UUID(bale_id) bale_id,state,dispatch_cycle FROM bale_commitment "
            + "WHERE submission_id=UUID_TO_BIN(?) ORDER BY bale_id", submission);
    }

    private ObjectNode command(String kind) throws Exception {
        var facts = http.data("/temporary-commands/facts?warehouseId=" + warehouse + "&submissionId=" + submission);
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", kind)
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("reason", "isolated supervisor verifies unloading");
        for (String key : List.of("warehouseId", "submissionId", "workRound", "authorizationId", "freezeDeviceId",
            "sourceRecoveryEpoch", "source", "expected")) body.set(key, facts.get(key).deepCopy());
        return body;
    }

    private ObjectNode disposition(boolean close) throws Exception {
        var body = command(close ? "CLOSE_DISPOSITION" : "PHYSICAL_DISPOSITION");
        var row = jdbc.queryForMap("SELECT BIN_TO_UUID(v.set_id) evidenceSetId,v.set_sha256 evidenceSetSha256,"
            + "BIN_TO_UUID(v.id) verificationId,CAST(v.verification_version AS CHAR) verificationVersion,"
            + "CAST(v.algorithm_version AS CHAR) verificationAlgorithmVersion FROM source_evidence_verification v "
            + "WHERE v.submission_id=UUID_TO_BIN(?)", submission);
        var expected = (ObjectNode) body.path("expected");
        row.forEach((key, value) -> expected.put(key, value.toString()));
        var manifest = SubmissionJson.object().put("version", 1);
        var items = manifest.putArray("items");
        reservations().stream().map(value -> (String) value.get("bale_id")).sorted(Comparator.comparing(UUID::fromString))
            .forEach(bale -> items.add(SubmissionJson.object().put("baleId", bale).put("disposition", close ? "QUARANTINE" : "RELEASE")));
        expected.put("dispositionManifestVersion", "1").put("dispositionManifestSha256", SubmissionJson.sha(manifest));
        body.set("dispositionManifest", manifest);
        return body;
    }
}
