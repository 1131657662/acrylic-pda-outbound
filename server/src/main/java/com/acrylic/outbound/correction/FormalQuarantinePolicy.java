package com.acrylic.outbound.correction;

import com.fasterxml.jackson.databind.JsonNode;

final class FormalQuarantinePolicy {
    private FormalQuarantinePolicy() { }
    static void eligible(JsonNode facts) {
        var origin=facts.path("origin"); var stock=facts.path("stock"); var owners=facts.path("owners");
        if(!origin.path("closeMode").asBoolean() || !origin.path("sourceClosed").asBoolean()
            || !"QUARANTINE".equals(origin.path("action").asText()) || !origin.path("cycle").equals(stock.path("dispatchCycle"))
            || !origin.path("warehouseId").equals(stock.path("warehouseId")) || !stock.path("upstreamPresent").asBoolean()
            || !stock.hasNonNull("activeSnapshotId") || !stock.path("snapshotId").equals(stock.path("activeSnapshotId"))
            || !stock.path("lastSeenSnapshotId").equals(stock.path("activeSnapshotId"))
            || !"NOT_DEDUCTED".equals(stock.path("bookStatus").asText()) || !stock.path("blockedReason").isNull()
            || !owners.isArray() || owners.size()!=1) throw CorrectionCommand.ledgerConflict();
        var owner=owners.get(0);
        if(!"QUARANTINED".equals(owner.path("state").asText()) || !owner.path("dispositionEvidenceId").equals(origin.path("dispositionId"))
            || !owner.path("orderId").equals(origin.path("orderId")) || !owner.path("cycle").equals(origin.path("cycle"))) throw CorrectionCommand.ledgerConflict();
        for(String key:java.util.List.of("submissionId","recoveryPackageId","continuationId","pcWorkId","pcDispositionId",
            "temporaryContinuationId","temporaryHandoverId","dispositionId","sourceScopeId","sourceWarehouseId","sourceEvidenceSetId"))
            if(!owner.path(key).isNull()) throw CorrectionCommand.ledgerConflict();
        QuarantineHistoryPolicy.eligible(facts);
    }
}
