package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.Duration;
import java.util.Set;

/** Eligibility for a proven missing debit; a case conclusion never supplies stock-release authority. */
final class ReconciliationPolicy {
    private ReconciliationPolicy() { }
    static void missingPosting(JsonNode facts) {
        var result = facts.path("result"); var dispatch = facts.path("dispatch"); var stock = facts.path("stock");
        var owners = facts.path("owners");
        if (!"COMPLETED".equals(result.path("orderStatus").asText()) || dispatch.path("count").asLong() != 1
            || dispatch.path("weightG").asLong() <= 0 || !facts.path("ledger").isArray() || !facts.path("ledger").isEmpty()
            || !"NOT_DEDUCTED".equals(stock.path("bookStatus").asText()) || owners.size() != 1)
            throw CorrectionCommand.ledgerConflict();
        var owner = owners.get(0);
        if (!"DISPATCHED".equals(owner.path("state").asText()) || !owner.path("orderId").equals(result.path("orderId"))
            || !owner.path("cycle").equals(dispatch.path("dispatchCycle"))
            || !stock.path("dispatchCycle").equals(dispatch.path("dispatchCycle"))
            || !stock.path("warehouseId").equals(result.path("warehouseId"))) throw CorrectionCommand.ledgerConflict();
    }

    static void permission(JsonNode actor) {
        for (var item : actor.at("/permissionSnapshot/effectivePermissions"))
            if ("inventory:reconcile".equals(item.asText())) return;
        throw CorrectionCommand.denied();
    }

    static void evidence(JsonNode evidence, Instant now) {
        physicalEvidence(evidence, now, "DISPATCHED_CONFIRMED");
    }

    static void returnedEvidence(JsonNode evidence, Instant now) {
        physicalEvidence(evidence, now, "RETURNED_IN_WAREHOUSE");
    }

    static void physicalEvidence(JsonNode evidence, Instant now, String observation) {
        var names = new java.util.HashSet<String>(); evidence.fieldNames().forEachRemaining(names::add);
        if (!evidence.isObject() || !names.equals(Set.of("observedAt", "place", "observation", "reference", "attachments"))
            || !observation.equals(evidence.path("observation").asText())) throw CorrectionCommand.evidenceRequired();
        text(evidence.path("place"), 200); text(evidence.path("reference"), 500);
        Instant observed;
        try { observed = Instant.parse(evidence.path("observedAt").asText()); }
        catch (RuntimeException failure) { throw CorrectionCommand.evidenceRequired(); }
        if (observed.isAfter(now) || observed.isBefore(now.minus(Duration.ofDays(3)))) throw CorrectionCommand.windowExceeded();
    }

    static void text(JsonNode value, int max) {
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max)
            throw new BusinessException(400, "INVALID_RECONCILIATION_EVIDENCE", "请填写有界、可核对的差异凭据");
    }
}
