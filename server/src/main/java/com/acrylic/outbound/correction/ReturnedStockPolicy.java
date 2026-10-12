package com.acrylic.outbound.correction;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/** Restoring a reversed debit grants neither quarantine clearance nor a new dispatch cycle. */
final class ReturnedStockPolicy {
    private ReturnedStockPolicy() { }
    static void eligible(JsonNode facts) {
        var dispatch = facts.path("dispatch"); var stock = facts.path("stock"); var proof = facts.path("reopening");
        if (!"COMPLETED".equals(facts.at("/result/orderStatus").asText()) || dispatch.path("count").asLong()!=0
            || dispatch.path("weightG").asLong()!=0 || !"EFFECTIVE".equals(proof.path("documentStatus").asText())
            || !Set.of("PHYSICAL_RETURN", "DISPATCH_CANCELLATION").contains(proof.path("type").asText())
            || !stock.path("dispatchCycle").equals(proof.path("cycleAfter"))
            || !dispatch.path("dispatchCycle").equals(proof.path("cycleBefore"))
            || !facts.path("owners").isArray() || !facts.path("owners").isEmpty()
            || !"DEDUCTED".equals(stock.path("bookStatus").asText()) || !stock.path("upstreamPresent").asBoolean()
            || !stock.path("snapshotId").equals(stock.path("activeSnapshotId"))
            || !stock.path("lastSeenSnapshotId").equals(stock.path("activeSnapshotId"))
            || !stock.path("blockedReason").isNull()) throw CorrectionCommand.ledgerConflict();
        var entries = new ArrayList<CorrectionContribution.Entry>(); UUID bale = uuid(dispatch.path("baleId"));
        for (var row : facts.path("ledger")) entries.add(new CorrectionContribution.Entry(uuid(row.path("id")),
            uuid(row.path("baleId")), uuid(row.path("lineId")), row.path("kind").asText(), row.path("quantityDelta").asLong(),
            row.path("weightDeltaG").asLong(), uuid(row.path("reversesEntryId")), uuid(row.path("reversesInventoryEntryId")),
            uuid(row.path("restoresEntryId")), "ORIGINAL".equals(row.path("kind").asText())));
        if (entries.isEmpty() || !CorrectionContribution.active(entries, bale, new long[]{0,0}).isEmpty())
            throw CorrectionCommand.ledgerConflict();
    }
    private static UUID uuid(JsonNode value) { return value.isMissingNode() || value.isNull() ? null : UUID.fromString(value.asText()); }
}
