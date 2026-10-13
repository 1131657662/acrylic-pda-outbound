package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class FormalQuarantinePolicyTest {
    private ObjectNode facts() {
        var facts=SubmissionJson.object();
        facts.putObject("origin").put("sourceKind","FORMAL").put("closeMode",true).put("sourceClosed",true).put("action","QUARANTINE")
            .put("cycle","1").put("warehouseId","warehouse").put("orderId","order").put("dispositionId","physical-proof");
        facts.putObject("stock").put("dispatchCycle","1").put("warehouseId","warehouse").put("upstreamPresent",1).put("bookStatus","NOT_DEDUCTED")
            .put("snapshotId","snapshot").put("activeSnapshotId","snapshot").put("lastSeenSnapshotId","snapshot").putNull("blockedReason");
        var owner=facts.putArray("owners").addObject().put("state","QUARANTINED").put("cycle","1").put("orderId","order").put("dispositionEvidenceId","physical-proof");
        for(String field:java.util.List.of("submissionId","recoveryPackageId","continuationId","pcWorkId","pcDispositionId","temporaryContinuationId","temporaryHandoverId",
            "dispositionId","sourceScopeId","sourceWarehouseId","sourceEvidenceSetId"))owner.putNull(field);
        facts.putArray("dispatches");facts.putArray("ledger");return facts;
    }
    @Test void originalFormalAppliedOwnerDoesNotCreateATemporarySource() { assertThatCode(()->QuarantinePolicy.eligible(facts())).doesNotThrowAnyException(); }
    @Test void independentlySignedApplicationAllowsAnUnclosedSourceToKeepItsOtherWork() {
        var facts=facts();var origin=(ObjectNode)facts.path("origin");origin.put("sourceClosed",false);
        origin.putObject("originalReceipt").putObject("application").putObject("receipt")
            .put("status","DISPOSITIONS_APPLIED").put("signedApplication","verified-original-signature");
        assertThatCode(()->QuarantinePolicy.eligible(facts)).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={"orderId","cycle","dispositionEvidenceId","submissionId","pcDispositionId","sourceScopeId","temporaryContinuationId"})
    void otherOwnersCannotBeRemoved(String field) {
        var facts=facts();((ObjectNode)facts.at("/owners/0")).put(field,"other");assertThatThrownBy(()->QuarantinePolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"open","upstream","blocked","deducted","snapshot","ledger","dispatch"})
    void independentSourceAndStockBlocksRemain(String field) {
        var facts=facts();var stock=(ObjectNode)facts.path("stock");
        switch(field) {
            case "open" -> ((ObjectNode)facts.path("origin")).put("sourceClosed",false);
            case "upstream" -> stock.put("upstreamPresent",0);
            case "blocked" -> stock.put("blockedReason","UNRESOLVED");
            case "deducted" -> stock.put("bookStatus","DEDUCTED");
            case "snapshot" -> stock.put("activeSnapshotId","other");
            case "ledger" -> facts.withArray("ledger").addObject();
            case "dispatch" -> facts.withArray("dispatches").addObject();
            default -> throw new AssertionError(field);
        }
        assertThatThrownBy(()->QuarantinePolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
}
