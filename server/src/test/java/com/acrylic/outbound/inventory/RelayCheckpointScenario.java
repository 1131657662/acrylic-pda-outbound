package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.ExportModels.Scope;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic registered FORMAL input; relay intake, processing and replay use actual HTTP. */
public final class RelayCheckpointScenario {
    private final OriginalRelayHttpFixture original;
    private final Path evidence;
    private final UUID receiveKey = UUID.randomUUID(), processKey = UUID.randomUUID();
    private final byte[] unfrozen;
    private final JsonNode evidenceReceipt;
    private JsonNode received, result;
    private byte[] receipt, changed;

    public RelayCheckpointScenario(WebApplicationContext context, Path attempt) throws Exception {
        original = new OriginalRelayHttpFixture(context);
        evidence = attempt.resolve("relay-checkpoint"); Files.createDirectories(evidence);
        unfrozen = original.bundle(null);
        evidenceReceipt = original.receive(unfrozen);
        assertThat(evidenceReceipt.path("submissionId").isNull()).isTrue();
        save("unfrozen.zip", unfrozen); save("original.zip", original.archive);
        save("unfrozen-reception.json", SubmissionJson.canonical(evidenceReceipt).getBytes(StandardCharsets.UTF_8));
    }

    public void complete(WebApplicationContext context) throws Exception {
        received = data("receive", original.supervisor.archive("/original-relays", original.archive, receiveKey), 200);
        assertThat(received.path("sourceTrust").asText()).isEqualTo("TRUSTED");
        result = data("process", original.process(id(), processKey), 200);
        assertThat(result.at("/businessReceipt/status").asText()).isEqualTo("SUCCEEDED");
        var returned = original.supervisor.get("/original-relays/results/" + result.path("resultId").asText());
        assertThat(returned.statusCode()).isEqualTo(200); receipt = returned.body();
        assertThat(SubmissionJson.parse(receipt)).isEqualTo(result); signature(context, result);
        save("receipt.json", receipt);
        changed = original.bundle(original.original.body.deepCopy().put("shortShipmentReason", "ISOLATED changed original"));
        save("changed.zip", changed); counts(original.jdbc, 2);
        var input = SubmissionJson.object().put("relayId", id()).put("resultId", result.path("resultId").asText())
            .put("receiveKey", receiveKey.toString()).put("processKey", processKey.toString())
            .put("evidenceRelayId", evidenceReceipt.path("relayId").asText())
            .put("warehouseId", original.original.web.identity().warehouse().toString())
            .put("collectorId", original.original.web.identity().user().id().toString())
            .put("relayOperatorId", original.relayUser.user().id().toString()).put("orderId", original.original.order);
        input.set("body", original.original.body); save("scenario.json", input.toPrettyString().getBytes(StandardCharsets.UTF_8));
    }

