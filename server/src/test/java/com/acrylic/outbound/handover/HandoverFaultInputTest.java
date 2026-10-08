package com.acrylic.outbound.handover;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class HandoverFaultInputTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test void controlledDeviceHasAnArchiveReferenceWithoutADeviceCommand() {
        var command = command();
        var parsed = HandoverFaultInput.read(command, NOW);
        assertThat(parsed.device()).isEqualTo(uuid(command, "sourceDeviceId"));
        assertThat(parsed.archive().inputHigh()).isEqualTo(7);
    }

    @ParameterizedTest @ValueSource(strings={"MISSING", "ONLINE", "UNKNOWN", "DEVICE_ACKED", "LOST"})
    void uncontrolledDeviceCannotBecomeAFaultFence(String state) {
        var command = command(); ((ObjectNode) command.path("physicalControl")).put("deviceState", state);
        assertThatThrownBy(() -> HandoverFaultInput.read(command, NOW)).hasMessageContaining("故障设备");
    }

    @Test void futureCustodyCannotProveACutoff() {
        var command = command(); ((ObjectNode) command.path("physicalControl")).put("controlledAt", NOW.plusSeconds(1).toString());
        assertThatThrownBy(() -> HandoverFaultInput.read(command, NOW)).hasMessageContaining("故障设备");
    }

    @Test void unresolvedInputCannotBeIgnored() {
        var command = command(); ((ObjectNode) command.path("physicalControl")).put("unresolvedInputCount", "1");
        assertThatThrownBy(() -> HandoverFaultInput.read(command, NOW)).hasMessageContaining("故障设备");
    }

    @Test void callerCannotSmuggleADeviceAcknowledgement() {
        var command = command().put("stopState", "DEVICE_ACKED");
        assertThatThrownBy(() -> HandoverFaultInput.read(command, NOW)).hasMessageContaining("故障设备");
    }

    @Test void missingSourceKeyCannotBeReplacedByATotalCount() {
        var command = command(); ((ObjectNode) command.path("archivedEvidence")).remove("verificationId");
        assertThatThrownBy(() -> HandoverFaultInput.read(command, NOW)).hasMessageContaining("故障设备");
    }

    @Test void negativeHighWatermarkCannotBeApproved() {
        var command = command(); ((ObjectNode) command.path("archivedEvidence")).put("orderScanHighWatermark", "-1");
        assertThatThrownBy(() -> HandoverFaultInput.read(command, NOW)).hasMessageContaining("故障设备");
    }

    private ObjectNode command() {
        var command = object().put("commandVersion", 1).put("commandType", "FAULT_FENCE")
            .put("commandId", id()).put("warehouseId", id()).put("caseId", id()).put("transportRecoveryEpoch", "1")
            .put("sourceDeviceId", id()); command.set("expected", object());
        command.putObject("physicalControl").put("deviceState", "POWERED_OFF_IN_CUSTODY").put("controlledAt", NOW.toString())
            .put("deviceLocation", "隔离保管柜").put("goodsLocation", "交接货区").put("unresolvedInputCount", "0");
        command.putObject("archivedEvidence").put("sourceEvidenceSetId", id()).put("verificationId", id()).put("setSha256", "a".repeat(64))
            .put("captureHighWatermark", "8").put("auditThroughSeq", "16").put("orderScanHighWatermark", "7");
        return command;
    }

    private String id() { return UUID.randomUUID().toString(); }
}
