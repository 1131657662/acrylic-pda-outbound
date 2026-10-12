package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PcQuarantinePolicyTest {
    private ObjectNode facts() {
        var facts=SubmissionJson.object();
        facts.putObject("origin").put("sourceKind","PC").put("closeMode",true).put("sourceClosed",true)
            .put("action","QUARANTINE").put("cycle","1").put("warehouseId","warehouse")
            .put("orderId","order").put("dispositionId","disposition");
        facts.putObject("stock").put("dispatchCycle","1").put("warehouseId","warehouse").put("upstreamPresent",1)
            .put("snapshotId","snapshot").put("activeSnapshotId","snapshot").put("lastSeenSnapshotId","snapshot")
            .put("bookStatus","NOT_DEDUCTED").putNull("blockedReason");
        var owner=facts.putArray("owners").addObject().put("state","QUARANTINED").put("cycle","1")
            .put("orderId","order").put("pcDispositionId","disposition");
        for(String field:java.util.List.of("submissionId","recoveryPackageId","continuationId","dispositionEvidenceId","pcWorkId",
            "temporaryContinuationId","temporaryHandoverId","dispositionId","sourceScopeId","sourceWarehouseId","sourceEvidenceSetId")) owner.putNull(field);
        facts.putArray("dispatches"); facts.putArray("ledger"); return facts;
    }
    @Test void originalPcOwnerIsReleasedWithoutAnInventedTempScope() {
        assertThatCode(()->QuarantinePolicy.eligible(facts())).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={"orderId","pcDispositionId","cycle","pcWorkId","sourceScopeId","dispositionId","submissionId"})
    void differentOrAdditionalOwnersRemainBlocked(String field) {
        var facts=facts(); ((ObjectNode)facts.at("/owners/0")).put(field,"other");
        assertThatThrownBy(()->QuarantinePolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"upstream","deducted","blocked","snapshot","open","ledger","dispatch"})
    void independentSourceStockAndAccountingBlocksCannotBeCleared(String field) {
        var facts=facts(); var stock=(ObjectNode)facts.path("stock");
        switch(field) {
            case "upstream" -> stock.put("upstreamPresent",0);
            case "deducted" -> stock.put("bookStatus","DEDUCTED");
            case "blocked" -> stock.put("blockedReason","UNKNOWN");
            case "snapshot" -> stock.put("activeSnapshotId","other");
            case "open" -> ((ObjectNode)facts.path("origin")).put("sourceClosed",false);
            case "ledger" -> facts.withArray("ledger").addObject();
            case "dispatch" -> facts.withArray("dispatches").addObject();
            default -> throw new AssertionError(field);
        }
        assertThatThrownBy(()->QuarantinePolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
}
