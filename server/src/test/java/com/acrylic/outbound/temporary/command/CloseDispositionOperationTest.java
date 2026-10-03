package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CloseDispositionOperationTest {
    @Test void recordsTheCommandReasonInTheSignedDecision() {
        UUID bale = UUID.randomUUID();
        var verification = mock(PhysicalDispositionVerification.class);
        var store = mock(PhysicalDispositionStore.class);
        var write = closeWrite(bale, "supervisor confirmed closure");
        var verified = new PhysicalDispositionVerification.Verified(write.context().id("warehouseId"), write.context().id("scopeId"),
            write.context().id("submissionId"), 1, UUID.fromString(write.command().expected().path("evidenceSetId").asText()),
            UUID.fromString(write.command().expected().path("verificationId").asText()), "a".repeat(64), 1, 1, List.of(bale));
        when(verification.readForClose(any(), any(), any(), anyLong(), any(), any(), anyString(), anyLong(), anyLong())).thenReturn(verified);
        UUID disposition = UUID.randomUUID();
        when(store.apply(any(), anyString(), anyLong(), anyList(), any(), eq(true), anyString())).thenReturn(disposition);

        var decision = new CloseDispositionOperation(verification, store).execute(write);

        assertThat(decision.path("closeReason").asText()).isEqualTo("supervisor confirmed closure");
        ObjectNode receipt = SubmissionJson.object().put("status", "RECORDED").put("commandType", "CLOSE_DISPOSITION");
        receipt.set("businessDecision", decision);
        CommandDecision.validate(receipt);
    }

    private static CommandWrite closeWrite(UUID bale, String reason) {
        CommandWrite base = CommandReceiptFixture.write();
        ObjectNode body = (ObjectNode) base.command().body();
        body.put("commandType", "CLOSE_DISPOSITION").put("reason", reason);
        var expected = (ObjectNode) body.path("expected");
        expected.put("evidenceSetId", UUID.randomUUID().toString()).put("evidenceSetSha256", "a".repeat(64))
            .put("verificationId", UUID.randomUUID().toString()).put("verificationVersion", "1")
            .put("verificationAlgorithmVersion", "1");
        ObjectNode manifest = body.putObject("dispositionManifest").put("version", 1);
        manifest.putArray("items").addObject().put("baleId", bale.toString()).put("disposition", "RELEASE");
        expected.put("dispositionManifestSha256", SubmissionJson.sha(manifest)).put("dispositionManifestVersion", "1");
        var command = CommandInput.parse(SubmissionJson.canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new CommandWrite(base.requestId(), base.receiptId(), base.eventId(), base.recordedAt(), command, base.context(), base.actor());
    }
}
