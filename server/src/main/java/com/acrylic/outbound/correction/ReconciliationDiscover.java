package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Discovers a missing posting from server-owned dispatch/result/ledger facts. */
final class ReconciliationDiscover implements CorrectionCommand.Operation {
    private final ReconciliationContext contexts;
    private final CorrectionStore store;
    private final ReconciliationRequestStore requests;
    ReconciliationDiscover(ReconciliationContext contexts, CorrectionStore store, ReconciliationRequestStore requests) {
        this.contexts = contexts; this.store = store; this.requests = requests;
    }
    @Override public String commandType() { return "RECONCILE_DISCOVER"; }
    @Override public void beforeLocks(CorrectionCommand.Input input) { contexts.beforeLocks(input); }
    @Override public JsonNode lockContext(CorrectionCommand.Input input, JsonNode anchor) {
        return contexts.read(input.uuid("warehouseId"), input.uuid("dispatchId"), null, true);
    }

    @Override public JsonNode execute(CorrectionCommand.Write write) {
        ReconciliationPolicy.permission(write.actor()); ReconciliationPolicy.missingPosting(write.anchor().path("facts"));
        requests.requireUndiscovered(write.command().uuid("dispatchId"));
        ReconciliationPolicy.text(write.command().body().path("reason"), 500);
        UUID id = Ids.next(); var facts = write.anchor().path("facts");
        store.insertReconciliation(id, UUID.fromString(write.actor().path("warehouseId").asText()), "MISSING_POSTING",
            UUID.fromString(facts.at("/dispatch/baleId").asText()), UUID.fromString(facts.at("/dispatch/dispatchId").asText()),
            facts.at("/dispatch/dispatchCycle").asInt(), facts.at("/result/currentResultVersion").asLong(), 0L,
            facts.at("/stock/stockVersion").asLong(), write.recordedAt());
        return SubmissionJson.object().put("caseId", id.toString()).put("status", "OPEN").put("kind", "MISSING_POSTING")
            .put("dispatchId", facts.at("/dispatch/dispatchId").asText()).put("factsSha256", write.anchor().path("factsSha256").asText())
            .put("availability", "UNCHANGED");
    }

    @Override public void afterReceipt(CorrectionCommand.Write write, JsonNode decision) { requests.discovered(write, decision); }
}
