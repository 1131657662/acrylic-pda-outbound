package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Supervisor custody attestation plus an immutable archive reference; unknown/offline operation is refused. */
record HandoverFaultInput(UUID device, Instant controlledAt, String deviceLocation, String goodsLocation,
        HandoverTemporaryArchive archive) {
    static HandoverFaultInput read(JsonNode command, Instant now) {
        try {
            fields(command, Set.of("commandVersion", "commandType", "commandId", "warehouseId", "caseId",
                "transportRecoveryEpoch", "expected", "sourceDeviceId", "physicalControl", "archivedEvidence"));
            var control = command.path("physicalControl");
            fields(control, Set.of("deviceState", "controlledAt", "deviceLocation", "goodsLocation", "unresolvedInputCount"));
            require("POWERED_OFF_IN_CUSTODY".equals(text(control, "deviceState"))
                && "0".equals(text(control, "unresolvedInputCount")), "HANDOVER_FAULT_NOT_CONTROLLED");
            var time = Instant.parse(text(control, "controlledAt"));
            require(!time.isAfter(now), "HANDOVER_FAULT_CUTOFF_INVALID");
            var reference = command.path("archivedEvidence");
            fields(reference, Set.of("sourceEvidenceSetId", "verificationId", "setSha256", "captureHighWatermark",
                "auditThroughSeq", "orderScanHighWatermark"));
            var archive = HandoverTemporaryArchive.read(reference);
            require(archive.payloadHash().matches("[a-f0-9]{64}") && archive.captureHigh() >= 0
                && archive.auditHigh() > 0 && archive.inputHigh() >= 0, "HANDOVER_FAULT_ARCHIVE_INVALID");
            return new HandoverFaultInput(uuid(command, "sourceDeviceId"), time,
                location(control, "deviceLocation"), location(control, "goodsLocation"), archive);
        } catch (BusinessException refusal) {
            if (refusal.code().startsWith("HANDOVER_FAULT_") || "INVALID_HANDOVER_FAULT".equals(refusal.code())) throw refusal;
            throw failure("INVALID_HANDOVER_FAULT");
        }
        catch (RuntimeException invalid) { throw failure("INVALID_HANDOVER_FAULT"); }
    }

    private static String location(JsonNode control, String field) {
        String value = text(control, field);
        require(value != null && !value.isBlank() && value.length() <= 200 && value.codePoints().noneMatch(Character::isISOControl),
            "INVALID_HANDOVER_FAULT");
        return value;
    }

    private static void fields(JsonNode value, Set<String> expected) {
        var actual = new HashSet<String>(); value.fieldNames().forEachRemaining(actual::add);
        require(value.isObject() && actual.equals(expected), "INVALID_HANDOVER_FAULT");
    }

    private static void require(boolean valid, String code) { if (!valid) throw failure(code); }
    private static BusinessException failure(String code) {
        return new BusinessException(409, code, "故障设备、实物控制或可信截点不完整，保留原资料并继续核对");
    }
}
