package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP renewal must preserve the signed purpose of both kinds of assignment. */
public final class HandoverPdaRenewalChecks {
    private final HandoverApprovalWorkflowChecks workflow;
    private final WebApplicationContext app;
    private final JdbcTemplate jdbc;
    private final Path evidence;
    private final OfflineSigner.PublicKeyView signingKey;
    private final HttpClient http = HttpClient.newHttpClient();
    private int requests;

    public HandoverPdaRenewalChecks(HandoverApprovalWorkflowChecks workflow, WebApplicationContext app,
        Path attempt) throws Exception {
        this.workflow = workflow;
        this.app = app;
        jdbc = workflow.f.jdbc;
        evidence = Files.createDirectory(attempt.resolve("authorization-renewal"));
        signingKey = app.getBean(OfflineSigner.class).publicKey();
    }

    public void verify() throws Exception {
        var target = SubmissionJson.parse(Files.readAllBytes(evidence.getParent()
            .resolve("receiving-assignment/assigned.json"))).path("data");
        verifyRenewal(target, workflow.f.r.f.tokens, "pda:handover-import");
        ordinaryScan();
        Files.writeString(evidence.resolve("verified.txt"),
            "RSA signatures verified; import and scan scopes preserved; same-key data replay; old credentials retained.");
    }

    private void ordinaryScan() throws Exception {
        var source = workflow.f.r.f.web;
        var identity = IdentityFixture.create(jdbc, app.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        var pda = new PdaTestDriver(new PublicationDriver(source.mvc(), source.json(), identity));
        var order = pda.readyOrder();
        var tokens = pda.login(pda.register());
        var assignment = pda.data(pda.claim(order, UUID.randomUUID().toString()), tokens);
        verifyRenewal(assignment, tokens, "pda:scan");
    }

    private void verifyRenewal(JsonNode first, JsonNode tokens, String scope) throws Exception {
        assertThat(verifiedClaims(first).path("scope").asText()).isEqualTo(scope);
        var key = UUID.randomUUID().toString();
        var renewed = renew(first, tokens, key);
        var claims = verifiedClaims(renewed);
        assertThat(claims.path("scope").asText()).isEqualTo(scope);
        for (String field : List.of("id", "orderId", "deviceId", "operatorId", "epoch", "startOrderScanSeq"))
            assertThat(renewed.path(field)).as(field).isEqualTo(first.path(field));
        assertThat(renewed.path("offlineAuthorizationId")).isNotEqualTo(first.path("offlineAuthorizationId"));
        assertThat(renewed.path("authorizationSequence").asText()).isEqualTo("1");
        assertThat(claims.path("renewedFromAuthorizationId")).isEqualTo(first.path("offlineAuthorizationId"));
        assertThat(renew(first, tokens, key)).isEqualTo(renewed);
        assertRetained(first, renewed);
    }

    private void assertRetained(JsonNode first, JsonNode renewed) {
        var original = Ids.bytes(UUID.fromString(first.path("offlineAuthorizationId").asText()));
        assertThat(jdbc.queryForObject("SELECT signed_token FROM offline_authorization WHERE id=?", String.class, original))
            .isEqualTo(first.path("offlineToken").asText());
        assertThat(jdbc.queryForObject("SELECT expires_at FROM offline_authorization WHERE id=?", java.sql.Timestamp.class, original)
            .toInstant()).isEqualTo(java.time.Instant.parse(first.path("expiresAt").asText()));
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(replaced_by) FROM offline_authorization WHERE id=?", String.class, original))
            .isEqualTo(renewed.path("offlineAuthorizationId").asText());
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM offline_authorization WHERE id=?", java.sql.Timestamp.class, original))
            .isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offline_authorization WHERE assignment_id=?", Integer.class,
            Ids.bytes(UUID.fromString(first.path("id").asText())))).isEqualTo(2);
    }

    private JsonNode renew(JsonNode assignment, JsonNode tokens, String key) throws Exception {
        var body = SubmissionJson.object().put("authorizationId", assignment.path("offlineAuthorizationId").asText())
            .put("assignmentEpoch", assignment.path("epoch").asText()).put("orderId", assignment.path("orderId").asText())
            .put("revision", assignment.path("revision").asInt()).put("pendingInputCount", 0);
        var path = "/api/v1/pda/assignments/" + assignment.path("id").asText() + "/renewal";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434" + path))
            .timeout(java.time.Duration.ofSeconds(30)).header("Authorization", "Bearer " + tokens.path("accessToken").asText())
            .header("Idempotency-Key", key).header("X-Server-Recovery-Epoch", "1").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int number = ++requests;
        Files.writeString(evidence.resolve(number + "-request.json"), body.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.write(evidence.resolve(number + "-response.json"), response.body(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }

    private JsonNode verifiedClaims(JsonNode assignment) throws Exception {
        var pieces = assignment.path("offlineToken").asText().split("\\.");
        assertThat(pieces).hasSize(3);
        var header = SubmissionJson.parse(Base64.getUrlDecoder().decode(pieces[0]));
        assertThat(header.path("alg").asText()).isEqualTo("RS256");
        assertThat(header.path("kid").asText()).isEqualTo(signingKey.keyId());
        var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(signingKey.publicKey())));
        var verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((pieces[0] + "." + pieces[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(pieces[2]))).isTrue();
        var claims = SubmissionJson.parse(Base64.getUrlDecoder().decode(pieces[1]));
        assertThat(claims.path("jti")).isEqualTo(assignment.path("offlineAuthorizationId"));
        return claims;
    }
}
