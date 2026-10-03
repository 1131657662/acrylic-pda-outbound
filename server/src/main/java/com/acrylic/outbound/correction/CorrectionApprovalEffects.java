package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Applies frozen correction lines while preserving original dispatches and postings. */
final class CorrectionApprovalEffects {
    private static final Map<String, String> ACTIONS = Map.of("PHYSICAL_RETURN", "RETURN",
        "DISPATCH_CANCELLATION", "CANCEL_DISPATCH", "WEIGHT_CORRECTION", "WEIGHT", "HEADER_CORRECTION", "HEADER");
    private final CorrectionStore store;
    private final CorrectionAccounting accounting;
    CorrectionApprovalEffects(CorrectionStore store, CorrectionAccounting accounting) { this.store = store; this.accounting = accounting; }

    ObjectNode apply(CorrectionCommand.Write write, long version, List<Map<String, Object>> lines) {
        var result = SubmissionJson.object(); var effects = result.putArray("effects");
        var ledger = result.putArray("ledgerKeys"); var reopened = result.putArray("reopenedCycles");
        String type = write.anchor().path("type").asText();
        for (var line : lines) {
            UUID lineId = UUID.fromString(line.get("line_id").toString());
            UUID dispatch = line.get("dispatch_id") == null ? null : UUID.fromString(line.get("dispatch_id").toString());
            long count = CorrectionResultSnapshots.number(line, "target_count") - CorrectionResultSnapshots.number(line, "before_count");
            long weight = CorrectionResultSnapshots.number(line, "target_weight_g") - CorrectionResultSnapshots.number(line, "before_weight_g");
            if (dispatch != null) {
                var facts = store.dispatchFacts(write.orderId(), dispatch);
                if (Set.of("DISPATCH_CANCELLATION", "PHYSICAL_RETURN").contains(type)) reopened.add(reopen(write, line, facts));
                accounting.approve(line, UUID.fromString(facts.path("baleId").asText()), write.recordedAt()).forEach(ledger::add);
            }
            store.insertEffect(Ids.next(), lineId, write.orderId(), version, dispatch, ACTIONS.getOrDefault(type, "WEIGHT"),
                count, weight, CorrectionResultSnapshots.instant(line.get("business_occurred_at")), write.recordedAt());
            effects.add(SubmissionJson.object().put("lineId", lineId.toString()).put("dispatchId", dispatch == null ? null : dispatch.toString())
                .put("countDelta", Long.toString(count)).put("weightDeltaG", Long.toString(weight)));
        }
        return result;
    }

    private ObjectNode reopen(CorrectionCommand.Write write, Map<String, Object> line, com.fasterxml.jackson.databind.JsonNode facts) {
        UUID dispatch = UUID.fromString(line.get("dispatch_id").toString());
        if (store.cycleReopeningExists(dispatch)) throw new BusinessException(409, "CYCLE_REOPENING_EXISTS", "该派发周期已重开过");
        String evidence = line.get("evidence_sha256") == null ? null : line.get("evidence_sha256").toString();
        if (evidence == null || evidence.isBlank()) throw CorrectionCommand.evidenceRequired();
        int cycle = facts.path("dispatchCycle").asInt();
        UUID bale = UUID.fromString(facts.path("baleId").asText());
        store.reopenDispatchOccupancy(write.orderId(), bale, cycle, cycle + 1);
        store.insertCycleReopening(Ids.next(), dispatch, UUID.fromString(line.get("line_id").toString()), cycle, cycle + 1,
            evidence, write.anchor().path("expectedResultVersion").asLong() + 1, store.recoveryEpoch(), write.recordedAt());
        return SubmissionJson.object().put("dispatchId", dispatch.toString()).put("cycleBefore", Integer.toString(cycle))
            .put("cycleAfter", Integer.toString(cycle + 1));
    }
}
