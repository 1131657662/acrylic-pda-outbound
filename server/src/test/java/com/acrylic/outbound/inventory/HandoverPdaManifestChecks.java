package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Real bearer read after the existing authentic stop and approval workflow. */
public final class HandoverPdaManifestChecks {
    private final HandoverApprovalWorkflowChecks workflow;
    private final Path evidence;
    private final JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();

    public HandoverPdaManifestChecks(HandoverApprovalWorkflowChecks workflow, Path attempt) throws Exception {
        this.workflow = workflow;
        evidence = Files.createDirectory(attempt.resolve("receiving-manifest"));
        jdbc = workflow.f.jdbc;
    }

    public void verify() throws Exception {
        var original = get(true, 200, null, "original");
        var manifest = SubmissionJson.parse(original.body());
        assertThat(manifest.path("caseId").asText()).isEqualTo(workflow.f.kase.toString());
        assertThat(manifest.path("status").asText()).isEqualTo("ACTIVATED");
        assertThat(manifest.path("recoveryPackageSha256").asText())
            .isEqualTo(workflow.f.command.path("payloadSha256").asText());
        assertThat(SubmissionJson.sha(manifest.path("preparedTicket")))
            .isEqualTo(manifest.path("preparedTicketSha256").asText());
        assertThat(manifest.path("stopReceipt").path("status").asText()).isEqualTo("STOPPED");
        for (String kind : new String[] {"approvalReceipt", "preparationReceipt", "activationReceipt"})
            assertThat(manifest.path(kind).path("signedReceipt").asText()).isNotBlank();
        assertThat(manifest.path("activationReceipt").path("businessDecision").path("status").asText())
            .isEqualTo("ACTIVATED");
        assertThat(manifest.path("capabilities").path("localDeletionAllowed").asBoolean()).isFalse();
        assertThat(manifest.path("checklist").size()).isEqualTo(2);
        get(false, 401, null, "anonymous");
        changedTarget();
        changedTicket();
        changedActivation();
        assertThat(get(true, 200, null, "restored").body()).isEqualTo(original.body());
    }

    private void changedTarget() throws Exception {
        UUID other = UUID.randomUUID();
        var id = Ids.bytes(workflow.f.kase);
        var prior = jdbc.queryForObject("SELECT target_device_id FROM handover_case WHERE id=?", byte[].class, id);
        try {
            jdbc.update("UPDATE handover_case SET target_device_id=? WHERE id=?", Ids.bytes(other), id);
            get(true, 404, "HANDOVER_CASE_NOT_FOUND", "other-device");
        } finally { jdbc.update("UPDATE handover_case SET target_device_id=? WHERE id=?", prior, id); }
    }

    private void changedTicket() throws Exception {
        var id = Ids.bytes(workflow.f.kase);
        var prior = jdbc.queryForObject("SELECT prepared_ticket_sha256 FROM handover_case WHERE id=?", byte[].class, id);
        try {
            jdbc.update("UPDATE handover_case SET prepared_ticket_sha256=UNHEX(?) WHERE id=?", "f".repeat(64), id);
            get(true, 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE", "changed-ticket");
        } finally { jdbc.update("UPDATE handover_case SET prepared_ticket_sha256=? WHERE id=?", prior, id); }
    }

    private void changedActivation() throws Exception {
        var id = Ids.bytes(workflow.f.kase);
        var prior = jdbc.queryForObject("SELECT activation_sha256 FROM handover_case WHERE id=?", byte[].class, id);
        try {
            jdbc.update("UPDATE handover_case SET activation_sha256=UNHEX(?) WHERE id=?", "f".repeat(64), id);
            get(true, 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE", "changed-activation");
        } finally { jdbc.update("UPDATE handover_case SET activation_sha256=? WHERE id=?", prior, id); }
    }

    private HttpResponse<byte[]> get(boolean bearer, int status, String code, String name) throws Exception {
        var path = "/api/v1/pda/handovers/" + workflow.f.kase + "/manifest";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434" + path))
            .timeout(java.time.Duration.ofSeconds(30));
        if (bearer) request.header("Authorization", "Bearer "
            + workflow.f.r.f.tokens.path("accessToken").asText());
        var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence.resolve(name + ".json"), response.body());
        assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(status);
        if (code != null) assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(code);
        if (status == 200) {
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        }
        return response;
    }
}
