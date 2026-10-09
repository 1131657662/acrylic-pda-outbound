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

    RecoveryAuthorityFixture(String kind) throws Exception {
        source = RecoverySourceFixture.initial(kind);
        key = new RecoveryReceiptFixture(); claims = claims(); publish();
    }

    private ObjectNode claims() {
        var id = source.identity;
        var value = SubmissionJson.object().put("iss", "acrylic-outbound")
            .put("iat", Instant.parse("2026-09-20T00:00:00Z").getEpochSecond())
            .put("exp", Instant.parse("2026-09-21T00:00:00Z").getEpochSecond());
        if (id.sourceKind().equals("FORMAL")) {
            return value.put("aud", "acrylic-pda-offline").put("scope", "pda:scan").put("jti", UUID.randomUUID().toString())
                .put("assignmentId", id.qualificationId().toString()).put("orderId", id.canonicalOrderId().toString())
                .put("sub", id.operatorId().toString()).put("deviceId", id.deviceId().toString())
                .put("warehouseId", id.warehouseId().toString()).put("serverRecoveryEpoch", id.sourceRecoveryEpoch());
        }
        value.put("aud", "acrylic-pda-temporary-work").put("purpose", "TEMP_WORK")
            .put("scope", "temporary_order:create pda:scan pda:submit").put("jti", id.qualificationId().toString())
            .put("manifestSha256", "a".repeat(64));
        value.putObject("identity").put("authorizationId", id.qualificationId().toString()).put("bundleId", UUID.randomUUID().toString())
            .put("userId", id.operatorId().toString()).put("deviceId", id.deviceId().toString())
            .put("warehouseId", id.warehouseId().toString()).put("serverRecoveryEpoch", id.sourceRecoveryEpoch());
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
        key.object(source, "payload", RecoveryReceiptFixture.bytes(payload));
        source.publish();
    }

    RecoverySourceAuthority reader() {
        return new RecoverySourceAuthority(new RecoveryReceiptKeys(List.of(key.pair.getPublic().getEncoded())));
    }
}
