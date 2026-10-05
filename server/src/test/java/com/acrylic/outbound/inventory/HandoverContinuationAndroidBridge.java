package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Two generated device identities; the Android process consumes this live server's signed bytes. */
public final class HandoverContinuationAndroidBridge {
    private final HandoverContinuationFixture fixture;
    private final Path attempt;

    public HandoverContinuationAndroidBridge(WebApplicationContext app, Path attempt) throws Exception {
        this.fixture = new HandoverContinuationFixture(app, attempt, true);
        this.attempt = attempt;
    }

    public void writeInput() throws Exception {
        var session = ((ObjectNode) fixture.tokens).deepCopy();
        assertThat(session.path("deviceId").asText()).isNotEqualTo(fixture.source.r.f.tokens.path("deviceId").asText());
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/signing-key"))
            .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + session.path("accessToken").asText()).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        var envelope = SubmissionJson.parse(response.body());
        session.set("signingKey", envelope.path("data"));
        session.put("serverRecoveryEpoch", envelope.path("serverRecoveryEpoch").asText());
        var input = SubmissionJson.object().put("fixturePurpose", "SYNTHETIC_HANDOVER_REAL_HTTP")
            .put("baseUrl", "http://127.0.0.1:19434/api/v1/").put("caseId", fixture.source.kase.toString());
        input.set("session", session);
        Files.writeString(attempt.resolve("private/handover-input.json"), SubmissionJson.canonical(input), StandardOpenOption.CREATE_NEW);
    }

    public void verifyResult() throws Exception {
        var result = SubmissionJson.parse(Files.readAllBytes(attempt.resolve("android-result.json")));
        assertThat(result.path("caseId").asText()).isEqualTo(fixture.source.kase.toString());
        assertThat(result.path("state").asText()).isEqualTo("FROZEN");
        assertThat(result.path("continuationRequests").asInt()).isEqualTo(2);
        assertThat(result.path("submissionStatus").asText()).isEqualTo("SYNCED");
        var jdbc = fixture.source.jdbc;
        var caseId = Ids.bytes(fixture.source.kase);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_continuation WHERE case_id=?", Long.class, caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(order_continuation_id) FROM handover_continuation WHERE case_id=?",
            String.class, caseId)).isEqualTo(result.path("continuationId").asText());
        var command = Files.readString(attempt.resolve("android-continuation-command.json"));
        assertThat(jdbc.queryForObject("SELECT command_text FROM handover_continuation WHERE case_id=?", String.class, caseId))
            .isEqualTo(SubmissionJson.canonical(SubmissionJson.parse(command)));
        var sourceId = Ids.bytes(UUID.fromString(fixture.source.r.f.id()));
        assertThat(jdbc.queryForObject("SELECT status FROM submission WHERE id=?", String.class, sourceId)).isEqualTo("ABORTED");
        Files.writeString(attempt.resolve("server-android-verified.marker"),
            "Same-instance continuation replay, signed Android consumption and original-prefix submission accepted; synthetic identities, JVM Room.");
    }
}
