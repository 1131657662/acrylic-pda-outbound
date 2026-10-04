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

/** Real HTTP recovery of the signed handover's target assignment. */
public final class HandoverPdaAssignmentChecks {
    private final HandoverApprovalWorkflowChecks workflow;
    private final JdbcTemplate jdbc;
    private final Path evidence;
    private final HttpClient http = HttpClient.newHttpClient();

    public HandoverPdaAssignmentChecks(HandoverApprovalWorkflowChecks workflow, Path attempt) throws Exception {
        this.workflow = workflow;
        jdbc = workflow.f.jdbc;
        evidence = Files.createDirectory(attempt.resolve("receiving-assignment"));
    }

    public void verify() throws Exception {
        var manifest = SubmissionJson.parse(get(true, 200, null, "assigned").body());
        var assignment = manifest.path("data");
        var receipt = SubmissionJson.parse(Files.readAllBytes(workflow.f.directory.getParent()
            .resolve("receiving-manifest/original.json"))).path("activationReceipt").path("businessDecision");
        assertThat(assignment.path("id").asText()).isEqualTo(receipt.path("assignmentId").asText());
        assertThat(assignment.path("deviceId").asText()).isEqualTo(receipt.path("targetDeviceId").asText());
        assertThat(assignment.path("operatorId").asText()).isEqualTo(receipt.path("targetUserId").asText());
        assertThat(assignment.path("startOrderScanSeq").asText()).isEqualTo(receipt.path("startOrderScanSeq").asText());
        assertThat(assignment.path("offlineToken").asText()).isNotBlank();
        get(false, 401, null, "anonymous");
        wrongTarget();
        changedSequence(assignment.path("id").asText());
        assertThat(SubmissionJson.parse(get(true, 200, null, "restored").body()).path("data"))
            .isEqualTo(assignment);
    }

    private void wrongTarget() throws Exception {
        var id = Ids.bytes(workflow.f.kase);
        var prior = jdbc.queryForObject("SELECT target_device_id FROM handover_case WHERE id=?", byte[].class, id);
        try {
            jdbc.update("UPDATE handover_case SET target_device_id=? WHERE id=?", Ids.bytes(UUID.randomUUID()), id);
            get(true, 404, "HANDOVER_CASE_NOT_FOUND", "wrong-target");
        } finally { jdbc.update("UPDATE handover_case SET target_device_id=? WHERE id=?", prior, id); }
    }

    private void changedSequence(String assignment) throws Exception {
        var id = Ids.bytes(UUID.fromString(assignment));
        var prior = jdbc.queryForObject("SELECT start_order_scan_seq FROM order_assignment WHERE id=?", Long.class, id);
        try {
            jdbc.update("UPDATE order_assignment SET start_order_scan_seq=? WHERE id=?", prior + 1, id);
            get(true, 409, "HANDOVER_RECEIPT_INTEGRITY_FAILURE", "changed-sequence");
        } finally { jdbc.update("UPDATE order_assignment SET start_order_scan_seq=? WHERE id=?", prior, id); }
    }

    private HttpResponse<byte[]> get(boolean bearer, int status, String code, String name) throws Exception {
        var path = "/api/v1/pda/handovers/" + workflow.f.kase + "/assignment";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434" + path))
            .timeout(java.time.Duration.ofSeconds(30));
        if (bearer) request.header("Authorization", "Bearer " + workflow.f.r.f.tokens.path("accessToken").asText());
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
