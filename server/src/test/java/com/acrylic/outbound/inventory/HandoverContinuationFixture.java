package com.acrylic.outbound.inventory;

import com.acrylic.outbound.continuation.DispositionProofs;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Two generated devices. All handover and preparation transitions use HTTP handlers. */
final class HandoverContinuationFixture {
    final HandoverApprovalWorkflowChecks workflow;
    final HandoverStopFixture source;
    final JsonNode tokens;
    final JsonNode assignment;
    final ObjectNode command;
    private final AtomicInteger calls = new AtomicInteger();

    HandoverContinuationFixture(WebApplicationContext app, Path attempt) throws Exception {
        workflow = new HandoverApprovalWorkflowChecks(app, attempt);
        source = workflow.f;
        var pda = new PdaTestDriver(source.r.f.web);
        tokens = pda.login(pda.register());
        source.post(source.command, source.r.f.tokens, 200, null);
        request("POST", "/submissions/" + source.r.f.id() + "/abort", source.r.f.abortBody(), source.r.f.tokens, 200);
        workflow.web.command(workflow.command("APPROVE"), 200, null);
        for (String kind : new String[]{"PREPARE", "ACTIVATE"}) workflow.web.command(workflow.command(kind)
            .put("targetDeviceId", tokens.path("deviceId").asText()).put("targetUserId", tokens.path("userId").asText()), 200, null);
        assignment = SubmissionJson.parse(request("GET", "/handovers/" + source.kase + "/assignment", null, tokens, 200)).path("data");
        command = SubmissionJson.object().put("commandVersion", 1).put("commandId", UUID.randomUUID().toString())
            .put("assignmentId", assignment.path("id").asText()).put("authorizationId", assignment.path("offlineAuthorizationId").asText())
            .put("payloadSha256", source.command.path("payloadSha256").asText())
            .put("retainedManifestSha256", SubmissionJson.sha(DispositionProofs.effective(source.r.body)))
            .put("nextOrderScanSeq", assignment.path("startOrderScanSeq").asText()).put("planSha256", assignment.path("planSha256").asText());
    }

    void acknowledge() throws Exception {
        String path = "/assignments/" + assignment.path("id").asText();
        var bundle = SubmissionJson.parse(request("POST", path + "/preparation", SubmissionJson.object(), tokens, 200)).path("data");
        var ack = SubmissionJson.object().put("bundleId", bundle.path("bundleId").asText())
            .put("manifestSha256", bundle.path("manifestSha256").asText()).put("assignmentEpoch", assignment.path("epoch").asText())
            .put("inventorySnapshotId", assignment.path("inventorySnapshotId").asText()).put("activatedAt", Instant.now().toString());
        request("POST", path + "/download-ack", ack, tokens, 200);
    }

    byte[] resume(ObjectNode body, JsonNode identity, int expected) throws Exception {
        return request("POST", "/handovers/" + source.kase + "/continuation", body, identity, expected);
    }

    private byte[] request(String method, String path, JsonNode body, JsonNode identity, int expected) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda" + path))
            .timeout(java.time.Duration.ofSeconds(40)).header("Content-Type", "application/json")
            .header("X-Server-Recovery-Epoch", "1").header("Authorization", "Bearer " + identity.path("accessToken").asText());
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body)));
        var response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        Files.write(source.directory.resolve("continuation-http-" + calls.incrementAndGet() + ".json"), response.body(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(expected);
        return response.body();
    }
}
