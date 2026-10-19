package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.e2averify.E2aSnapshotRows;
import com.acrylic.outbound.relay.RelayArchive;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual retained native package; all new supervisors, stock, target and physical assertions are synthetic. */
public final class IndependentCheckpointScenario {
    private final Path evidence;
    private final JdbcTemplate jdbc;
    private final RelayArchive.Bundle original;
    private final byte[] archive;
    private final UUID supervisor, operator, target, key = UUID.randomUUID();
    private final String relay;
    private final ObjectNode intent;
    private JsonNode result;

    public IndependentCheckpointScenario(WebApplicationContext context, Path attempt) throws Exception {
        evidence = attempt.resolve("independent-checkpoint"); Files.createDirectories(evidence);
        jdbc = context.getBean(JdbcTemplate.class);
        archive = Files.readAllBytes(Path.of(System.getenv("E2A_CROSS_ROOT"))
            .resolve(".local/goal-20261013/e06-closure-computer-02/actual-original.zip"));
        original = RelayArchive.read(archive); assertThat(original.original().source()).isEqualTo("INDEPENDENT");
        supervisor = identity(context, true); operator = identity(context, false);
        var wire = actor(context, supervisor);
        relay = data(evidence, "receive", wire.upload("/original-relays", archive, "application/zip", UUID.randomUUID(), null), 200).path("relayId").asText();
        var inventory = page(wire, "inventory", "initial-inventory");
        assertThat(inventory.path("hasMore").asBoolean()).isFalse(); assertThat(inventory.path("items").size()).isEqualTo(2);
        target = IndependentCheckpointTarget.create(jdbc, original, inventory);
        var physical = physical(wire);
        var candidates = page(wire, "candidates", "full-candidates"); assertThat(candidates.path("hasMore").asBoolean()).isFalse();
        intent = associationIntent(physical, candidates);
        save("original.zip", archive); save("intent.json", SubmissionJson.canonical(intent).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private ObjectNode associationIntent(JsonNode physical, JsonNode candidates) {
        var body = SubmissionJson.object().put("contextSha256", candidates.path("contextSha256").asText())
            .put("physicalCheckId", physical.at("/receipt/receiptId").asText()).put("targetOrderId", target.toString())
            .put("reason", "ISOLATED explicit association of original native task; not a field decision");
        body.putObject("shortfallApproval")
            .put("originalShortfall", original.closure().path("shortfall").asText())
            .put("targetShortfall", "0")
            .put("reason", "ISOLATED supervisor approved the retained original shortfall; target plan is fully covered");
        var exclusions = body.putArray("candidateExclusions");
        for (var candidate : candidates.path("items")) if (!candidate.path("id").asText().equals(target.toString()))
            exclusions.addObject().put("orderId", candidate.path("id").asText()).put("reason", "ISOLATED reviewed distinct synthetic target");
        return body;
    }

    public void complete(WebApplicationContext context) throws Exception {
        var wire = actor(context, supervisor);
        save("source-before.json", new E2aSnapshotRows(jdbc).capture().toPrettyString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        result = data(evidence, "association", wire.post(route("/associations"), intent, key), 200);
        assertThat(result.at("/receipt/associationRecorded").asBoolean()).isTrue(); capabilities(result.path("receipt"));
        save("source-after.json", new E2aSnapshotRows(jdbc).capture().toPrettyString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var summary = SubmissionJson.object().put("relayId", relay).put("targetId", target.toString()).put("key", key.toString())
            .put("supervisorId", supervisor.toString()).put("operatorId", operator.toString())
            .put("warehouseId", original.original().warehouse().toString()).put("taskId", original.original().localOrder().toString());
        summary.set("result", result); save("scenario.json", summary.toPrettyString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        targetUnprocessed(jdbc);
    }

    public void verifyMissing(WebApplicationContext context, Path directory) throws Exception {
        Files.createDirectories(directory); var wire = actor(context, supervisor); var db = context.getBean(JdbcTemplate.class);
        var facts = new ReturnedStockCheckpointEvidence(db, directory.resolve("facts"));
        data(directory, "missing-association", wire.get(route("/associations/" + result.at("/receipt/associationId").asText())), 404);
        data(directory, "evidence-only", wire.post(route("/process"), SubmissionJson.object(), UUID.randomUUID()), 409);
        var state = data(directory, "original-state", wire.get(route("/submission")), 200);
        assertThat(state.path("state").asText()).isEqualTo("EVIDENCE_ONLY");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM independent_association", Integer.class)).isZero();
        facts.preserved(); targetUnprocessed(db);
    }

    public void verifyRestored(WebApplicationContext context, Path directory) throws Exception {
        Files.createDirectories(directory); var wire = actor(context, supervisor); var denied = actor(context, operator);
        var db = context.getBean(JdbcTemplate.class); var facts = new ReturnedStockCheckpointEvidence(db, directory.resolve("facts"));
        assertThat(data(directory, "same-key", wire.post(route("/associations"), intent, key), 200)).isEqualTo(result);
        assertThat(data(directory, "by-task", wire.get(route("/associations")), 200)).isEqualTo(result);
        assertThat(data(directory, "by-id", wire.get(route("/associations/" + result.at("/receipt/associationId").asText())), 200)).isEqualTo(result);
        data(directory, "changed-refusal", wire.post(route("/associations"), intent.deepCopy().put("reason", "ISOLATED changed"), key), 409);
        data(directory, "permission-refusal", denied.post(route("/associations"), intent, key), 403);
        data(directory, "evidence-only", wire.post(route("/process"), SubmissionJson.object(), UUID.randomUUID()), 409);
        var downloaded = wire.get(route("/original")); record(directory, "original-replay", downloaded, 200);
        assertThat(downloaded.body()).containsExactly(archive); capabilities(result.path("receipt"));
        facts.preserved(); targetUnprocessed(db);
    }

    public void verifyDefaultOff(WebApplicationContext context, Path directory) throws Exception {
        Files.createDirectories(directory); var wire = actor(context, supervisor); var denied = actor(context, operator);
        var db = context.getBean(JdbcTemplate.class); var facts = new ReturnedStockCheckpointEvidence(db, directory.resolve("facts"));
        data(directory, "supervisor-refusal", wire.post(route("/associations"), intent, key), 409);
        data(directory, "operator-refusal", denied.post(route("/associations"), intent, key), 403);
        facts.preserved(); targetUnprocessed(db);
    }

    public void verifyShortfallOff(WebApplicationContext context, Path directory) throws Exception {
        Files.createDirectories(directory); var wire = actor(context, supervisor); var denied = actor(context, operator);
        var db = context.getBean(JdbcTemplate.class); var facts = new ReturnedStockCheckpointEvidence(db, directory.resolve("facts"));
        var refusal = wire.post(route("/associations"), intent, key);
        record(directory, "supervisor-refusal", refusal, 409);
        assertThat(SubmissionJson.parse(refusal.body()).path("code").asText()).isEqualTo("INDEPENDENT_SHORTFALL_DISABLED");
        data(directory, "operator-refusal", denied.post(route("/associations"), intent, key), 403);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM independent_association", Integer.class)).isZero();
        facts.preserved(); targetUnprocessed(db);
    }

    private JsonNode physical(IndependentCheckpointHttp wire) throws Exception {
        byte[] raw = "ISOLATED synthetic physical evidence; original native package unchanged".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var attachment = data(evidence, "attachment", wire.upload(route("/physical/attachments"), raw, "application/octet-stream", UUID.randomUUID(), ImportFiles.sha256(raw)), 200);
        save("attachment-original.bin", raw);
        var inventory = page(wire, "inventory", "physical-inventory");
        var body = SubmissionJson.object().put("contextSha256", inventory.path("contextSha256").asText())
            .put("checkedAt", Instant.now().toString()).put("area", "ISOLATED vehicle bay").put("reason", "Synthetic assertions, not field facts");
        var packages = body.putArray("packages");
        for (var row : inventory.path("items")) packages.addObject().put("captureId", row.path("captureId").asText()).put("status", "LOADED").put("note", "");
        body.putArray("attachments").add(attachment); return data(evidence, "physical", wire.post(route("/physical/checks"), body, UUID.randomUUID()), 200);
    }

    private UUID identity(WebApplicationContext context, boolean elevated) {
        var identity = IdentityFixture.create(jdbc, context.getBean(PasswordEncoder.class), elevated ? "SUPERVISOR" : "WAREHOUSE_ADMIN");
        UUID user = identity.user().id();
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(user), Ids.bytes(original.original().warehouse()));
        if (elevated) jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='TEMPORARY_ORDER_CONFIRMER'", Ids.bytes(user));
        return user;
    }

    private IndependentCheckpointHttp actor(WebApplicationContext context, UUID user) throws Exception {
        return IndependentCheckpointHttp.login(context, user, original.original().warehouse());
    }
    private JsonNode page(IndependentCheckpointHttp wire, String section, String name) throws Exception {
        return data(evidence, name, wire.get(route("/independent-reconciliation?section=" + section + "&limit=200")), 200);
    }
    private void targetUnprocessed(JdbcTemplate db) {
        assertThat(db.queryForObject("SELECT COUNT(*) FROM submission_scope WHERE source_order_id=UUID_TO_BIN(?)"
            + " OR canonical_order_id=UUID_TO_BIN(?)", Integer.class, target.toString(), target.toString())).isZero();
        for (String table : List.of("dispatch_record", "order_result_version"))
            assertThat(db.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE order_id=UUID_TO_BIN(?)", Integer.class, target.toString())).isZero();
        assertThat(db.queryForObject("SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)", String.class, target.toString())).isEqualTo("DRAFT");
    }
    private void capabilities(JsonNode receipt) {
        for (String name : List.of("stockAvailable", "businessProcessed", "releaseAllowed", "localDeletionAllowed", "newCollectionAuthorized"))
            assertThat(receipt.path(name).asBoolean()).as(name).isFalse();
    }
    private String route(String suffix) { return "/original-relays/" + relay + suffix; }
    private JsonNode data(Path directory, String name, HttpResponse<byte[]> response, int status) throws Exception {
        record(directory, name, response, status); return CorrectionExportHttpClient.data(response, status);
    }
    private void record(Path directory, String name, HttpResponse<byte[]> response, int status) throws Exception {
        Files.write(directory.resolve(name + ".body"), response.body(), StandardOpenOption.CREATE_NEW);
        Files.writeString(directory.resolve(name + ".status"), Integer.toString(response.statusCode()), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(name).isEqualTo(status);
    }
    private void save(String name, byte[] value) throws Exception { Files.write(evidence.resolve(name), value, StandardOpenOption.CREATE_NEW); }
}
