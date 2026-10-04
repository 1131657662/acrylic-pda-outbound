package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.pda.PdaAccess;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HandoverPdaAssignmentServiceTest {
    private final UUID caseId = UUID.randomUUID(), orderId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID(), deviceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID(), warehouseId = UUID.randomUUID();
    private final HandoverPdaEvidenceService evidence = mock(HandoverPdaEvidenceService.class);
    private final AssignmentStore assignments = mock(AssignmentStore.class);
    private final PdaAccess access = mock(PdaAccess.class);
    private final HandoverPdaAssignmentService service = new HandoverPdaAssignmentService(evidence, assignments, access);
    private AssignmentStore.View target;

    @BeforeEach void arrange() {
        target = view(assignmentId.toString(), deviceId.toString());
        var manifest = SubmissionJson.object().put("status", "ACTIVATED");
        manifest.putObject("activationReceipt").putObject("businessDecision")
            .put("assignmentId", assignmentId.toString()).put("assignmentEpoch", "2")
            .put("expiresAt", target.expiresAt()).put("startOrderScanSeq", "8")
            .put("targetDeviceId", deviceId.toString()).put("targetUserId", userId.toString());
        manifest.putObject("stopReceipt").putObject("source")
            .put("assignmentId", UUID.randomUUID().toString()).put("orderId", orderId.toString());
        when(evidence.manifest(caseId)).thenReturn(manifest);
        when(access.require(anyString())).thenReturn(new PdaSessions.Principal(UUID.randomUUID(), userId,
            "receiver", deviceId, warehouseId, "1", "1"));
        when(assignments.get(assignmentId)).thenReturn(target);
        when(assignments.active(orderId)).thenReturn(target);
        when(assignments.credentialActive(assignmentId)).thenReturn(true);
    }

    @Test void returnsOnlyTheVerifiedTargetCredential() {
        assertThat(service.assignment(caseId)).isEqualTo(target);
        verify(access).require("pda:submit");
    }

    @Test void renewedCredentialRemainsReadableAfterOriginalReceiptExpiryChanges() {
        ((com.fasterxml.jackson.databind.node.ObjectNode) evidence.manifest(caseId)
            .path("activationReceipt").path("businessDecision")).put("expiresAt", "2026-10-05T00:00:00Z");
        assertThat(service.assignment(caseId)).isEqualTo(target);
    }

    @Test void preparedCaseCannotRevealTargetCredential() {
        ((com.fasterxml.jackson.databind.node.ObjectNode) evidence.manifest(caseId)).put("status", "TARGET_PREPARED");
        assertThatThrownBy(() -> service.assignment(caseId)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.status()).isEqualTo(404));
        verifyNoInteractions(assignments);
    }

    @Test void mismatchedTargetCredentialIsRejected() {
        when(assignments.get(assignmentId)).thenReturn(view(assignmentId.toString(), UUID.randomUUID().toString()));
        assertThatThrownBy(() -> service.assignment(caseId)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("HANDOVER_RECEIPT_INTEGRITY_FAILURE"));
    }

    @Test void inactiveCredentialCannotBeRecoveredFromOldReceipt() {
        when(assignments.credentialActive(assignmentId)).thenReturn(false);
        assertThatThrownBy(() -> service.assignment(caseId)).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("HANDOVER_ASSIGNMENT_INACTIVE"));
    }

    private AssignmentStore.View view(String id, String device) {
        return new AssignmentStore.View(id, orderId.toString(), 1, device, "PDA", userId.toString(),
            "receiver", "2", "1", UUID.randomUUID().toString(), "plan", "inventory", "8", "0",
            "ACTIVE", "handover", "2026-10-04T00:00:00Z", "2026-10-06T00:00:00Z", null,
            "signed-token", UUID.randomUUID().toString(), "0", "ORDER", "VEHICLE", "1");
    }
}
