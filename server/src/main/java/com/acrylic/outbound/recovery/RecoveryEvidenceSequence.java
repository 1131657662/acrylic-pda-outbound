package com.acrylic.outbound.recovery;

import com.acrylic.outbound.audit.PdaAuditFacts;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Distinguishes full device E2A coverage from the earlier frozen source audit boundary. */
final class RecoveryEvidenceSequence {
    private RecoveryEvidenceSequence() { }

    static void verify(RecoveryEvidenceRecords.Records records, JsonNode stop, RecoverySourceIdentity expected) {
        require(stop.path("captureHighWatermark").longValue() == expected.captureHighWatermark()
            && stop.path("auditThroughSeq").longValue() == expected.auditThroughSeq(), "RECOVERY_E2A_STOP_WATERMARK");
        require(stop.path("stopTransactionVersion").longValue() == 1, "RECOVERY_E2A_STOP_VERSION");
        require(sequence(records.captures(), "deviceCaptureSeq") == expected.captureHighWatermark()
            && sequence(records.captures(), "orderScanSeq") == expected.orderScanHighWatermark()
            && sequence(records.audits(), "localSeq") == expected.auditThroughSeq(), "RECOVERY_E2A_ITEM_WATERMARK");
        var frozen = records.frozen(); RecoverySourceAuthority.requirePayload(frozen, expected);
        for (String field : List.of("auditEvents", "auditCoverage", "auditArchives", "temporaryContinuation", "temporaryHandover"))
            require(!frozen.has(field), "RECOVERY_E2A_PREFIX_UNSUPPORTED");
        require(uuid(frozen, "submissionId").equals(expected.submissionId())
            && uuid(frozen.path("source"), "creatorDeviceId").equals(expected.creatorDeviceId())
            && uuid(frozen.path("source"), "clientOrderId").equals(expected.clientOrderId()), "RECOVERY_E2A_FROZEN_SOURCE");
        records(frozen.path("captures"), records.captures()); records(frozen.path("scans"), records.scans());
        require(records.audits().size() >= 2, "RECOVERY_E2A_STOP_EVENT");
        var prefix = records.audits().subList(0, records.audits().size() - 1);
        records(frozen.path("events"), prefix);
        require(prefix.getLast().path("type").asText().equals("FROZEN")
            && prefix.getLast().path("captureId").isNull()
            && prefix.getLast().path("detail").asText().equals(expected.submissionId().toString()), "RECOVERY_E2A_FREEZE_EVENT");
        RecoveryInitialRecords.verify(frozen, withAudit(expected, expected.auditThroughSeq() - 1), prefix);
        scans(records, expected);
        for (var audit : records.audits()) audit(audit, expected);
        stop(records, stop, expected);
    }

    private static void scans(RecoveryEvidenceRecords.Records records, RecoverySourceIdentity expected) {
        var captures = new HashMap<UUID, JsonNode>(); var saved = new HashSet<UUID>(); long previous = 0;
        for (var capture : records.captures()) {
            require(uuid(capture, "originDeviceId").equals(expected.deviceId())
                && uuid(capture, "operatorId").equals(expected.operatorId())
                && uuid(capture, "authorizationId").equals(expected.qualificationId())
                && uuid(capture, "clientOrderId").equals(expected.clientOrderId())
                && text(capture, "sourceRecoveryEpoch").equals(expected.sourceRecoveryEpoch()), "RECOVERY_E2A_CAPTURE_SOURCE");
            captures.put(uuid(capture, "id"), capture);
        }
        for (var scan : records.scans()) {
            UUID id = uuid(scan, "captureId"); var capture = captures.get(id); long order = revision(scan, "orderScanSeq");
            require(saved.add(id) && capture != null && "COMMITTED".equals(text(capture, "state"))
                && order > previous && order == revision(capture, "orderScanSeq")
                && text(scan, "barcode").equals(text(capture, "barcode"))
                && revision(scan, "expectedDispatchCycle") == revision(capture, "expectedDispatchCycle"), "RECOVERY_E2A_SCAN_CAPTURE");
            previous = order;
        }
        for (var capture : records.captures())
            require("COMMITTED".equals(text(capture, "state")) == saved.contains(uuid(capture, "id")), "RECOVERY_E2A_SCAN_CAPTURE");
    }

    private static void stop(RecoveryEvidenceRecords.Records records, JsonNode stop, RecoverySourceIdentity expected) {
        var event = records.audits().getLast();
        require(text(event, "type").equals("STOP") && event.path("captureId").isNull()
            && uuid(event, "id").toString().equals(text(stop, "stopEventId"))
            && text(event, "detail").equals(expected.submissionId().toString()), "RECOVERY_E2A_STOP_EVENT");
        var frozenItem = records.items().stream().filter(item -> item.itemKind().equals("STOP")).findFirst().orElseThrow();
        require(frozenItem.originalDeviceSeq() == expected.auditThroughSeq(), "RECOVERY_E2A_FROZEN_WATERMARK");
    }

    private static void audit(JsonNode value, RecoverySourceIdentity expected) {
        PdaAuditFacts.validate(value);
        require(uuid(value, "originDeviceId").equals(expected.deviceId()) && uuid(value, "operatorId").equals(expected.operatorId())
            && uuid(value, "authorizationId").equals(expected.qualificationId())
            && uuid(value.path("source"), "clientOrderId").equals(expected.clientOrderId()), "RECOVERY_E2A_AUDIT_SOURCE");
    }

    private static long sequence(List<JsonNode> values, String field) {
        long expected = 1;
        for (var value : values) require(revision(value, field) == expected++, "RECOVERY_E2A_ITEM_SEQUENCE");
        return expected - 1;
    }

    private static void records(JsonNode expected, List<JsonNode> actual) {
        require(expected.isArray() && expected.size() == actual.size(), "RECOVERY_E2A_FROZEN_RECORDS");
        for (int index = 0; index < actual.size(); index++)
            require(expected.get(index).equals(actual.get(index)), "RECOVERY_E2A_FROZEN_RECORDS");
    }

    private static RecoverySourceIdentity withAudit(RecoverySourceIdentity source, long high) {
        return new RecoverySourceIdentity(source.sourceKind(), source.warehouseId(), source.scopeId(), source.submissionId(),
            source.deviceId(), source.operatorId(), source.qualificationId(), source.canonicalOrderId(), source.creatorDeviceId(),
            source.clientOrderId(), source.sourceRecoveryEpoch(), source.workRound(), source.captureHighWatermark(), high,
            source.orderScanHighWatermark());
    }
}
