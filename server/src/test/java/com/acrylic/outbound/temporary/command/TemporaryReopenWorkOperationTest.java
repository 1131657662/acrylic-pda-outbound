package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemporaryReopenWorkOperationTest {
    @Test void issuesAFlatRetainedProductDecision() {
        UUID bale = UUID.randomUUID(), ticket = UUID.randomUUID();
        var verification = mock(PhysicalDispositionVerification.class);
        var tickets = mock(TemporaryContinuationTicketStore.class);
        var operation = new TemporaryReopenWorkOperation(verification, tickets);
        var base = CommandReceiptFixture.write();
        var body = reopenBody(base, bale);
        String manifestHash = body.path("expected").path("retainedManifestSha256").asText();
        var command = CommandInput.parse(SubmissionJson.canonical(body).getBytes(StandardCharsets.UTF_8));
        var contextJson = (ObjectNode) base.context().json();
        contextJson.put("status", "ABORTED");
        var context = new CommandContext(contextJson);
        var write = new CommandWrite(base.requestId(), base.receiptId(), base.eventId(), Instant.parse("2026-09-21T00:00:00.123Z"),
            command, context, base.actor());
        var proof = new PhysicalDispositionVerification.Verified(context.id("warehouseId"), context.id("scopeId"),
            context.id("submissionId"), 1, UUID.fromString(body.path("expected").path("evidenceSetId").asText()),
            UUID.fromString(body.path("expected").path("verificationId").asText()), "ABORTED", "a".repeat(64), 1, 1, List.of(bale));
        var item = new TemporaryRetainedManifest.Item(bale, 7, "b".repeat(64));
        when(verification.readForContinuation(any(), any(), any(), eq(1L), any(), any(), anyString(), eq(1L), eq(1L))).thenReturn(proof);
        when(tickets.issue(any(), eq(proof), anyList(), eq(manifestHash), eq(1L), eq(3L), eq(write.recordedAt())))
            .thenReturn(new TemporaryContinuationTicketStore.Ticket(ticket, 1, 2, List.of(item)));

        var decision = operation.execute(write);

        assertThat(decision.path("products")).hasSize(1);
        assertThat(decision.path("products").get(0).path("baleId").asText()).isEqualTo(bale.toString());
        assertThat(decision.path("products").get(0).path("disposition").asText()).isEqualTo("RETAIN");
        assertThat(decision.path("status").asText()).isEqualTo("TICKET_ISSUED");
    }

    private static ObjectNode reopenBody(CommandWrite base, UUID bale) {
        var body = (ObjectNode) base.command().body();
        body.put("commandType", "REOPEN_WORK").put("reason", "operator checked").put("nextOrderScanSeq", "3");
        var expected = (ObjectNode) body.path("expected");
        expected.put("evidenceSetId", UUID.randomUUID().toString()).put("evidenceSetSha256", "a".repeat(64))
            .put("verificationId", UUID.randomUUID().toString()).put("verificationVersion", "1")
            .put("verificationAlgorithmVersion", "1").put("retainedManifestVersion", "1");
        var manifest = SubmissionJson.object().put("version", 1); manifest.putArray("items").addObject()
            .put("baleId", bale.toString()).put("expectedDispatchCycle", "7").put("recordSha256", "b".repeat(64));
        body.set("retainedManifest", manifest);
        expected.put("retainedManifestSha256", SubmissionJson.sha(manifest));
        return body;
    }
}
