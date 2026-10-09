package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class HandoverFormalFaultInputTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test void formalReferencePreservesOriginalIdsAndIndependentHighWatermarks() {
        var command = command();
        var input = HandoverFaultInput.read(command, NOW);
        assertThat(input.archive()).isNull();
        assertThat(input.formalArchive().recall()).isEqualTo(uuid(command.path("archivedEvidence"), "changeRequestId"));
        assertThat(input.formalArchive().captureHigh()).isEqualTo(8);
        assertThat(input.inputHigh()).isEqualTo(7);
        assertThat(input.formalArchive().auditHigh()).isEqualTo(16);
    }

    @ParameterizedTest @ValueSource(strings={"TEMP", "UNKNOWN", "formal", ""})
    void formalFieldsCannotBorrowAnotherSourceKind(String source) {
        var command = command(); ((ObjectNode) command.path("archivedEvidence")).put("sourceKind", source);
        assertThatThrownBy(() -> HandoverFaultInput.read(command, NOW)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest @ValueSource(strings={"changeRequestId", "recoveryPackageId", "payloadSha256", "orderScanHighWatermark"})
    void missingOriginalReferenceCannotBeApproved(String field) {
        var command = command(); ((ObjectNode) command.path("archivedEvidence")).remove(field);
        assertThatThrownBy(() -> HandoverFaultInput.read(command, NOW)).isInstanceOf(BusinessException.class);
    }

    @Test void temporaryVerificationCannotBeSmuggledIntoFormalReference() {
        var command = command(); ((ObjectNode) command.path("archivedEvidence")).put("verificationId", id());
        assertThatThrownBy(() -> HandoverFaultInput.read(command, NOW)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest @ValueSource(strings={"0", "-1", "01", "18446744073709551616"})
    void invalidAuditCutoffIsRejected(String high) {
        var command = command(); ((ObjectNode) command.path("archivedEvidence")).put("auditThroughSeq", high);
        assertThatThrownBy(() -> HandoverFaultInput.read(command, NOW)).isInstanceOf(BusinessException.class);
    }

    @Test void formalDescriptorDoesNotClaimAnOriginalDeviceAcknowledgement() {
        var reference = HandoverFaultInput.read(command(), NOW).formalArchive();
        var origin = new HandoverStopStore.Origin(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "1", 1);
        var descriptor = reference.descriptor(origin);
        assertThat(text(descriptor, "sourceKind")).isEqualTo("FORMAL");
        assertThat(uuid(descriptor, "assignmentId")).isEqualTo(origin.assignment());
        assertThat(descriptor.has("authorizationId")).isFalse();
        assertThat(descriptor.path("mayResumeScanning").asBoolean()).isFalse();
    }

    private ObjectNode command() {
        var command = object().put("commandVersion", 1).put("commandType", "FAULT_FENCE")
            .put("commandId", id()).put("warehouseId", id()).put("caseId", id()).put("transportRecoveryEpoch", "1")
            .put("sourceDeviceId", id()); command.set("expected", object());
        command.putObject("physicalControl").put("deviceState", "POWERED_OFF_IN_CUSTODY").put("controlledAt", NOW.toString())
            .put("deviceLocation", "隔离保管柜").put("goodsLocation", "交接货区").put("unresolvedInputCount", "0");
        command.putObject("archivedEvidence").put("sourceKind", "FORMAL").put("changeRequestId", id()).put("recoveryPackageId", id())
            .put("payloadSha256", "a".repeat(64)).put("captureHighWatermark", "8").put("auditThroughSeq", "16").put("orderScanHighWatermark", "7");
        return command;
    }

    private String id() { return UUID.randomUUID().toString(); }
}
