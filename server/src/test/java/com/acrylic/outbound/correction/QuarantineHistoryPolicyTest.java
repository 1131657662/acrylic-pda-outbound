package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class QuarantineHistoryPolicyTest {
    private static final String BALE=UUID.randomUUID().toString(),DISPATCH=UUID.randomUUID().toString();
    private static final String DEBIT=UUID.randomUUID().toString(),REVERSAL=UUID.randomUUID().toString();
    private ObjectNode facts() {
        var facts=SubmissionJson.object();facts.putObject("stock").put("baleId",BALE).put("warehouseId","warehouse").put("dispatchCycle","2");
        facts.putArray("dispatches").addObject().put("dispatchId",DISPATCH);facts.putArray("ledger").addObject().put("id",DEBIT);
        facts.withArray("ledger").addObject().put("id",REVERSAL);
        var item=facts.putArray("history").addObject();
        item.putObject("result").put("orderStatus","COMPLETED").put("warehouseId","warehouse").put("orderId","order");
        item.putObject("dispatch").put("dispatchId",DISPATCH).put("baleId",BALE).put("dispatchCycle","1").put("count","0").put("weightG","0");
        var reopening=item.putObject("reopening").put("documentStatus","EFFECTIVE").put("type","PHYSICAL_RETURN").put("cycleBefore","1").put("cycleAfter","2");
        var approval=item.putObject("stockRestorationReceipt").put("commandType","RECONCILE_RETURN_APPROVE").put("orderId","order");
        item.put("reopeningSha256","original-reopening-hash");
        approval.putObject("context").put("dispatchId",DISPATCH).put("reopeningSha256","original-reopening-hash");
        approval.putObject("businessDecision").put("status","RESOLVED").put("bookStatus","NOT_DEDUCTED").put("dispatchCycle","2");
        item.putArray("ledger").addObject().put("id",DEBIT).put("baleId",BALE).put("kind","ORIGINAL").put("quantityDelta","-1").put("weightDeltaG","-300500");
        item.withArray("ledger").addObject().put("id",REVERSAL).put("baleId",BALE).put("kind","REVERSAL").put("lineId",UUID.randomUUID().toString())
            .put("quantityDelta","1").put("weightDeltaG","300500").put("reversesInventoryEntryId",DEBIT);
        return facts;
    }
    @Test void originalReturnAndRestorationProveZeroContributionInAPriorCycle() {
        assertThatCode(()->QuarantineHistoryPolicy.eligible(facts())).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={"active","weight","cycle","warehouse","bale","withdrawn","not-physical","restoration","proof",
        "wrong-order","unrestored","orphan","missing-ledger","cross-bale","bad-reference","duplicate-history"})
    void unprovenOldCyclesAndNonzeroContributionsCannotBeCleared(String conflict) {
        var facts=facts();var item=(ObjectNode)facts.at("/history/0");var dispatch=(ObjectNode)item.path("dispatch");
        var approval=(ObjectNode)item.path("stockRestorationReceipt");
        switch(conflict) {
            case "active" -> dispatch.put("count","1");
            case "weight" -> dispatch.put("weightG","1");
            case "cycle" -> dispatch.put("dispatchCycle","2");
            case "warehouse" -> ((ObjectNode)item.path("result")).put("warehouseId","other");
            case "bale" -> dispatch.put("baleId",UUID.randomUUID().toString());
            case "withdrawn" -> ((ObjectNode)item.path("reopening")).put("documentStatus","WITHDRAW_PENDING");
            case "not-physical" -> ((ObjectNode)item.path("reopening")).put("type","WEIGHT_CORRECTION");
            case "restoration" -> approval.put("commandType","RECONCILE_APPROVE");
            case "proof" -> ((ObjectNode)approval.path("context")).put("reopeningSha256","changed");
            case "wrong-order" -> approval.put("orderId","other");
            case "unrestored" -> ((ObjectNode)approval.path("businessDecision")).put("bookStatus","DEDUCTED");
            case "orphan" -> facts.withArray("ledger").addObject().put("id",UUID.randomUUID().toString());
            case "missing-ledger" -> item.withArray("ledger").removeAll();
            case "cross-bale" -> ((ObjectNode)item.at("/ledger/1")).put("baleId",UUID.randomUUID().toString());
            case "bad-reference" -> ((ObjectNode)item.at("/ledger/1")).put("reversesInventoryEntryId",UUID.randomUUID().toString());
            case "duplicate-history" -> facts.withArray("history").add(item.deepCopy());
            default -> throw new AssertionError(conflict);
        }
        assertThatThrownBy(()->QuarantineHistoryPolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
    @Test void offsettingTotalsWithoutAnOriginalReversalReferenceAreRejected() {
        var facts=facts();((ObjectNode)facts.at("/history/0/ledger/1")).remove("reversesInventoryEntryId");
        assertThatThrownBy(()->QuarantineHistoryPolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
    @Test void advancedCycleWithoutAnyPriorDispatchProofIsRejected() {
        var facts=facts();facts.withArray("dispatches").removeAll();facts.withArray("ledger").removeAll();facts.remove("history");
        assertThatThrownBy(()->QuarantineHistoryPolicy.eligible(facts)).isInstanceOf(BusinessException.class);
    }
}
