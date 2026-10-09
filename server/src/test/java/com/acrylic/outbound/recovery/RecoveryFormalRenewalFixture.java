package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.*;
import java.time.Instant;
import java.util.*;

/** Synthetic same-assignment renewal with a fixed original preparation and nonempty cumulative scans. */
final class RecoveryFormalRenewalFixture {
    final RecoveryPreparationFixture first = new RecoveryPreparationFixture("FORMAL");
    final RecoveryAuthorityFixture renewed;
    final ObjectNode origin;
    final ObjectNode payload;

    RecoveryFormalRenewalFixture() throws Exception {
        first.state.authority.claims.put("policyVersion", "1").put("autoDeductInventory", false)
            .put("startOrderScanSeq", "1").put("allocationVersion", "0");
        first.state.secondScan(); material(); first.repack(); first.publish();
        renewed = new RecoveryAuthorityFixture(RecoverySourceFixture.credentialCopy(first.state.authority.source));
        renewed.claims.removeAll(); renewed.claims.setAll(first.state.authority.claims.deepCopy());
        renewed.claims.put("jti", UUID.randomUUID().toString()).put("authorizationSequence", "1")
            .put("renewedFromAuthorizationId", first.state.authority.claims.path("jti").asText())
            .put("iat", Instant.parse("2026-09-21T00:00:00Z").getEpochSecond())
            .put("exp", Instant.parse("2026-09-23T00:00:00Z").getEpochSecond());
        origin = first.origin.deepCopy(); payload = first.state.payload.deepCopy().put("closedAt", "2026-10-09T00:00:00Z")
            .put("expectedCount", "2").put("expectedWeightGrams", "2468");
        ((ObjectNode)payload.at("/captures/1")).put("authorizationId", renewed.claims.path("jti").asText())
            .put("confirmedAt", "2026-09-21T00:30:00Z");
        ((ObjectNode)payload.at("/scans/1")).put("savedAt", "2026-09-21T00:31:00Z"); publish();
    }

    private void material() {
        var scan = first.state.payload.at("/scans/1"); var row = ((ArrayNode)first.content.get("inventory")).get(0).deepCopy();
        ((ObjectNode)row).put("barcode", scan.path("barcode").asText()); ((ArrayNode)first.content.get("inventory")).add(row);
        ((ArrayNode)first.content.get("availability")).addObject().put("barcode", scan.path("barcode").asText())
            .put("baleId", scan.path("baleId").asText()).put("dispatchCycle", "2").put("available", true);
        ((ObjectNode)first.content.get("plan").at("/order/lines/0")).put("plannedCount", 2);
    }

    void publish() throws Exception {
        var header = SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", renewed.key.kid);
        origin.put("offlineToken", renewed.key.token(header, renewed.claims));
        renewed.key.object(renewed.source, "origin", RecoveryReceiptFixture.bytes(origin));
        renewed.key.object(renewed.source, "payload", RecoveryReceiptFixture.bytes(payload)); renewed.source.publish();
    }

    Map<UUID, RecoveryPinnedSource> catalog() {
        return Map.of(UUID.fromString(first.state.authority.claims.path("jti").asText()), first.read(),
            UUID.fromString(renewed.claims.path("jti").asText()), renewed.source.read());
    }

    RecoverySourceFormalRenewals reader() {
        return new RecoverySourceFormalRenewals(new RecoveryReceiptKeys(List.of(first.state.authority.key.pair.getPublic().getEncoded(),
            renewed.key.pair.getPublic().getEncoded())));
    }
}
