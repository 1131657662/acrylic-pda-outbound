package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
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

/** Exercises original-byte delivery through the named receiving PDA session. */
public final class HandoverPdaPackageChecks {
    private final HandoverApprovalWorkflowChecks workflow;
    private final JdbcTemplate jdbc;
    private final Path evidence;
    private final Path packageFile;
    private final HttpClient http = HttpClient.newHttpClient();

    public HandoverPdaPackageChecks(HandoverApprovalWorkflowChecks workflow, Path attempt) throws Exception {
        this.workflow = workflow;
        jdbc = workflow.f.jdbc;
        evidence = Files.createDirectory(attempt.resolve("receiving-package"));
        packageFile = attempt.resolve("storage/submissions/" + workflow.f.command.path("payloadSha256").asText() + ".json");
    }

    public void verify() throws Exception {
        var manifest = SubmissionJson.parse(request("manifest", true, 200, null, "manifest").body());
        var original = request("recovery-package", true, 200, null, "original");
        assertThat(original.body()).isEqualTo(Files.readAllBytes(packageFile));
        assertThat(ImportFiles.sha256(original.body())).isEqualTo(manifest.path("recoveryPackageSha256").asText());
        assertThat(SubmissionJson.parse(original.body()).path("recoveryPackageId").asText())
            .isEqualTo(workflow.f.command.path("recoveryPackageId").asText());
        assertThat(manifest.path("preparedTicket").path("targetDeviceId").asText())
            .isEqualTo(workflow.f.r.f.tokens.path("deviceId").asText());
        request("recovery-package", false, 401, null, "anonymous");
        wrongTarget();
        changedTicket();
        missingFile();
        corruptedFile();
        assertThat(request("recovery-package", true, 200, null, "restored").body()).isEqualTo(original.body());
    }

    private void wrongTarget() throws Exception {
        var id = Ids.bytes(workflow.f.kase);
        var previous = jdbc.queryForObject("SELECT target_device_id FROM handover_case WHERE id=?", byte[].class, id);
        try {
            jdbc.update("UPDATE handover_case SET target_device_id=? WHERE id=?", Ids.bytes(UUID.randomUUID()), id);
            request("recovery-package", true, 404, "HANDOVER_CASE_NOT_FOUND", "wrong-target");
        } finally { jdbc.update("UPDATE handover_case SET target_device_id=? WHERE id=?", previous, id); }
    }

    private void changedTicket() throws Exception {
        var id = Ids.bytes(workflow.f.kase);
        var previous = jdbc.queryForObject("SELECT prepared_ticket_sha256 FROM handover_case WHERE id=?", byte[].class, id);
        try {
            jdbc.update("UPDATE handover_case SET prepared_ticket_sha256=UNHEX(?) WHERE id=?", "f".repeat(64), id);
            request("recovery-package", true, 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE", "changed-ticket");
        } finally { jdbc.update("UPDATE handover_case SET prepared_ticket_sha256=? WHERE id=?", previous, id); }
    }

    private void missingFile() throws Exception {
        var retained = evidence.resolve("missing-original.retained");
        Files.move(packageFile, retained);
        try { request("recovery-package", true, 409, "HANDOVER_STOP_FILE_UNAVAILABLE", "missing-file"); }
        finally { Files.move(retained, packageFile); }
    }

    private void corruptedFile() throws Exception {
        var retained = evidence.resolve("corrupted-original.retained");
        Files.move(packageFile, retained);
        var corrupt = Files.readAllBytes(retained);
        corrupt[0] ^= 1;
        Files.write(packageFile, corrupt);
        try { request("recovery-package", true, 409, "HANDOVER_STOP_FILE_UNAVAILABLE", "corrupted-file"); }
        finally {
            Files.move(packageFile, evidence.resolve("corrupted-file.retained"));
            Files.move(retained, packageFile);
        }
    }

    private HttpResponse<byte[]> request(String suffix, boolean bearer, int status, String code, String name) throws Exception {
        var path = "/api/v1/pda/handovers/" + workflow.f.kase + "/" + suffix;
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434" + path))
            .timeout(java.time.Duration.ofSeconds(30));
        if (bearer) builder.header("Authorization", "Bearer " + workflow.f.r.f.tokens.path("accessToken").asText());
        var response = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
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
