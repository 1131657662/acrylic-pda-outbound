package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP preparation, payload download and acknowledgement for the activated receiver. */
public final class HandoverPdaDownloadChecks {
    private static final Set<String> PART_NAMES = Set.of("plan", "inventory", "availability");
    private final HandoverApprovalWorkflowChecks workflow;
    private final JdbcTemplate jdbc;
    private final Path evidence;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String base;

    public HandoverPdaDownloadChecks(HandoverApprovalWorkflowChecks workflow, Path attempt) throws Exception {
        this.workflow = workflow;
        jdbc = workflow.f.jdbc;
        evidence = Files.createDirectory(attempt.resolve("receiving-download"));
        var assignment = assignment();
        base = "/api/v1/pda/assignments/" + assignment.path("id").asText();
    }

    public void verify() throws Exception {
        var assignment = assignment();
        var first = prepare("first", "1", 200, null);
        var manifest = SubmissionJson.parse(first.path("manifest").asText());
        assertThat(first.path("bundleId").asText()).isNotBlank();
        assertThat(PdaTestDriver.sha(first.path("manifest").asText())).isEqualTo(first.path("manifestSha256").asText());
        assertManifestBindings(manifest, assignment);
        verifySignature(first, assignment);
        downloadParts(first, manifest);
        assertThat(prepare("replay", "1", 200, null)).isEqualTo(first);
        prepare("wrong-epoch", "2", 409, "SERVER_RECOVERY_EPOCH_MISMATCH");
        content("wrong-bundle", "plan", UUID.randomUUID().toString(), 404, "NOT_FOUND");
        otherDeviceCannotRead(first);
        acknowledge(first);
        assertThat(jdbc.queryForObject("SELECT download_ack_at IS NOT NULL FROM order_assignment WHERE id=?", Boolean.class,
            Ids.bytes(UUID.fromString(assignment.path("id").asText())))).isTrue();
        proveAssignMismatch(first);
    }

    private JsonNode assignment() throws Exception {
        return SubmissionJson.parse(Files.readAllBytes(evidence.getParent().resolve("receiving-assignment/assigned.json"))).path("data");
    }

    private void assertManifestBindings(JsonNode manifest, JsonNode assignment) {
        assertThat(manifest.path("assignmentId").asText()).isEqualTo(assignment.path("id").asText());
        assertThat(manifest.path("orderId").asText()).isEqualTo(assignment.path("orderId").asText());
        assertThat(manifest.path("deviceId").asText()).isEqualTo(assignment.path("deviceId").asText());
        assertThat(manifest.path("operatorId").asText()).isEqualTo(assignment.path("operatorId").asText());
        assertThat(manifest.path("assignmentEpoch").asText()).isEqualTo(assignment.path("epoch").asText());
        assertThat(manifest.path("serverRecoveryEpoch").asText()).isEqualTo("1");
        assertThat(manifest.path("inventorySnapshotId").asText()).isEqualTo(assignment.path("inventorySnapshotId").asText());
        assertThat(manifest.path("revision").asInt()).isEqualTo(assignment.path("revision").asInt());
        var names = new HashSet<String>();
        for (JsonNode part : manifest.path("parts")) names.add(part.path("name").asText());
        assertThat(names).isEqualTo(PART_NAMES);
    }

