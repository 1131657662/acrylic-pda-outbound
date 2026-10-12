package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReconciliationPolicyTest {
    private ObjectNode facts() {
        var result = SubmissionJson.object();
        result.putObject("result").put("orderStatus", "COMPLETED").put("orderId", "order").put("warehouseId", "warehouse");
        result.putObject("dispatch").put("count", "1").put("weightG", "310000").put("dispatchCycle", "1");
        result.putObject("stock").put("bookStatus", "NOT_DEDUCTED").put("dispatchCycle", "1").put("warehouseId", "warehouse")
            .put("upstreamPresent", false).put("blockedReason", "UNRESOLVED");
        result.putArray("owners").addObject().put("state", "DISPATCHED").put("orderId", "order").put("cycle", "1");
        result.putArray("ledger"); return result;
    }

    @Test void missingDebitDoesNotTreatUpstreamAbsenceOrQuarantineAsReleaseEvidence() {
        var facts = facts(); var original = facts.deepCopy();
        assertThatCode(() -> ReconciliationPolicy.missingPosting(facts)).doesNotThrowAnyException();
        assertThat(facts).isEqualTo(original);
    }
    @Test void rejectsAnyExistingPostingInsteadOfBalancingAnAggregate() {
        var facts = facts(); facts.withArray("ledger").addObject().put("quantityDelta", "0");
        assertThatThrownBy(() -> ReconciliationPolicy.missingPosting(facts)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsZeroResultContribution() {
        var facts = facts(); ((ObjectNode) facts.path("dispatch")).put("count", "0");
        assertThatThrownBy(() -> ReconciliationPolicy.missingPosting(facts)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsNewCycleOwnerForOldDispatch() {
        var facts = facts(); ((ObjectNode) facts.path("stock")).put("dispatchCycle", "2");
        assertThatThrownBy(() -> ReconciliationPolicy.missingPosting(facts)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsPhysicalReturnAsMissingDebitEvidence() {
        var evidence = SubmissionJson.object().put("observedAt", "2026-10-13T01:00:00Z").put("place", "warehouse")
            .put("reference", "receipt").put("observation", "RETURNED"); evidence.putArray("attachments");
        assertThatThrownBy(() -> ReconciliationPolicy.evidence(evidence, Instant.parse("2026-10-13T02:00:00Z")))
            .isInstanceOf(BusinessException.class);
    }
    @Test void rejectsFuturePhysicalObservation() {
        var evidence = SubmissionJson.object().put("observedAt", "2026-10-13T03:00:00Z").put("place", "warehouse")
            .put("reference", "receipt").put("observation", "DISPATCHED_CONFIRMED"); evidence.putArray("attachments");
        assertThatThrownBy(() -> ReconciliationPolicy.evidence(evidence, Instant.parse("2026-10-13T02:00:00Z")))
            .isInstanceOf(BusinessException.class);
    }
}
