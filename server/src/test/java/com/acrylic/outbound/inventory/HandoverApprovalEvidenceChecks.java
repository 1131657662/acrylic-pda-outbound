package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.handover.HandoverApprovalEvidenceProbe;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Real stop and original-device abort HTTP; downstream consumption runs on the actual service and database. */
public final class HandoverApprovalEvidenceChecks {
    private final HandoverStopFixture f;
    private byte[] stop;
    public HandoverApprovalEvidenceChecks(WebApplicationContext app, Path attempt) throws Exception {
        f = new HandoverStopFixture(app, attempt.resolve("approval-evidence"));
    }

    public void verify(WebApplicationContext app) throws Exception {
        var facts = new HandoverStopFacts(f, f.jdbc); var before = facts.snapshot();
        reject(app, 2, "HANDOVER_STOP_NOT_PROVEN"); facts.unchanged(before);
        stop = f.post(f.command, f.r.f.tokens, 200, null); abort();
        before = facts.snapshot(); consume(app, "consumed"); facts.unchanged(before);
        reject(app, 1, "HANDOVER_EVIDENCE_CHANGED"); facts.unchanged(before);
        unavailableFile(app); corruptFile(app);
        before = facts.snapshot();
        assertThat(f.post(f.command, f.r.f.tokens, 200, null)).isEqualTo(stop); facts.unchanged(before);
    }

    private void abort() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/submissions/" + f.r.f.id() + "/abort"))
            .timeout(java.time.Duration.ofSeconds(30)).header("Content-Type", "application/json")
            .header("X-Server-Recovery-Epoch", "1").header("Authorization", "Bearer " + f.r.f.tokens.path("accessToken").asText())
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(f.r.f.abortBody()))).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        Files.writeString(f.directory.resolve("abort.json"), response.body(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(SubmissionJson.parse(response.body()).path("data").path("status").asText()).isEqualTo("ABORTED");
    }

    private void unavailableFile(WebApplicationContext app) throws Exception {
        var path = file(); var retained = path.resolveSibling(path.getFileName() + ".approval-missing-retained");
        Files.move(path, retained);
        try {
            var facts = new HandoverStopFacts(f, f.jdbc); var before = facts.snapshot();
            reject(app, 2, "HANDOVER_STOP_FILE_UNAVAILABLE"); facts.unchanged(before);
        } finally { Files.move(retained, path); }
    }

    private void corruptFile(WebApplicationContext app) throws Exception {
        var path = file(); var original = path.resolveSibling(path.getFileName() + ".approval-original-retained");
        Files.copy(path, original);
        try {
            Files.writeString(path, "{}");
            var facts = new HandoverStopFacts(f, f.jdbc); var before = facts.snapshot();
            reject(app, 2, "HANDOVER_STOP_FILE_UNAVAILABLE"); facts.unchanged(before);
        } finally {
            Files.move(path, path.resolveSibling(path.getFileName() + ".approval-corrupt-retained"));
            Files.copy(original, path);
        }
    }

    private Path file() { return f.directory.getParent().resolve("storage/submissions/" + f.command.path("payloadSha256").asText() + ".json"); }

    private void consume(WebApplicationContext app, String name) throws Exception {
        var result = HandoverApprovalEvidenceProbe.consume(app, f.kase, 2);
        assertThat(result).isEqualTo(f.r.body);
        Files.writeString(f.directory.resolve(name + ".json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private void reject(WebApplicationContext app, long high, String code) {
        assertThatThrownBy(() -> HandoverApprovalEvidenceProbe.consume(app, f.kase, high)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo(code));
    }

    public void restart(WebApplicationContext app) throws Exception {
        var facts = new HandoverStopFacts(f, app.getBean(JdbcTemplate.class)); var before = facts.snapshot();
        consume(app, "restart-consumed");
        assertThat(f.post(f.command, f.r.f.tokens, 200, null)).isEqualTo(stop); facts.unchanged(before);
    }
}
