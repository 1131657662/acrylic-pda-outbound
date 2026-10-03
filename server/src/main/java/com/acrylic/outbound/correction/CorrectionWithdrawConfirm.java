package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** The original maker confirms; result restoration does not depend on inventory deduction policy. */
final class CorrectionWithdrawConfirm implements CorrectionCommand.Operation {
    private final CorrectionStore store;
    private final CorrectionAccounting accounting;
    private final CorrectionOccupancyRestoration occupancy;
    private final CorrectionResultSnapshots snapshots;
    CorrectionWithdrawConfirm(CorrectionStore store, CorrectionAccounting accounting, CorrectionOccupancyRestoration occupancy) {
        this.store = store; this.accounting = accounting; this.occupancy = occupancy; snapshots = new CorrectionResultSnapshots(store);
    }
    @Override public String commandType() { return "WITHDRAW_CONFIRM"; }

    @Override public void afterReceipt(CorrectionCommand.Write write, JsonNode decision) {
        occupancy.record(write, decision);
    }

    @Override public JsonNode execute(CorrectionCommand.Write write) {
        validate(write);
        var current = store.lockCurrentVersion(write.orderId());
        long version = CorrectionResultSnapshots.number(current, "result_version") + 1;
        if (store.resultVersionExists(write.orderId(), version) != 0) throw CorrectionCommand.stale();
        store.dispatchesForUpdate(write.orderId());
        var lines = store.lines(write.documentId()); snapshots.requireTargets(write.orderId(), lines);
        var restoredOccupancies = occupancy.restore(write, lines);
        var restored = accounting.withdraw(lines, write.recordedAt());
        long[] totals = snapshots.totals(current, lines, true);
        var result = SubmissionJson.object().put("documentId", write.documentId().toString()).put("status", "WITHDRAWN")
            .put("orderId", write.orderId().toString()).put("resultVersion", Long.toString(version))
            .put("effectiveCount", Long.toString(totals[0])).put("effectiveWeightG", Long.toString(totals[1]))
            .put("reversedEntryCount", Integer.toString(restored.size())).put("confirmedBy", write.actor().path("principalId").asText())
            .put("headerSnapshot", current.get("header_snapshot").toString());
        result.set("reversedEntries", restored);
        result.set("restoredOccupancies", restoredOccupancies);
        snapshots.version(write, current, result); snapshots.scans(write, version, lines, true);
        store.moveCurrentPointer(write.orderId(), version, write.recordedAt());
        if (store.confirmWithdrawal(write.documentId(), UUID.fromString(write.actor().path("principalId").asText()), write.recordedAt()) != 1)
            throw CorrectionCommand.stale();
        snapshots.outbox(write, result, "CORRECTION_WITHDRAWN"); result.remove("headerSnapshot");
        return result;
    }

    private void validate(CorrectionCommand.Write write) {
        if (write.documentId() == null) throw CorrectionCommand.missing();
        if (!"WITHDRAW_PENDING".equals(write.anchor().path("status").asText())) throw CorrectionCommand.stale();
        if (!write.actor().path("principalId").asText().equals(write.anchor().path("requestedBy").asText())) throw CorrectionCommand.denied();
        // Header restoration needs its own frozen patch proof; do not claim a no-op withdrawal.
        if ("HEADER_CORRECTION".equals(write.anchor().path("type").asText())) throw CorrectionCommand.ledgerConflict();
    }
}
