package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Generated source publication/claim/recall/upload use MockMvc; abort and supervisor fault commands use real HTTP. */
final class HandoverFormalFaultSource {
    final HandoverApprovalWorkflowChecks workflow;
    final HandoverStopFixture source;
    final ObjectNode command;
    final byte[] receipt;
    final HandoverFaultFacts facts;

    HandoverFormalFaultSource(WebApplicationContext app, Path attempt) throws Exception {
        workflow = new HandoverApprovalWorkflowChecks(app, attempt, true);
        source = workflow.f;
        facts = new HandoverFaultFacts(source.jdbc, source.directory.resolve("formal-fault-facts"));
        abort(); command = command();
        new HandoverFormalFaultRefusals(this).beforeFence();
        receipt = "true".equals(System.getenv("HANDOVER_FAULT_BROWSER_VERIFY"))
            ? HandoverFormalFaultBrowserChecks.execute(this) : HandoverFormalFaultConcurrentChecks.execute(this);
        Files.write(source.directory.resolve("formal-fault-receipt.json"), receipt, StandardOpenOption.CREATE_NEW);
        Files.writeString(source.directory.resolve("formal-fault-command.json"), SubmissionJson.canonical(command), StandardOpenOption.CREATE_NEW);
        var before = facts.snapshot("replay-before");
        assertThat(workflow.web.command(command, 200, null)).isEqualTo(receipt);
        assertThat(facts.snapshot("replay-after")).isEqualTo(before);
        new HandoverFormalFaultRefusals(this).afterFence();
        unchangedOriginal();
    }

    private void abort() throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/submissions/" + source.r.f.id() + "/abort"))
            .header("Authorization", "Bearer " + source.r.f.tokens.path("accessToken").asText())
            .header("X-Server-Recovery-Epoch", "1").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(source.r.f.abortBody())));
        var response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        Files.write(source.directory.resolve("formal-fault-abort.json"), response.body(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(200);
        assertThat(SubmissionJson.parse(response.body()).path("data").path("status").asText()).isEqualTo("ABORTED");
    }

    private ObjectNode command() throws Exception {
        var context = SubmissionJson.parse(workflow.web.get(contextPath("FAULT_FENCE")));
        assertThat(context.path("fault").path("entryAvailable").asBoolean()).isTrue();
        var result = SubmissionJson.object().put("commandVersion", 1).put("commandType", "FAULT_FENCE")
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", source.r.f.web.identity().warehouse().toString())
            .put("caseId", source.kase.toString()).put("transportRecoveryEpoch", "1")
            .put("sourceDeviceId", source.r.f.tokens.path("deviceId").asText());
        result.set("expected", context.path("expected"));
        result.putObject("physicalControl").put("deviceState", "POWERED_OFF_IN_CUSTODY").put("controlledAt", Instant.now().toString())
            .put("deviceLocation", "开发隔离保管柜").put("goodsLocation", "开发隔离货位").put("unresolvedInputCount", "0");
        result.putObject("archivedEvidence").put("sourceKind", "FORMAL")
            .put("changeRequestId", source.r.body.path("changeRequestId").asText())
            .put("recoveryPackageId", source.r.body.path("recoveryPackageId").asText())
            .put("payloadSha256", SubmissionJson.sha(source.r.body))
            .put("captureHighWatermark", source.r.body.path("captureHighWatermark").asText())
            .put("auditThroughSeq", source.r.body.at("/auditCoverage/0/throughSeq").asText())
            .put("orderScanHighWatermark", source.r.body.path("captureHighWatermark").asText());
        return result;
    }

    String contextPath(String kind) {
        return "/handovers/" + source.kase + "/context?warehouseId=" + source.r.f.web.identity().warehouse() + "&commandType=" + kind;
    }

    void unchangedOriginal() {
        var original = source.jdbc.queryForMap("SELECT status,stop_receipt,resolution_receipt FROM order_change_request WHERE id=UUID_TO_BIN(?)",
            source.r.body.path("changeRequestId").asText());
        assertThat(original).containsEntry("status", "REQUESTED").containsEntry("stop_receipt", null).containsEntry("resolution_receipt", null);
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM handover_evidence WHERE case_id=UUID_TO_BIN(?)", Integer.class,
            source.kase.toString())).isZero();
    }
}
