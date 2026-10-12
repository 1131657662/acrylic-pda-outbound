package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ReturnedStockPolicyTest {
    private ObjectNode facts() {
        var facts = SubmissionJson.object(); String bale = UUID.randomUUID().toString(), debit = UUID.randomUUID().toString();
        facts.putObject("result").put("orderStatus", "COMPLETED");
        facts.putObject("dispatch").put("baleId", bale).put("count", "0").put("weightG", "0").put("dispatchCycle", "1");
        facts.putObject("stock").put("dispatchCycle", "2").put("bookStatus", "DEDUCTED").put("upstreamPresent", 1)
            .put("snapshotId", "snapshot").put("activeSnapshotId", "snapshot").put("lastSeenSnapshotId", "snapshot").putNull("blockedReason");
        facts.putObject("reopening").put("documentStatus", "EFFECTIVE").put("type", "PHYSICAL_RETURN").put("cycleBefore", "1").put("cycleAfter", "2");
        facts.putArray("owners"); var ledger = facts.putArray("ledger");
        ledger.addObject().put("id", debit).put("baleId", bale).put("kind", "ORIGINAL").put("quantityDelta", "-1").put("weightDeltaG", "-300000");
        ledger.addObject().put("id", UUID.randomUUID().toString()).put("baleId", bale).put("kind", "REVERSAL")
            .put("quantityDelta", "1").put("weightDeltaG", "300000").put("reversesInventoryEntryId", debit);
        return facts;
    }
    @Test void onlyAProvenReversalAndNewCycleCanRestoreTheDeductedProjection() { assertThatCode(() -> ReturnedStockPolicy.eligible(facts())).doesNotThrowAnyException(); }
    @ParameterizedTest @ValueSource(strings={"quarantine", "missing-upstream", "new-snapshot", "new-owner", "withdrawn", "old-cycle"})
    void independentBlockersCannotBeClearedByCaseResolution(String blocker) {
        var facts = facts(); var stock = (ObjectNode) facts.path("stock");
        switch (blocker) {
            case "quarantine" -> stock.put("blockedReason", "QUARANTINED");
            case "missing-upstream" -> stock.put("upstreamPresent", 0);
            case "new-snapshot" -> stock.put("activeSnapshotId", "new");
            case "new-owner" -> facts.withArray("owners").addObject().put("state", "QUARANTINED");
            case "withdrawn" -> ((ObjectNode) facts.path("reopening")).put("documentStatus", "WITHDRAWN");
            case "old-cycle" -> stock.put("dispatchCycle", "1");
            default -> throw new AssertionError(blocker);
        }
        assertThatThrownBy(() -> ReturnedStockPolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
    @Test void matchingZeroSumWithoutTheRealReversalReferenceIsRejected() {
        var facts = facts(); ((ObjectNode) facts.at("/ledger/1")).put("reversesInventoryEntryId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> ReturnedStockPolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
}
