package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.sha256;

/** Synthetic pinned archive state and in-memory development signatures; no original fact claim. */
final class RecoverySourceStateFixture {
    final RecoveryAuthorityFixture authority;
    final ObjectNode payload;
    final ObjectNode occupancy;
    final ObjectNode permission;
    String status = "ABORTED";
    boolean local;

    RecoverySourceStateFixture(String kind) throws Exception {
        authority = new RecoveryAuthorityFixture(kind);
        var versions = kind.equals("TEMP") ? (ObjectNode)authority.claims.path("identity") : authority.claims;
        versions.put("userAuthVersion", "3").put("deviceVersion", "7"); authority.publish();
        payload = (ObjectNode)SubmissionJson.parse(authority.source.read().original("payload"));
        var capture = (ObjectNode)payload.path("captures").get(0); capture.put("expectedDispatchCycle", "2");
        var scan = (ObjectNode)payload.path("scans").get(0);
        scan.put("status", "ACTIVE").put("baleId", UUID.randomUUID().toString()).put("expectedDispatchCycle", "2");
        occupancy = root().putNull("sourceEvidenceSetId");
        occupancy.putArray("rows").add(row(scan));
        permission = root(); permission.putArray("windows").add(window()); publish();
    }

    private ObjectNode root() {
        var value = SubmissionJson.object().put("formatVersion", 1);
        value.set("source", authority.source.manifest.path("source").deepCopy()); return value;
    }

    private ObjectNode row(ObjectNode scan) {
        var id = authority.source.identity;
        var row = SubmissionJson.object().put("ownerState", "REVIEW_RESERVED").put("ownerSubmissionId", id.submissionId().toString())
            .putNull("sourceEvidenceSetId").putNull("continuationId").putNull("recoveryPackageId").putNull("dispositionId")
            .putNull("dispositionEvidenceId").putNull("pcWorkId").putNull("pcDispositionId")
            .putNull("temporaryContinuationId").putNull("temporaryHandoverActivationId")
            .put("upstreamPresent", true).put("bookStatus", "NOT_DEDUCTED").putNull("blockedReason");
        for (String field : List.of("baleId", "barcode", "captureId", "orderScanSeq")) row.set(field, scan.path(field).deepCopy());
        row.set("dispatchCycle", scan.path("expectedDispatchCycle").deepCopy());
        nullable(row, "ownerOrderId", id.canonicalOrderId());
        nullable(row, "ownerScopeId", id.sourceKind().equals("TEMP") ? id.scopeId() : null);
        nullable(row, "ownerWarehouseId", id.sourceKind().equals("TEMP") ? id.warehouseId() : null);
        return row;
    }

    ObjectNode window() {
        var id = authority.source.identity;
        var value = SubmissionJson.object().put("from", "2026-09-20T00:00:00Z").put("through", "2026-09-21T00:00:00Z")
            .put("userId", id.operatorId().toString()).put("deviceId", id.deviceId().toString()).put("warehouseId", id.warehouseId().toString())
            .put("userAuthVersion", "3").put("deviceVersion", "7").put("userActive", true).put("deviceStatus", "ACTIVE")
            .put("deviceTemporaryOrderEnabled", true);
        var permissions = value.putArray("permissions");
        for (String scope : List.of("pda:login", "pda:scan", "pda:submit", "temporary_order:create")) permissions.add(scope);
        value.putArray("warehouseIds").add(id.warehouseId().toString()); return value;
    }

    void sourceReserved() {
        UUID evidence = UUID.randomUUID(); occupancy.put("sourceEvidenceSetId", evidence.toString());
        row().put("ownerState", "SOURCE_RESERVED").putNull("ownerSubmissionId").putNull("ownerOrderId")
            .put("sourceEvidenceSetId", evidence.toString());
    }

    void secondScan() {
        var capture = ((ObjectNode)payload.path("captures").get(0)).deepCopy().put("id", UUID.randomUUID().toString())
            .put("orderScanSeq", "2").put("barcode", "01234567891");
        var scan = ((ObjectNode)payload.path("scans").get(0)).deepCopy().put("captureId", capture.path("id").asText())
            .put("orderScanSeq", "2").put("barcode", "01234567891").put("baleId", UUID.randomUUID().toString());
        ((com.fasterxml.jackson.databind.node.ArrayNode)payload.path("captures")).add(capture);
        ((com.fasterxml.jackson.databind.node.ArrayNode)payload.path("scans")).add(scan);
        ((com.fasterxml.jackson.databind.node.ArrayNode)occupancy.path("rows")).add(row(scan));
    }

    void publish() throws Exception {
        String payloadHash = SubmissionJson.sha(payload);
        occupancy.put("payloadSha256", payloadHash); permission.put("payloadSha256", payloadHash);
        permission.put("credentialSha256", sha256(authority.source.read().original("origin")));
        var receipt = authority.key.receipt(authority.source, status).put("payloadSha256", payloadHash);
        if (local) receipt.putNull("payloadSha256").put("payloadPresent", false).put("localFrozenPayloadSha256", payloadHash);
        object("payload", payload); object("occupancy", occupancy); object("permission", permission);
        object("result", authority.key.signed(receipt)); authority.source.publish();
    }

    void object(String kind, ObjectNode value) throws Exception {
        authority.key.object(authority.source, kind, RecoveryReceiptFixture.bytes(value)); authority.source.publish();
    }

    RecoveryPinnedSource read() { return authority.source.read(); }
    RecoverySourceOccupancy occupancyReader() { return new RecoverySourceOccupancy(keys()); }
    RecoverySourcePermission permissionReader() {
        return new RecoverySourcePermission(keys());
    }
    private RecoveryReceiptKeys keys() { return new RecoveryReceiptKeys(List.of(authority.key.pair.getPublic().getEncoded())); }
    ObjectNode row() { return (ObjectNode)occupancy.path("rows").get(0); }
    ObjectNode firstWindow() { return (ObjectNode)permission.path("windows").get(0); }
    private void nullable(ObjectNode value, String field, UUID id) { if (id == null) value.putNull(field); else value.put(field, id.toString()); }
}
