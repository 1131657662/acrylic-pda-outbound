package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionControl;
import com.acrylic.outbound.submissions.SubmissionEvents;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemporaryRejectionOperationTest {
    @Test
    void recordsRejectionAndLeavesReservationForLaterDisposition() {
        UUID submission = UUID.randomUUID();
        UUID original = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        UUID authorization = UUID.randomUUID();
        UUID clientOrder = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        var control = new SubmissionControl(submission, null, null, device, 1, "1", null,
            new SubmissionControl.Temporary(device, clientOrder, authorization));
        var head = new SubmissionStore.Head(control, original, warehouse, "REVIEW_REQUIRED", null, null, order);
        var body = SubmissionJson.object().put("shortShipmentReason", "");
        var line = body.putObject("temporaryOrder").putArray("lines").addObject();
        line.put("deliveryCode", "D-1").put("productId", UUID.randomUUID().toString()).put("model", "M")
            .put("name", "N").put("plannedCount", "1");
        body.putArray("scans").add(SubmissionJson.object().put("status", "ACTIVE")
            .put("deliveryCode", "D-1").put("weightGrams", "10"));
        var candidate = SubmissionJson.object().put("policyVersion", "REVIEW_DEFAULT_V1")
            .put("fromDate", "2026-01-01").put("throughDate", "2026-01-02").put("total", "0")
            .put("fingerprint", "a".repeat(64)).put("contextSha256", "b".repeat(64));

        var source = mock(TemporaryConfirmationSource.class);
        when(source.read(any())).thenReturn(new TemporaryConfirmationSource.Value(head, body, null, false));
        var candidates = mock(TemporaryDecisionCandidates.class);
        when(candidates.snapshot(eq(warehouse), eq(submission), any())).thenReturn(candidate);
        var submissions = mock(SubmissionStore.class);
        var events = mock(SubmissionEvents.class);
        var operation = new TemporaryRejectionOperation(
            new TemporaryRejectionOperation.Dependencies(source, candidates, submissions, events));

        var context = context(submission, original, warehouse, device, authorization, clientOrder, order);
        var command = command(context, reviewer, "主管复核后拒绝");
        var decision = operation.execute(new CommandWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            Instant.parse("2026-10-02T00:00:00Z"), CommandInput.parse(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8)), context, actor(reviewer)));

        assertThat(decision.path("kind").asText()).isEqualTo("REJECT");
        verify(submissions).state(submission, "REJECTED", decisionIssues("主管复核后拒绝"), null);
        verify(events).transition(head, "REVIEW_REQUIRED", reviewer);
    }

    private static CommandContext context(UUID submission, UUID original, UUID warehouse, UUID device,
            UUID authorization, UUID clientOrder, UUID order) {
        var value = SubmissionJson.object().put("scopeId", UUID.randomUUID().toString())
            .put("warehouseId", warehouse.toString()).put("creatorDeviceId", device.toString())
            .put("clientOrderId", clientOrder.toString()).put("submissionId", submission.toString())
            .put("workRound", "1").put("status", "REVIEW_REQUIRED").put("authorizationId", authorization.toString())
            .put("authorizationEpoch", "1").put("authorizationUserVersion", "1").put("authorizationDeviceVersion", "1")
            .put("freezeDeviceId", device.toString()).put("originalOperatorId", original.toString())
            .put("sourceRecoveryEpoch", "1").put("canonicalOrderId", order.toString()).put("mappingId", UUID.randomUUID().toString())
            .put("mappingVersion", "1").put("mappingOrderId", order.toString()).put("currentSubmissionId", submission.toString())
            .put("scopeRound", "1").put("decisionVersion", "0").put("slotVersion", "1")
            .put("slotScopeId", UUID.randomUUID().toString()).put("slotSubmissionId", submission.toString())
            .put("slotRound", "1").put("targetRevision", "1").put("receivedPayloadSha256", "c".repeat(64))
            .put("declaredUploadSha256", "d".repeat(64)).put("planSha256", "e".repeat(64));
        return new CommandContext(value);
    }

    private static ObjectNode command(CommandContext context, UUID actor, String reason) {
        var command = SubmissionJson.object().put("commandVersion", 1).put("commandType", "REJECT")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1").put("reason", reason)
            .put("warehouseId", context.text("warehouseId")).put("submissionId", context.text("submissionId"))
            .put("workRound", "1").put("authorizationId", context.text("authorizationId"))
            .put("freezeDeviceId", context.text("freezeDeviceId")).put("sourceRecoveryEpoch", "1");
        command.putObject("source").put("kind", "TEMP").put("creatorDeviceId", context.text("creatorDeviceId"))
            .put("clientOrderId", context.text("clientOrderId"));
        var expected = CommandExpected.current(context, "1").put("candidateContextSha256", "b".repeat(64));
        command.set("expected", expected);
        return command;
    }

    private static ObjectNode actor(UUID principal) {
        var actor = SubmissionJson.object().put("principalId", principal.toString()).put("userAuthVersion", "1")
            .putNull("deviceId").putNull("sessionId").putNull("deviceVersion");
        actor.putObject("permissionSnapshot").putArray("requiredPermissions").add("submission:review");
        actor.with("permissionSnapshot").putArray("effectivePermissions").add("submission:review");
        return actor;
    }

    private static JsonNode decisionIssues(String reason) {
        var issues = SubmissionJson.object().putArray("issues");
        return issues.add(SubmissionJson.object().put("code", "SUPERVISOR_REJECTED").put("reason", reason));
    }
}
