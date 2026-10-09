package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;

/** Synthetic historical authorization, independent of today's wall clock or a private-key file. */
final class RecoveryAuthorityFixture {
    final RecoverySourceFixture source;
    final RecoveryReceiptFixture key;
    final ObjectNode claims;
    String closedAt = "2026-09-20T01:00:00Z";
    String confirmedAt = "2026-09-20T00:30:00Z";
    String savedAt = "2026-09-20T00:31:00Z";

    RecoveryAuthorityFixture(String kind) throws Exception {
        this(RecoverySourceFixture.initial(kind));
    }

    RecoveryAuthorityFixture(RecoverySourceFixture source) throws Exception {
        this.source = source;
        key = new RecoveryReceiptFixture(); claims = claims(); publish();
    }

    private ObjectNode claims() {
        var id = source.identity;
        var value = SubmissionJson.object().put("iss", "acrylic-outbound")
            .put("iat", Instant.parse("2026-09-20T00:00:00Z").getEpochSecond())
            .put("exp", Instant.parse("2026-09-21T00:00:00Z").getEpochSecond());
        if (id.sourceKind().equals("FORMAL")) {
            return value.put("aud", "acrylic-pda-offline").put("scope", "pda:scan").put("jti", UUID.randomUUID().toString())
                .put("assignmentEpoch", "1").put("revision", 1).put("inventorySnapshotId", UUID.randomUUID().toString())
                .put("assignmentId", id.qualificationId().toString()).put("orderId", id.canonicalOrderId().toString())
                .put("sub", id.operatorId().toString()).put("deviceId", id.deviceId().toString())
                .put("warehouseId", id.warehouseId().toString()).put("serverRecoveryEpoch", id.sourceRecoveryEpoch());
        }
        value.put("aud", "acrylic-pda-temporary-work").put("purpose", "TEMP_WORK")
            .put("scope", "temporary_order:create pda:scan pda:submit").put("jti", id.qualificationId().toString())
            .put("manifestSha256", "a".repeat(64));
        value.putObject("identity").put("authorizationId", id.qualificationId().toString()).put("bundleId", UUID.randomUUID().toString())
            .put("userId", id.operatorId().toString()).put("deviceId", id.deviceId().toString())
            .put("warehouseId", id.warehouseId().toString()).put("authorizationEpoch", "1")
            .put("serverRecoveryEpoch", id.sourceRecoveryEpoch());
        return value;
    }

    void publish() throws Exception {
        var header = SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", key.kid);
        key.object(source, "origin", RecoveryReceiptFixture.bytes(SubmissionJson.object().put("offlineToken", key.token(header, claims))));
        var id = source.identity;
        var payload = SubmissionJson.object().put("closedAt", closedAt).put("freezeDeviceId", id.deviceId().toString())
            .put("workRound", Long.toString(id.workRound())).put("sourceRecoveryEpoch", id.sourceRecoveryEpoch())
            .put(id.sourceKind().equals("FORMAL") ? "assignmentId" : "authorizationId", id.qualificationId().toString());
        payload.putObject("source").put("kind", id.sourceKind());
        records(payload);
        key.object(source, "payload", RecoveryReceiptFixture.bytes(payload)); source.publish();
    }

    private void records(ObjectNode payload) {
        var id = source.identity;
        UUID captureId = UUID.randomUUID();
        var capture = payload.putArray("captures").addObject().put("id", captureId.toString()).put("state", "COMMITTED")
            .put("authorizationId", claims.path("jti").asText()).put("originDeviceId", id.deviceId().toString())
            .put("operatorId", id.operatorId().toString()).put("sourceRecoveryEpoch", id.sourceRecoveryEpoch())
            .put("orderScanSeq", "1").put("barcode", "01234567890").put("confirmedAt", confirmedAt);
        if (id.sourceKind().equals("FORMAL")) capture.put("assignmentId", id.qualificationId().toString())
            .put("orderId", id.canonicalOrderId().toString()).put("warehouseId", id.warehouseId().toString())
            .put("assignmentEpoch", claims.path("assignmentEpoch").asText()).put("orderRevision", claims.path("revision").intValue())
            .put("snapshotId", claims.path("inventorySnapshotId").asText());
        else capture.put("clientOrderId", id.clientOrderId().toString())
            .put("authorizationEpoch", claims.at("/identity/authorizationEpoch").asText());
        payload.putArray("scans").addObject().put("captureId", captureId.toString()).put("orderScanSeq", "1")
            .put("barcode", "01234567890").put("savedAt", savedAt);
    }

    RecoverySourceAuthority reader() {
        return new RecoverySourceAuthority(new RecoveryReceiptKeys(List.of(key.pair.getPublic().getEncoded())));
    }
}
