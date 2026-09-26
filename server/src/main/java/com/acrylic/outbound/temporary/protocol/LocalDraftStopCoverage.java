package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.temporary.protocol.ProtocolJson.require;

/** Full-device coverage is distinct from this stopped source. Other sources are not promoted to trusted business facts. */
final class LocalDraftStopCoverage {
    private final LocalDraftStopProtocol.VerifiedSignature signed;
    private final LocalDraftStopProtocol.Expected expected;
    private final JsonNode metadata;
    private final Map<Long, Scan> scans = new HashMap<>();
    private final Map<String, Scan> captureIds = new HashMap<>();
    private JsonNode lastCapture;
    private long captures, audits, ownCaptures, archives, stops;
    private int phase;
    private boolean needsScan;
    record Scan(String captureId, String operatorId, String sourceKey) { }

    LocalDraftStopCoverage(LocalDraftStopProtocol.VerifiedSignature signed, JsonNode metadata, LocalDraftStopProtocol.Expected expected) {
        this.signed = signed; this.metadata = metadata; this.expected = expected;
    }
    long ownCaptures() { return ownCaptures; }
    long archives() { return archives; }

    void append(LocalDraftStopSnapshotProtocol.Item item, JsonNode body) {
        require(body.isObject(), "DRAFT_RECORD_OBJECT");
        if (!"ARCHIVE".equals(item.kind())) {
            String id = "SCAN".equals(item.kind()) ? "captureId" : "id";
            require(SubmissionJson.uuid(body, id).equals(item.recordId()), "DRAFT_RECORD_ID");
            String seq = "CAPTURE".equals(item.kind()) ? "deviceCaptureSeq" : "localSeq";
            require(LocalDraftStopRaw.number(body, seq) == item.deviceSeq(), "DRAFT_RECORD_SEQUENCE");
            String status = switch (item.kind()) { case "CAPTURE" -> "state"; case "SCAN" -> "status"; default -> "type"; };
            LocalDraftStopClaims.equal(body, status, item.recordStatus());
        }
        switch (item.kind()) {
            case "CAPTURE" -> capture(item, body);
            case "SCAN" -> scan(item, body);
            case "AUDIT" -> audit(item, body);
            case "ARCHIVE" -> archive(item, body);
            default -> throw new IllegalArgumentException("DRAFT_RECORD_KIND");
        }
    }

    private void capture(LocalDraftStopSnapshotProtocol.Item item, JsonNode body) {
        require(phase == 0 && !needsScan && item.deviceSeq() == ++captures && captures <= signed.captureHighWatermark(), "DRAFT_CAPTURE_PREFIX");
        LocalDraftStopClaims.equal(body, "originDeviceId", expected.creatorDeviceId().toString());
        SubmissionJson.uuid(body, "operatorId");
        require(Set.of("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED", "INVALID", "BUSINESS_REJECTED", "DISCARDED", "COMMITTED")
            .contains(item.recordStatus()), "DRAFT_CAPTURE_STATE");
        String source = sourceKey(body);
        require(captureIds.put(item.recordId().toString(), new Scan(item.recordId().toString(), body.path("operatorId").textValue(), source)) == null, "DRAFT_CAPTURE_DUPLICATE");
        if (source.equals("TEMP/" + expected.clientOrderId())) {
            require(LocalDraftStopRaw.number(body, "orderScanSeq") == ++ownCaptures, "DRAFT_OWN_CAPTURE_PREFIX");
            LocalDraftStopClaims.equal(body, "operatorId", expected.originalOperatorId().toString());
            LocalDraftStopClaims.equal(body, "authorizationId", expected.authorizationId().toString());
            LocalDraftStopClaims.equal(body, "snapshotId", expected.snapshotId().toString());
            LocalDraftStopClaims.equal(body, "sourceRecoveryEpoch", Long.toString(expected.sourceRecoveryEpoch()));
        }
        lastCapture = body; needsScan = "COMMITTED".equals(item.recordStatus());
    }