    public void verifyMissing(WebApplicationContext context, Path directory) throws Exception {
        Files.createDirectories(directory); var actor = actor(context, true);
        var jdbc = context.getBean(JdbcTemplate.class);
        var facts = new ReturnedStockCheckpointEvidence(jdbc, directory.resolve("facts"));
        record(directory, "missing-process", actor.post("/original-relays/" + id() + "/process", SubmissionJson.object(), processKey), 404);
        record(directory, "missing-receipt", actor.get("/original-relays/results/" + result.path("resultId").asText()), 404);
        evidenceOnly(actor, directory);
        facts.preserved(); counts(jdbc, 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM original_relay_result", Integer.class)).isZero();
    }

    public void verifyRestored(WebApplicationContext context, Path directory) throws Exception {
        Files.createDirectories(directory); var actor = actor(context, true); var operator = actor(context, false);
        var jdbc = context.getBean(JdbcTemplate.class);
        var facts = new ReturnedStockCheckpointEvidence(jdbc, directory.resolve("facts"));
        assertThat(unwrapped(directory, "receive-replay", actor.archive("/original-relays", original.archive, receiveKey), 200)).isEqualTo(received);
        concurrent(actor, directory);
        var returned = actor.get("/original-relays/results/" + result.path("resultId").asText());
        record(directory, "receipt-replay", returned, 200); assertThat(returned.body()).containsExactly(receipt);
        signature(context, SubmissionJson.parse(returned.body()));
        var zip = actor.get("/original-relays/" + id() + "/original");
        record(directory, "original-replay", zip, 200); assertThat(zip.body()).containsExactly(original.archive);
        assertThat(unwrapped(directory, "pda-replay", original.pdaSubmit(original.original.body), 200)).isEqualTo(result.path("businessReceipt"));
        record(directory, "changed-refusal", actor.archive("/original-relays", changed, receiveKey), 409);
        record(directory, "permission-refusal", operator.archive("/original-relays", original.archive, receiveKey), 403);
        evidenceOnly(actor, directory); facts.preserved(); counts(jdbc, 2);
    }

    public void verifyDefaultOff(WebApplicationContext context, Path directory) throws Exception {
        Files.createDirectories(directory); var actor = actor(context, true); var operator = actor(context, false);
        var jdbc = context.getBean(JdbcTemplate.class);
        var facts = new ReturnedStockCheckpointEvidence(jdbc, directory.resolve("facts"));
        for (var item : List.of(Map.entry("supervisor", actor), Map.entry("operator", operator))) {
            record(directory, item.getKey() + "-receive", item.getValue().archive("/original-relays", original.archive, receiveKey), 409);
            record(directory, item.getKey() + "-process", item.getValue().post("/original-relays/" + id() + "/process", SubmissionJson.object(), processKey), 409);
        }
        facts.preserved(); counts(jdbc, 2);
    }

    private void concurrent(CorrectionExportHttpClient actor, Path directory) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return actor.post("/original-relays/" + id() + "/process", SubmissionJson.object(), processKey); });
            var second = pool.submit(() -> { start.await(); return actor.post("/original-relays/" + id() + "/process", SubmissionJson.object(), processKey); });
            start.countDown();
            assertThat(unwrapped(directory, "process-replay-one", first.get(45, TimeUnit.SECONDS), 200)).isEqualTo(result);
            assertThat(unwrapped(directory, "process-replay-two", second.get(45, TimeUnit.SECONDS), 200)).isEqualTo(result);
        }
    }

    private void evidenceOnly(CorrectionExportHttpClient actor, Path directory) throws Exception {
        String id = evidenceReceipt.path("relayId").asText();
        assertThat(unwrapped(directory, "evidence-query", actor.get("/original-relays/" + id + "/submission"), 200).path("state").asText()).isEqualTo("EVIDENCE_ONLY");
        record(directory, "unfrozen-refusal", actor.post("/original-relays/" + id + "/process", SubmissionJson.object(), processKey), 409);
        var zip = actor.get("/original-relays/" + id + "/original");
        record(directory, "unfrozen-original", zip, 200); assertThat(zip.body()).containsExactly(unfrozen);
    }

    private CorrectionExportHttpClient actor(WebApplicationContext context, boolean supervisor) throws Exception {
        var user = supervisor ? original.relayUser.user().id() : original.original.web.identity().user().id();
        return CorrectionExportHttpClient.login(context, new Scope(user, original.original.web.identity().warehouse(), UUID.randomUUID()));
    }

    private void counts(JdbcTemplate jdbc, int expected) {
        for (String table : List.of("dispatch_record", "inventory_ledger"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE "
                + (table.equals("inventory_ledger") ? "dispatch_id IN (SELECT id FROM dispatch_record WHERE order_id=UUID_TO_BIN(?))" : "order_id=UUID_TO_BIN(?)"),
                Integer.class, original.original.order)).isEqualTo(expected);
    }

    private void signature(WebApplicationContext context, JsonNode value) throws Exception {
        String[] parts = value.path("signedRelayReceipt").asText().split("\\."); assertThat(parts).hasSize(3);
        var signer = context.getBean(com.acrylic.outbound.pda.OfflineSigner.class);
        var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(signer.publicKey().publicKey())));
        var verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        var claims = SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.path("aud").asText()).isEqualTo("acrylic-pda-relay-result");
        var unsigned = value.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) unsigned).remove("signedRelayReceipt");
        assertThat(claims.path("receipt")).isEqualTo(unsigned); assertThat(value.path("localDeletionAllowed").asBoolean()).isFalse();
    }

    private String id() { return received.path("relayId").asText(); }
    private JsonNode data(String name, HttpResponse<byte[]> response, int status) throws Exception { return unwrapped(evidence, name, response, status); }
    private JsonNode unwrapped(Path directory, String name, HttpResponse<byte[]> response, int status) throws Exception {
        record(directory, name, response, status); return CorrectionExportHttpClient.data(response, status);
    }
    private void record(Path directory, String name, HttpResponse<byte[]> response, int status) throws Exception {
        Files.write(directory.resolve(name + ".body"), response.body(), StandardOpenOption.CREATE_NEW);
        Files.writeString(directory.resolve(name + ".status"), Integer.toString(response.statusCode()), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(name).isEqualTo(status);
    }
    private void save(String name, byte[] bytes) throws Exception { Files.write(evidence.resolve(name), bytes, StandardOpenOption.CREATE_NEW); }
}
