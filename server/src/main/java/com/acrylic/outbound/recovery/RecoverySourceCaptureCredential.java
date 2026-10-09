package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Binds retained capture metadata to its signed historical credential, including a FORMAL renewal's jti. */
final class RecoverySourceCaptureCredential {
    private RecoverySourceCaptureCredential() { }

    static void verify(JsonNode capture, JsonNode claims, RecoverySourceIdentity source) {
        require(uuid(capture, "authorizationId").equals(uuid(claims, "jti")), "RECOVERY_SOURCE_CAPTURE_CREDENTIAL");
        if (source.sourceKind().equals("TEMP")) {
            require(text(capture, "authorizationEpoch").equals(text(claims.path("identity"), "authorizationEpoch")),
                "RECOVERY_SOURCE_CAPTURE_CREDENTIAL");
            return;
        }
        require(text(capture, "assignmentEpoch").equals(text(claims, "assignmentEpoch"))
            && uuid(capture, "snapshotId").equals(uuid(claims, "inventorySnapshotId"))
            && capture.path("orderRevision").isInt() && claims.path("revision").isInt()
            && capture.path("orderRevision").intValue() > 0
            && capture.path("orderRevision").equals(claims.path("revision")), "RECOVERY_SOURCE_CAPTURE_CREDENTIAL");
    }
}