    private void scan(LocalDraftStopSnapshotProtocol.Item item, JsonNode body) {
        require(phase == 0 && needsScan && lastCapture != null && item.recordId().toString().equals(lastCapture.path("id").textValue()), "DRAFT_SCAN_CAPTURE");
        require(item.deviceSeq() <= signed.auditThroughSeq() && Set.of("ACTIVE", "VOID").contains(item.recordStatus()), "DRAFT_SCAN_STATUS");
        require(LocalDraftStopRaw.number(body, "orderScanSeq") == LocalDraftStopRaw.number(lastCapture, "orderScanSeq"), "DRAFT_SCAN_ORDER");
        LocalDraftStopClaims.equal(body, "barcode", LocalDraftStopClaims.text(lastCapture, "barcode"));
        require(LocalDraftStopRaw.number(body, "expectedDispatchCycle") > 0 &&
            LocalDraftStopRaw.number(body, "expectedDispatchCycle") == LocalDraftStopRaw.number(lastCapture, "expectedDispatchCycle"), "DRAFT_SCAN_CYCLE");
        SubmissionJson.uuid(body, "baleId");
        require(scans.put(item.deviceSeq(), new Scan(item.recordId().toString(), lastCapture.path("operatorId").textValue(), sourceKey(lastCapture))) == null, "DRAFT_SCAN_EVENT_DUPLICATE");
        needsScan = false;
    }

    private void audit(LocalDraftStopSnapshotProtocol.Item item, JsonNode body) {
        require(phase <= 1 && !needsScan && captures == signed.captureHighWatermark(), "DRAFT_AUDIT_PHASE"); phase = 1;
        require(item.deviceSeq() == ++audits && audits <= signed.auditThroughSeq(), "DRAFT_AUDIT_PREFIX");
        LocalDraftStopClaims.equal(body, "originDeviceId", expected.creatorDeviceId().toString());
        SubmissionJson.uuid(body, "operatorId"); String source = sourceKey(body);
        if (!body.path("captureId").isNull()) {
            Scan capture = captureIds.get(SubmissionJson.uuid(body, "captureId").toString());
            require(capture != null, "DRAFT_AUDIT_CAPTURE");
            if (source.startsWith("TEMP/") || capture.sourceKey().startsWith("TEMP/")) {
                require(capture.sourceKey().equals(source), "DRAFT_AUDIT_CAPTURE_SOURCE");
                LocalDraftStopClaims.equal(body, "operatorId", capture.operatorId());
            }
            // FORMAL continuation may reference an older capture under a newer assignment/operator.
            // Its authority belongs to later trusted business-fact verification, not this raw boundary.
        }
        Scan scan = scans.remove(audits);
        if (scan != null) {
            require("SAVED".equals(item.recordStatus()) && source.equals(scan.sourceKey()), "DRAFT_SCAN_AUDIT");
            LocalDraftStopClaims.equal(body, "captureId", scan.captureId()); LocalDraftStopClaims.equal(body, "operatorId", scan.operatorId());
        } else require(!"SAVED".equals(item.recordStatus()), "DRAFT_MISSING_SCAN");
        if (source.equals("TEMP/" + expected.clientOrderId())) ownAudit(item, body);
    }

    private void ownAudit(LocalDraftStopSnapshotProtocol.Item item, JsonNode body) {
        LocalDraftStopClaims.equal(body, "operatorId", expected.originalOperatorId().toString());
        LocalDraftStopClaims.equal(body, "authorizationId", expected.authorizationId().toString());
        require(!Set.of("FROZEN", "STOP").contains(item.recordStatus()), "DRAFT_ALREADY_SUBMITTED");
        require(stops == 0, "DRAFT_EVENTS_AFTER_STOP");
        if ("LOCAL_EVIDENCE_STOP".equals(item.recordStatus())) {
            require(item.recordId().equals(signed.stopEventId()) && item.deviceSeq() == signed.auditThroughSeq()
                && body.path("captureId").isNull(), "DRAFT_STOP_EVENT");
            LocalDraftStopClaims.equal(body, "detail", expected.evidenceId().toString()); stops++;
        }
    }

