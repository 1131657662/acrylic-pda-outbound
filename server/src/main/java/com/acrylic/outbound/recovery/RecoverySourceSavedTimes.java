package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Checks each original save against its original credential; closing/uploading later does not renew it. */
final class RecoverySourceSavedTimes {
    private RecoverySourceSavedTimes() { }

    static void verify(JsonNode body, Map<UUID, Credential> credentials) {
        Instant closed = Instant.parse(text(body, "closedAt"));
        var captures = new HashMap<UUID, JsonNode>(); var origins = new HashMap<UUID, Credential>();
        for (var capture : SubmissionJson.array(body, "captures")) {
            UUID id = uuid(capture, "id");
            require(captures.put(id, capture) == null, "RECOVERY_SOURCE_CAPTURE_DUPLICATE");
            var credential = credentials.get(uuid(capture, "authorizationId"));
            require(credential != null, "RECOVERY_SOURCE_CREDENTIAL_MISSING");
            origin(capture, credential.source());
            RecoverySourceCaptureCredential.verify(capture, credential.claims(), credential.source()); origins.put(id, credential);
            require(Set.of("COMMITTED", "INVALID", "BUSINESS_REJECTED", "DISCARDED").contains(text(capture, "state")),
                "RECOVERY_SOURCE_CAPTURE_STATE");
        }
        var saved = new HashSet<UUID>();
        for (var scan : SubmissionJson.array(body, "scans")) {
            UUID id = uuid(scan, "captureId"); var capture = captures.get(id);
            require(saved.add(id) && capture != null && "COMMITTED".equals(text(capture, "state")),
                "RECOVERY_SOURCE_SAVE_BINDING");
            save(scan, capture, closed, origins.get(id));
        }
        captures.forEach((id, capture) -> require("COMMITTED".equals(text(capture, "state")) == saved.contains(id),
            "RECOVERY_SOURCE_SAVE_MISSING"));
    }

    private static void origin(JsonNode capture, RecoverySourceIdentity source) {
        boolean temporary = source.sourceKind().equals("TEMP");
        require(capture.has("clientOrderId") == temporary && capture.has("assignmentId") != temporary,
            "RECOVERY_SOURCE_CAPTURE_ORIGIN");
        require(uuid(capture, "originDeviceId").equals(source.deviceId())
            && uuid(capture, "operatorId").equals(source.operatorId())
            && text(capture, "sourceRecoveryEpoch").equals(source.sourceRecoveryEpoch()), "RECOVERY_SOURCE_CAPTURE_ORIGIN");
        if (temporary) require(uuid(capture, "clientOrderId").equals(source.clientOrderId())
            && uuid(capture, "authorizationId").equals(source.qualificationId()), "RECOVERY_SOURCE_CAPTURE_ORIGIN");
        else require(uuid(capture, "assignmentId").equals(source.qualificationId())
            && uuid(capture, "warehouseId").equals(source.warehouseId())
            && uuid(capture, "orderId").equals(source.canonicalOrderId()), "RECOVERY_SOURCE_CAPTURE_ORIGIN");
    }

    private static void save(JsonNode scan, JsonNode capture, Instant closed, Credential credential) {
        var limits = window(credential.claims());
        Instant confirmed = Instant.parse(text(capture, "confirmedAt")), saved = Instant.parse(text(scan, "savedAt"));
        require(!confirmed.isBefore(limits.issued()) && !saved.isBefore(confirmed) && saved.isBefore(limits.expires())
            && !saved.isAfter(closed), "RECOVERY_SOURCE_ORIGIN_WINDOW");
        require(revision(scan, "orderScanSeq") == revision(capture, "orderScanSeq")
            && text(scan, "barcode").equals(text(capture, "barcode")), "RECOVERY_SOURCE_SAVE_BINDING");
    }

    static Window window(JsonNode claims) {
        require(claims.path("iat").isIntegralNumber() && claims.path("iat").canConvertToLong()
            && claims.path("exp").isIntegralNumber() && claims.path("exp").canConvertToLong(), "RECOVERY_SOURCE_ORIGIN_WINDOW");
        long issued = claims.path("iat").longValue(), expires = claims.path("exp").longValue();
        require(issued >= 0 && expires > issued, "RECOVERY_SOURCE_ORIGIN_WINDOW");
        return new Window(Instant.ofEpochSecond(issued), Instant.ofEpochSecond(expires));
    }

    record Window(Instant issued, Instant expires) { }
    record Credential(RecoverySourceIdentity source, JsonNode claims) { }
}
