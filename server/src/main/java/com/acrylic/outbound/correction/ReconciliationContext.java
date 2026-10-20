package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fixed fact digest shared by the product preparation query and locked business command. */
@Component
final class ReconciliationContext {
    private final ReconciliationFacts facts;
    private final ReconciliationRequestStore requests;
    private final CorrectionStore store;
    private final JdbcTemplate jdbc;
    private final com.acrylic.outbound.common.IdentityWriteGuard guard;
    private final ReturnedStockFacts returned;
    ReconciliationContext(ReconciliationFacts facts, ReconciliationRequestStore requests, CorrectionStore store, JdbcTemplate jdbc,
        com.acrylic.outbound.common.IdentityWriteGuard guard, ReturnedStockFacts returned) {
        this.facts = facts; this.requests = requests; this.store = store; this.jdbc = jdbc; this.guard = guard;
        this.returned = returned;
    }

    void beforeLocks(CorrectionCommand.Input input) {
        ReconciliationInput.validate(input.body()); guard.lock(input.text("transportRecoveryEpoch"));
    }

    ObjectNode read(UUID warehouse, UUID dispatch, UUID caseId, boolean lock) {
        JsonNode caseFacts = caseId == null ? null : store.reconciliationFacts(caseId);
        if (caseFacts != null) {
            if (!warehouse.toString().equals(caseFacts.path("warehouseId").asText())
                || !Set.of("MISSING_POSTING", "RETURNED_STOCK").contains(caseFacts.path("kind").asText())) throw CorrectionCommand.missing();
            dispatch = UUID.fromString(caseFacts.path("dispatchId").asText());
        }
        if (dispatch == null) throw CorrectionCommand.missing();
        UUID order = facts.order(dispatch);
        if (!warehouse.toString().equals(store.orderFacts(order).path("warehouseId").asText())) throw CorrectionCommand.missing();
        if (lock) {
            jdbc.queryForList("SELECT id FROM warehouse WHERE id=? FOR UPDATE", byte[].class, Ids.bytes(warehouse));
            jdbc.queryForList("SELECT id FROM outbound_order WHERE id=? FOR UPDATE", byte[].class, Ids.bytes(order)); facts.lock(dispatch);
        }
        var current = facts.read(dispatch); var result = SubmissionJson.object();
        result.put("warehouseId", warehouse.toString()).put("orderId", order.toString()).put("dispatchId", dispatch.toString());
        result.put("factsSha256", ReconciliationFacts.hash(current)); result.set("facts", current);
        if (caseFacts != null) attachCase(result, caseFacts, caseId);
        return result;
    }

    private void attachCase(ObjectNode result, JsonNode caseFacts, UUID id) {
        if (!Set.of("MISSING_POSTING", "RETURNED_STOCK").contains(caseFacts.path("kind").asText())) throw CorrectionCommand.missing();
        result.set("case", caseFacts);
        var opened = requests.original(id, false); result.set("discoveredReceipt", opened);
        if (!result.path("orderId").equals(opened.path("orderId"))
            || !result.path("dispatchId").equals(opened.at("/businessDecision/dispatchId"))) throw CorrectionCommand.corrupt();
        if (Set.of("REVIEW_READY", "RESOLVED").contains(caseFacts.path("status").asText())) {
            result.set("submittedReceipt", requests.original(id, true)); result.set("submittedCommand", requests.submittedCommand(id));
        }
    }

    ObjectNode returned(UUID warehouse, UUID dispatch, UUID caseId, boolean lock) {
        var result = read(warehouse, dispatch, caseId, lock);
        if (caseId!=null && !"RETURNED_STOCK".equals(result.at("/case/kind").asText())) throw CorrectionCommand.missing();
        var allFacts = (ObjectNode) result.path("facts");
        allFacts.set("reopening", returned.read(UUID.fromString(result.path("dispatchId").asText())));
        result.put("factsSha256", ReconciliationFacts.hash(allFacts)); return result;
    }

    static boolean isBusiness(String kind) { return Set.of("RECONCILE_DISCOVER", "RECONCILE_SUBMIT", "RECONCILE_APPROVE",
        "RECONCILE_RETURN_DISCOVER", "RECONCILE_RETURN_SUBMIT", "RECONCILE_RETURN_APPROVE",
        "RECONCILE_QUARANTINE_DISCOVER", "RECONCILE_QUARANTINE_SUBMIT", "RECONCILE_QUARANTINE_APPROVE").contains(kind); }

    static ObjectNode expected(JsonNode context, String auth) {
        var result = SubmissionJson.object().put("userAuthVersion", auth);
        var fields = context.has("dispositionId") ? Set.of("baleId", "dispositionId", "factsSha256")
            : Set.of("orderId", "dispatchId", "factsSha256");
        for (String key : fields) result.set(key, context.get(key));
        if (context.has("case")) {
            for (String key : Set.of("caseId", "status", "rowVersion")) result.set(key, context.path("case").get(key));
            result.put("discoveredReceiptId", context.path("discoveredReceipt").path("receiptId").asText());
            if (context.has("submittedReceipt")) result.put("submittedCommandSha256", context.path("submittedReceipt").path("commandSha256").asText());
        }
        return result;
    }
}
