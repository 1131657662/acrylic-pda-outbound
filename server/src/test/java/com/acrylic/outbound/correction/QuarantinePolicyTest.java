package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class QuarantinePolicyTest {
    private ObjectNode facts() {
        var facts=SubmissionJson.object();
        facts.putObject("stock").put("dispatchCycle","1").put("warehouseId","warehouse").put("upstreamPresent",1)
            .put("bookStatus","NOT_DEDUCTED").put("snapshotId","snapshot").put("lastSeenSnapshotId","snapshot")
            .put("activeSnapshotId","snapshot").putNull("blockedReason");
        facts.putObject("origin").put("closeMode",true).put("sourceClosed",true).put("action","QUARANTINE").put("cycle","1")
            .put("warehouseId","warehouse").put("scopeId","scope").put("dispositionId","disposition").put("evidenceSetId","set");
        var owner=facts.putArray("owners").addObject().put("state","QUARANTINED").put("cycle","1")
            .put("dispositionId","disposition").put("sourceScopeId","scope").put("sourceWarehouseId","warehouse").put("sourceEvidenceSetId","set");
        for(String key:java.util.List.of("orderId","submissionId","recoveryPackageId","continuationId","dispositionEvidenceId",
            "pcWorkId","pcDispositionId","temporaryContinuationId","temporaryHandoverId")) owner.putNull(key);
        facts.putArray("dispatches"); facts.putArray("ledger"); return facts;
    }
    @Test void provenClosedUnpostedSourceCanBeCleared() { assertThatCode(()->QuarantinePolicy.eligible(facts())).doesNotThrowAnyException(); }
    @ParameterizedTest @ValueSource(strings={"REJECTED","ABORTED"})
    void originalTerminalPerBaleDispositionDoesNotRequireClosingOtherWork(String status) {
        var facts=open(status);
        assertThatCode(()->QuarantinePolicy.eligible(facts)).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={"SCANNING","REVIEW_REQUIRED","SUCCEEDED",""})
    void openDispositionCannotReleaseAnUnprovenOriginalState(String status) {
        assertThatThrownBy(()->QuarantinePolicy.eligible(open(status))).isInstanceOf(BusinessException.class);
    }
    @Test void closureReceiptCannotProveAPerBaleDisposition() {
        var facts=open("ABORTED"); ((ObjectNode)facts.at("/origin/originalReceipt")).put("commandType","CLOSE_DISPOSITION");
        assertThatThrownBy(()->QuarantinePolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
    @Test void originalTerminalStateDriftIsRejected() {
        var facts=open("ABORTED"); ((ObjectNode)facts.path("origin")).put("sourceStatus","REJECTED");
        assertThatThrownBy(()->QuarantinePolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
    private ObjectNode open(String status) {
        var facts=facts(); var origin=(ObjectNode)facts.path("origin");
        origin.put("closeMode",false).put("sourceClosed",false).put("sourceStatus",status);
        origin.putObject("originalReceipt").put("commandType","PHYSICAL_DISPOSITION").putObject("originalContext").put("status",status);
        return facts;
    }
    @ParameterizedTest @ValueSource(strings={"missing-upstream","snapshot","blocked","deducted","open","not-quarantine","cycle","warehouse","dispatch","ledger"})
    void independentStockAndSourceConflictsRemainBlocked(String conflict) {
        var facts=facts(); var stock=(ObjectNode)facts.path("stock"); var origin=(ObjectNode)facts.path("origin");
        switch(conflict) {
            case "missing-upstream" -> stock.put("upstreamPresent",0);
            case "snapshot" -> stock.put("activeSnapshotId","other");
            case "blocked" -> stock.put("blockedReason","UNKNOWN_SOURCE");
            case "deducted" -> stock.put("bookStatus","DEDUCTED");
            case "open" -> origin.put("sourceClosed",false);
            case "not-quarantine" -> origin.put("action","RELEASE");
            case "cycle" -> origin.put("cycle","2");
            case "warehouse" -> origin.put("warehouseId","other");
            case "dispatch" -> facts.withArray("dispatches").addObject().put("dispatchCycle","1");
            case "ledger" -> facts.withArray("ledger").addObject().put("quantityDelta","0");
            default -> throw new AssertionError(conflict);
        }
        assertThatThrownBy(()->QuarantinePolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"sourceScopeId","sourceEvidenceSetId","sourceWarehouseId","dispositionId","pcWorkId","temporaryHandoverId"})
    void anotherOwnerCannotBeReleasedWithTheOriginalSource(String field) {
        var facts=facts(); ((ObjectNode)facts.at("/owners/0")).put(field,"other");
        assertThatThrownBy(()->QuarantinePolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
    @Test void missingOwnersAndAnAdditionalOwnerAreRejected() {
        var missing=facts(); missing.withArray("owners").removeAll(); var extra=facts(); extra.withArray("owners").addObject();
        assertThatThrownBy(()->QuarantinePolicy.eligible(missing)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->QuarantinePolicy.eligible(extra)).isInstanceOf(BusinessException.class);
    }
    @Test void physicalObservationCannotBeReplacedWithTheReturnedStockObservation() {
        var evidence=SubmissionJson.object().put("observedAt",java.time.Instant.now().toString()).put("place","仓库")
            .put("reference","已核实解除隔离条件").put("observation","QUARANTINE_CLEARED_IN_WAREHOUSE"); evidence.putArray("attachments").addObject();
        assertThatCode(()->QuarantinePolicy.evidence(evidence,java.time.Instant.now())).doesNotThrowAnyException();
        evidence.put("observation","RETURNED_IN_WAREHOUSE");
        assertThatThrownBy(()->QuarantinePolicy.evidence(evidence,java.time.Instant.now())).isInstanceOf(BusinessException.class);
    }
}