    private JsonNode prepare(String name, String epoch, int status, String code) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434" + base + "/preparation"))
            .timeout(Duration.ofSeconds(30)).header("Authorization", bearer()).header("X-Server-Recovery-Epoch", epoch)
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.noBody()).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence.resolve("preparation-" + name + ".json"), response.body());
        assertThat(response.statusCode()).as(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(status);
        var envelope = SubmissionJson.parse(response.body());
        if (code != null) assertThat(envelope.path("code").asText()).isEqualTo(code);
        return status == 200 ? envelope.path("data") : envelope;
    }

    private void downloadParts(JsonNode receipt, JsonNode manifest) throws Exception {
        for (JsonNode part : manifest.path("parts")) {
            var bytes = content(part.path("name").asText(), part.path("name").asText(), receipt.path("bundleId").asText(), 200, null);
            assertThat(bytes.length).isEqualTo(part.path("compressedBytes").asInt());
            assertThat(sha(bytes)).isEqualTo(part.path("compressedSha256").asText());
            var raw = new GZIPInputStream(new ByteArrayInputStream(bytes)).readAllBytes();
            assertThat(raw.length).isEqualTo(part.path("bytes").asInt());
            assertThat(sha(raw)).isEqualTo(part.path("sha256").asText());
        }
    }

    private byte[] content(String evidenceName, String part, String bundle, int status, String code) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434" + base + "/content/" + part + "?bundleId=" + bundle))
            .timeout(Duration.ofSeconds(30)).header("Authorization", bearer()).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence.resolve("content-" + evidenceName + ".bin"), response.body());
        assertThat(response.statusCode()).as(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(status);
        if (code != null) assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(code);
        return response.body();
    }

    private void verifySignature(JsonNode receipt, JsonNode assignment) throws Exception {
        var key = get("/api/v1/pda/signing-key", "signing-key").path("data");
        var token = receipt.path("signedManifest").asText().split("\\.");
        assertThat(token).hasSize(3);
        var header = SubmissionJson.parse(Base64.getUrlDecoder().decode(token[0]));
        assertThat(header.path("alg").asText()).isEqualTo("RS256");
        assertThat(header.path("kid")).isEqualTo(key.path("keyId"));
        var verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(
            Base64.getDecoder().decode(key.path("publicKey").asText()))));
        verifier.update((token[0] + "." + token[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(token[2]))).isTrue();
        var claims = SubmissionJson.parse(Base64.getUrlDecoder().decode(token[1]));
        assertThat(claims.path("aud").asText()).isEqualTo("acrylic-pda-preparation");
        assertThat(claims.path("manifestSha256")).isEqualTo(receipt.path("manifestSha256"));
        assertThat(claims.path("assignmentId").asText()).isEqualTo(assignment.path("id").asText());
    }

    private void acknowledge(JsonNode receipt) throws Exception {
        var assignment = assignment();
        var body = SubmissionJson.object().put("bundleId", receipt.path("bundleId").asText())
            .put("manifestSha256", receipt.path("manifestSha256").asText()).put("assignmentEpoch", assignment.path("epoch").asText())
            .put("inventorySnapshotId", assignment.path("inventorySnapshotId").asText()).put("activatedAt", Instant.now().toString());
        var first = post("download-ack", body, "ack-first", 200, null);
        var replay = post("download-ack", body, "ack-replay", 200, null);
        assertThat(SubmissionJson.parse(replay).path("data")).isEqualTo(SubmissionJson.parse(first).path("data"));
    }

    private byte[] post(String path, ObjectNode body, String name, int status, String code) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434" + base + "/" + path))
            .timeout(Duration.ofSeconds(30)).header("Authorization", bearer()).header("X-Server-Recovery-Epoch", "1")
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence.resolve(name + ".json"), response.body());
        assertThat(response.statusCode()).as(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(status);
        if (code != null) assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(code);
        return response.body();
    }

    private void otherDeviceCannotRead(JsonNode receipt) throws Exception {
        var pda = new PdaTestDriver(workflow.f.r.f.web);
        var other = pda.login(pda.register());
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434" + base + "/content/inventory?bundleId="
                + receipt.path("bundleId").asText())).timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + other.path("accessToken").asText()).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence.resolve("content-other-device.json"), response.body());
        assertThat(response.statusCode()).isEqualTo(404);
    }

    private void proveAssignMismatch(JsonNode receipt) throws Exception {
        var role = jdbc.queryForObject("SELECT id FROM role WHERE code='WAREHOUSE_ADMIN'", byte[].class);
        var permission = jdbc.queryForObject("SELECT id FROM permission WHERE code='pda:assign'", byte[].class);
        jdbc.update("DELETE FROM role_permission WHERE role_id=? AND permission_id=?", role, permission);
        try {
            prepare("receiver-without-assign", "1", 403, "PERMISSION_DENIED");
            content("receiver-without-assign-content", "inventory", receipt.path("bundleId").asText(), 200, null);
        } finally {
            jdbc.update("INSERT INTO role_permission(role_id,permission_id) VALUES(?,?)", role, permission);
        }
    }

    private String bearer() { return "Bearer " + workflow.f.r.f.tokens.path("accessToken").asText(); }

    private JsonNode get(String path, String name) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434" + path)).timeout(Duration.ofSeconds(30))
            .header("Authorization", bearer()).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence.resolve(name + ".json"), response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        return SubmissionJson.parse(response.body());
    }

    private static String sha(byte[] bytes) { return ImportFiles.sha256(bytes); }
}
