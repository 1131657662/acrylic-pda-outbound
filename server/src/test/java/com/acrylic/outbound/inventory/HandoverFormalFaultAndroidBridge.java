package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Named target consumes the actual supervisor fault chain through real HTTP and Android Room. */
public final class HandoverFormalFaultAndroidBridge {
    private final HandoverFormalFaultSource f;
    private final Path attempt;
    private final JsonNode tokens;

    public HandoverFormalFaultAndroidBridge(WebApplicationContext app, Path attempt) throws Exception {
        this.attempt = attempt; f = new HandoverFormalFaultSource(app, attempt);
        var pda = new PdaTestDriver(f.source.r.f.web);
        tokens = pda.login(pda.register());
        approve();
        for (String kind : new String[]{"PREPARE", "ACTIVATE"}) f.workflow.web.command(f.workflow.command(kind)
            .put("targetDeviceId", tokens.path("deviceId").asText()).put("targetUserId", tokens.path("userId").asText()), 200, null);
        f.unchangedOriginal();
    }

    public void writeInput() throws Exception {
        var session = ((ObjectNode) tokens).deepCopy();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/signing-key"))
            .header("Authorization", "Bearer " + tokens.path("accessToken").asText()).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        var envelope = SubmissionJson.parse(response.body());
        session.set("signingKey", envelope.path("data")); session.put("serverRecoveryEpoch", envelope.path("serverRecoveryEpoch").asText());
        var input = SubmissionJson.object().put("fixturePurpose", "SYNTHETIC_HANDOVER_REAL_HTTP")
            .put("baseUrl", "http://127.0.0.1:19434/api/v1/").put("caseId", f.source.kase.toString()).put("deferFormalUpload", true);
        input.set("session", session);
        Files.writeString(attempt.resolve("private/handover-input.json"), SubmissionJson.canonical(input), StandardOpenOption.CREATE_NEW);
    }

    public void verifyResult() throws Exception {
        var result = SubmissionJson.parse(Files.readAllBytes(attempt.resolve("android-result.json")));
        assertThat(result.path("caseId").asText()).isEqualTo(f.source.kase.toString());
        assertThat(result.path("continuationRequests").asInt()).isEqualTo(2);
        assertThat(result.path("submissionStatus").asText()).isEqualTo("DEFERRED");
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM handover_continuation WHERE case_id=UUID_TO_BIN(?)", Integer.class,
            f.source.kase.toString())).isEqualTo(1);
        f.unchangedOriginal();
        Files.writeString(attempt.resolve("formal-fault-android-verified.marker"),
            "Actual supervisor fault chain and Android original-prefix import/continuation/reopen/freeze; final upload separately verified.");
    }

    private void approve() throws Exception {
        var valid = f.workflow.command("APPROVE").put("stopState", "FAULT_FENCED");
        var missing = valid.deepCopy(); missing.withArray("checklist").remove(0);
        var before = f.facts.snapshot("approve-reduction-before");
        f.workflow.web.command(missing, 409, "HANDOVER_CHECKLIST_MISMATCH");
        var removed = valid.deepCopy(); ((ObjectNode) removed.path("checklist").get(0)).put("state", "REMOVED");
        f.workflow.web.command(removed, 400, "HANDOVER_CHECKLIST_REQUIRED");
        ((ObjectNode) removed.path("checklist").get(0)).put("approvedEvidenceId", java.util.UUID.randomUUID().toString());
        f.workflow.web.command(removed, 409, "DISPOSITION_EVIDENCE_MISMATCH");
        assertThat(f.facts.snapshot("approve-reduction-after")).isEqualTo(before);
        f.workflow.web.command(valid, 200, null);
    }

    public void completeWork() throws Exception { new HandoverFormalFaultCompletion(f, tokens, attempt).verify(); }

    public void restart(WebApplicationContext app) throws Exception {
        var jdbc = app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        new HandoverFormalFaultCompletion(f, tokens, attempt).restart(jdbc);
    }
}
