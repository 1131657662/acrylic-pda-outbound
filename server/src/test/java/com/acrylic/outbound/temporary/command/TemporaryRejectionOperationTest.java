package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionControl;
import com.acrylic.outbound.submissions.SubmissionEvents;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.submissions.SubmissionSnapshot;
import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemporaryRejectionOperationTest {
    @Test
    void recordsRejectionAndLeavesReservationForLaterDisposition() {
        var scenario = scenario(null, "B-1");
        var decision = scenario.operation().execute(scenario.write());

        assertThat(decision.path("kind").asText()).isEqualTo("REJECT");
        verify(scenario.submissions()).state(scenario.submission(), "REJECTED", decisionIssues("主管复核后拒绝"), null);
        verify(scenario.events()).transition(scenario.head(), "REVIEW_REQUIRED", scenario.reviewer());
    }

    @Test
    void blocksRejectionWhenEveryActiveScanHasAStoredStockConflict() {
        String issues = stockIssues("B-1", "B-2");
        var scenario = scenario(issues, "B-1", "B-2");

        assertThatThrownBy(() -> scenario.operation().execute(scenario.write()))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("SUBMISSION_STILL_CONFLICTED"));
        verify(scenario.submissions(), never()).state(any(), any(), any(), any());
        verify(scenario.events(), never()).transition(any(), any(), any());
    }

    @Test
    void permitsRejectionWhenOnlySomeActiveScansHaveAStoredStockConflict() {
        var scenario = scenario(stockIssues("B-1"), "B-1", "B-2");

        var decision = scenario.operation().execute(scenario.write());

        assertThat(decision.path("kind").asText()).isEqualTo("REJECT");
        verify(scenario.submissions()).state(scenario.submission(), "REJECTED", decisionIssues("主管复核后拒绝"), null);
    }

    private static Scenario scenario(String storedIssues, String... barcodes) {
        var ids = new ScenarioIds(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        var control = new SubmissionControl(ids.submission(), null, null, ids.device(), 1, "1", null,
            new SubmissionControl.Temporary(ids.device(), ids.clientOrder(), ids.authorization()));
        var head = new SubmissionStore.Head(control, ids.original(), ids.warehouse(), "REVIEW_REQUIRED", null, storedIssues, ids.order());
        var evidence = evidence(barcodes);
        var candidate = SubmissionJson.object().put("policyVersion", "REVIEW_DEFAULT_V1")
            .put("fromDate", "2026-01-01").put("throughDate", "2026-01-02").put("total", "0")
            .put("fingerprint", "a".repeat(64)).put("contextSha256", "b".repeat(64));
        var source = mock(TemporaryConfirmationSource.class);
        when(source.read(any())).thenReturn(new TemporaryConfirmationSource.Value(head, evidence.body(), evidence.snapshot(), false));
        var candidates = mock(TemporaryDecisionCandidates.class);
        when(candidates.snapshot(eq(ids.warehouse()), eq(ids.submission()), any())).thenReturn(candidate);
        var submissions = mock(SubmissionStore.class);var events = mock(SubmissionEvents.class);
        var operation = new TemporaryRejectionOperation(
            new TemporaryRejectionOperation.Dependencies(source, candidates, submissions, events));
        var context = context(ids.submission(), ids.original(), ids.warehouse(), ids.device(), ids.authorization(),
            ids.clientOrder(), ids.order());
        var command = command(context, ids.reviewer(), "主管复核后拒绝");
        var write = new CommandWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-10-02T00:00:00Z"),
            CommandInput.parse(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8)), context, actor(ids.reviewer()));
        return new Scenario(operation, write, submissions, events, head, ids.submission(), ids.reviewer());
    }

    private static Evidence evidence(String... barcodes) {
        var body = SubmissionJson.object().put("shortShipmentReason", "");
        var line = body.putObject("temporaryOrder").putArray("lines").addObject();
        line.put("deliveryCode", "D-1").put("productId", UUID.randomUUID().toString()).put("model", "M")
            .put("name", "N").put("plannedCount", Integer.toString(barcodes.length));
        var scans = body.putArray("scans");var validated = new ArrayList<SubmissionSnapshot.Scan>();
        for (int i = 0; i < barcodes.length; i++) {
            String barcode = barcodes[i];var bale = new SubmissionSnapshot.Bale(UUID.randomUUID(), barcode, "D-1", 10, "10", "M", "N");
            scans.add(SubmissionJson.object().put("status", "ACTIVE").put("barcode", barcode)
                .put("deliveryCode", "D-1").put("weightGrams", "10").put("model", "M").put("name", "N"));
            validated.add(new SubmissionSnapshot.Scan(UUID.randomUUID(), i + 1L, 1, bale));
        }
        return new Evidence(body, new SubmissionSnapshot.Validated(validated, List.of(), 10L * barcodes.length, false));
    }

    private static String stockIssues(String... barcodes) {
        var issues = SubmissionJson.object().putArray("issues");
        for (String barcode : barcodes) issues.add(SubmissionJson.object()
            .put("code", "INVENTORY_UNAVAILABLE").put("barcode", barcode));
        return SubmissionJson.canonical(issues);
    }

    private record Scenario(TemporaryRejectionOperation operation, CommandWrite write, SubmissionStore submissions,
        SubmissionEvents events, SubmissionStore.Head head, UUID submission, UUID reviewer) { }
    private record Evidence(ObjectNode body, SubmissionSnapshot.Validated snapshot) { }
    private record ScenarioIds(UUID submission, UUID original, UUID reviewer, UUID warehouse, UUID device,
        UUID authorization, UUID clientOrder, UUID order) { }

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
