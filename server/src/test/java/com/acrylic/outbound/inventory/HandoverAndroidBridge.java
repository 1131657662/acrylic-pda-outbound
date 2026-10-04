package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Exports only test credentials; Android downloads signed material directly from this live server. */
public final class HandoverAndroidBridge {
    private final HandoverApprovalWorkflowChecks workflow;
    private final Path attempt;

    public HandoverAndroidBridge(HandoverApprovalWorkflowChecks workflow, Path attempt) {
        this.workflow = workflow;
        this.attempt = attempt;
    }

    public void writeInput() throws Exception {
        var session = ((ObjectNode) workflow.f.r.f.tokens).deepCopy();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/signing-key"))
            .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + session.path("accessToken").asText()).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        var envelope = SubmissionJson.parse(response.body());
        session.set("signingKey", envelope.path("data"));
        session.put("serverRecoveryEpoch", envelope.path("serverRecoveryEpoch").asText());
        var input = SubmissionJson.object().put("fixturePurpose", "SYNTHETIC_HANDOVER_REAL_HTTP")
            .put("baseUrl", "http://127.0.0.1:19434/api/v1/").put("caseId", workflow.f.kase.toString());
        input.set("session", session);
        Files.writeString(attempt.resolve("private/handover-input.json"), SubmissionJson.canonical(input), StandardOpenOption.CREATE_NEW);
    }

    public void verifyResult() throws Exception {
        var result = SubmissionJson.parse(Files.readAllBytes(attempt.resolve("android-result.json")));
        assertThat(result.path("caseId").asText()).isEqualTo(workflow.f.kase.toString());
        assertThat(result.path("state").asText()).isEqualTo("HANDOVER_FROZEN");
        assertThat(result.path("ackRequests").asInt()).isEqualTo(2);
        assertThat(result.path("sourceSha256").asText()).isEqualTo(workflow.f.command.path("payloadSha256").asText());
        assertThat(result.path("captures").asInt()).isEqualTo(workflow.f.r.body.path("captures").size());
        var target = Ids.bytes(UUID.fromString(result.path("assignmentId").asText()));
        var ack = workflow.f.jdbc.queryForObject("SELECT download_ack_at FROM order_assignment WHERE id=?", java.sql.Timestamp.class, target);
        assertThat(ack.toInstant()).isEqualTo(java.time.Instant.parse(result.path("ackAt").asText()));
        assertThat(workflow.f.jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?", String.class,
            Ids.bytes(workflow.f.kase))).isEqualTo("ACTIVATED");
        Files.writeString(attempt.resolve("server-android-verified.marker"),
            "Android persisted original source digest and matching server acknowledgement; receiving work stays frozen.");
    }
}
