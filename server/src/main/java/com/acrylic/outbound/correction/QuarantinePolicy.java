package com.acrylic.outbound.correction;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Set;

/** Removing a quarantine never supplies upstream stock, reverses a debit, or creates another cycle. */
final class QuarantinePolicy {
    private QuarantinePolicy() { }
    static void eligible(JsonNode facts) {
        if("FORMAL".equals(facts.at("/origin/sourceKind").asText())) { FormalQuarantinePolicy.eligible(facts); return; }
        if("PC".equals(facts.at("/origin/sourceKind").asText())) { PcQuarantinePolicy.eligible(facts); return; }
        var stock=facts.path("stock"); var origin=facts.path("origin"); var owners=facts.path("owners");
        source(origin);
        if(!"QUARANTINE".equals(origin.path("action").asText()) || !origin.path("cycle").equals(stock.path("dispatchCycle"))
            || !origin.path("warehouseId").equals(stock.path("warehouseId")) || !stock.path("upstreamPresent").asBoolean()
            || !stock.hasNonNull("activeSnapshotId") || !stock.path("snapshotId").equals(stock.path("activeSnapshotId"))
            || !stock.path("lastSeenSnapshotId").equals(stock.path("activeSnapshotId"))
            || !"NOT_DEDUCTED".equals(stock.path("bookStatus").asText()) || !stock.path("blockedReason").isNull()
            || !owners.isArray() || owners.size()!=1) throw CorrectionCommand.ledgerConflict();
        owner(owners.get(0),origin); QuarantineHistoryPolicy.eligible(facts);
    }

    private static void source(JsonNode origin) {
        if(origin.path("closeMode").asBoolean()) {
            if(!origin.path("sourceClosed").asBoolean()) throw CorrectionCommand.ledgerConflict();
            return;
        }
        var receipt=origin.path("originalReceipt");
        if(!"PHYSICAL_DISPOSITION".equals(receipt.path("commandType").asText())
            || !Set.of("ABORTED","REJECTED").contains(origin.path("sourceStatus").asText())
            || !origin.path("sourceStatus").equals(receipt.at("/originalContext/status"))) throw CorrectionCommand.ledgerConflict();
    }

    private static void owner(JsonNode owner,JsonNode origin) {
        if(!"QUARANTINED".equals(owner.path("state").asText()) || !owner.path("cycle").equals(origin.path("cycle")))
            throw CorrectionCommand.ledgerConflict();
        for(var key:java.util.Map.of("dispositionId","dispositionId","sourceScopeId","scopeId",
            "sourceWarehouseId","warehouseId","sourceEvidenceSetId","evidenceSetId").entrySet())
            if(!owner.path(key.getKey()).equals(origin.path(key.getValue()))) throw CorrectionCommand.ledgerConflict();
        for(String key:Set.of("orderId","submissionId","recoveryPackageId","continuationId","dispositionEvidenceId",
            "pcWorkId","pcDispositionId","temporaryContinuationId","temporaryHandoverId"))
            if(!owner.path(key).isNull()) throw CorrectionCommand.ledgerConflict();
    }

    static void evidence(JsonNode evidence,Instant now) { ReconciliationPolicy.physicalEvidence(evidence,now,"QUARANTINE_CLEARED_IN_WAREHOUSE"); }
}
