package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

final class ReturnedStockDiscover implements CorrectionCommand.Operation {
    private final ReconciliationContext contexts;
    private final CorrectionStore store;
    private final ReconciliationRequestStore requests;
    ReturnedStockDiscover(ReconciliationContext contexts, CorrectionStore store, ReconciliationRequestStore requests) {
        this.contexts = contexts; this.store = store; this.requests = requests;
    }
    @Override public String commandType() { return "RECONCILE_RETURN_DISCOVER"; }
    @Override public void beforeLocks(CorrectionCommand.Input input) { contexts.beforeLocks(input); }
    @Override public JsonNode lockContext(CorrectionCommand.Input input, JsonNode anchor) {
        return contexts.returned(input.uuid("warehouseId"), input.uuid("dispatchId"), null, true);
    }
    @Override public JsonNode execute(CorrectionCommand.Write write) {
        ReconciliationPolicy.permission(write.actor()); ReturnedStockPolicy.eligible(write.anchor().path("facts"));
        requests.requireUndiscovered(write.command().uuid("dispatchId"));
        UUID id = Ids.next(); var facts = write.anchor().path("facts");
        store.insertReconciliation(id, UUID.fromString(write.actor().path("warehouseId").asText()), "RETURNED_STOCK",
            UUID.fromString(facts.at("/dispatch/baleId").asText()), write.command().uuid("dispatchId"),
            facts.at("/stock/dispatchCycle").asInt(), facts.at("/result/currentResultVersion").asLong(), null,
            facts.at("/stock/stockVersion").asLong(), write.recordedAt());
        return SubmissionJson.object().put("caseId", id.toString()).put("status", "OPEN").put("kind", "RETURNED_STOCK")
            .put("dispatchId", write.command().text("dispatchId")).put("factsSha256", write.anchor().path("factsSha256").asText())
            .put("availability", "UNCHANGED");
    }
    @Override public void afterReceipt(CorrectionCommand.Write write, JsonNode decision) { requests.discovered(write, decision); }
}