    private void archive(LocalDraftStopSnapshotProtocol.Item item, JsonNode body) {
        require(!needsScan && audits == signed.auditThroughSeq() && "ARCHIVED".equals(item.recordStatus()) && ++archives <= 4096, "DRAFT_ARCHIVE_PHASE");
        phase = 2;
        LocalDraftStopClaims.fields(body, Set.of("originalProof", "originalSigningKey"));
        require(!LocalDraftStopClaims.text(body, "originalProof").isEmpty() && !LocalDraftStopClaims.text(body, "originalSigningKey").isEmpty(), "DRAFT_ARCHIVE_ORIGINALS");
        require(item.deviceSeq() <= signed.auditThroughSeq(), "DRAFT_ARCHIVE_BOUNDARY");
        var proof = LocalDraftStopRaw.parse(body.path("originalProof").textValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var key = LocalDraftStopRaw.parse(body.path("originalSigningKey").textValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        require(key.isObject(), "DRAFT_ARCHIVE_KEY");
        LocalDraftStopClaims.equal(proof, "originDeviceId", expected.creatorDeviceId().toString());
        LocalDraftStopClaims.equal(proof, "warehouseId", expected.warehouseId().toString());
        long epoch = LocalDraftStopRaw.number(proof, "recoveryEpoch"), after = LocalDraftStopRaw.number(proof, "afterSeq");
        long through = LocalDraftStopRaw.number(proof, "throughSeq");
        require(epoch > 0 && epoch <= expected.sourceRecoveryEpoch() && through == item.deviceSeq() && after < through && through - after <= 200, "DRAFT_ARCHIVE_RANGE");
        String identity = "acrylic-pda-audit-archive/" + expected.creatorDeviceId() + "/" + epoch + "/" + after;
        require(UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(item.recordId()), "DRAFT_ARCHIVE_ID");
        require(proof.path("localDeletionAllowed").isBoolean() && !proof.path("localDeletionAllowed").booleanValue(), "DRAFT_ARCHIVE_DELETION");
        require(LocalDraftStopClaims.text(proof, "payloadSha256").matches("[0-9a-f]{64}") && !LocalDraftStopClaims.text(proof, "signedArchive").isEmpty(), "DRAFT_ARCHIVE_PROOF");
        // Original archive signatures must be verified with trusted historical keys by the intake caller.
    }

    private String sourceKey(JsonNode body) {
        JsonNode source = body.path("source");
        if (source.isObject()) {
            require(!body.has("assignmentId") && !body.has("clientOrderId"), "DRAFT_MIXED_SOURCE");
            LocalDraftStopClaims.fields(source, Set.of("kind", "creatorDeviceId", "clientOrderId"));
            LocalDraftStopClaims.equal(source, "kind", "TEMP");
            LocalDraftStopClaims.equal(source, "creatorDeviceId", expected.creatorDeviceId().toString());
            return "TEMP/" + SubmissionJson.uuid(source, "clientOrderId");
        }
        boolean temp = body.has("clientOrderId");
        require(!body.has("source") && temp != body.has("assignmentId"), "DRAFT_SOURCE_KIND");
        return (temp ? "TEMP/" : "FORMAL/") + SubmissionJson.uuid(body, temp ? "clientOrderId" : "assignmentId");
    }

    void finish() {
        require(!needsScan && captures == signed.captureHighWatermark() && audits == signed.auditThroughSeq() && stops == 1 && scans.isEmpty(), "DRAFT_COVERAGE");
        require(ownCaptures == LocalDraftStopRaw.number(metadata.path("draft"), "nextScanSeq") - 1, "DRAFT_OWN_COUNT");
        if ("DRAFT".equals(metadata.path("draft").path("state").textValue())) require(ownCaptures == 0, "DRAFT_NOT_STARTED");
    }
}
