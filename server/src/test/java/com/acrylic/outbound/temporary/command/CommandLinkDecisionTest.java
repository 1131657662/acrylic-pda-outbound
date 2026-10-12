package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CommandLinkDecisionTest {
    @ParameterizedTest
    @CsvSource({"APPLIED,1,1", "APPLIED,2,2", "APPLIED,3,2", "NOT_APPLIED,0,1", "NOT_APPLIED,2,1"})
    void acceptsExactCandidateExtentIncludingHistoricalDecisions(String state,int source,int total) {
        assertThatCode(()->CommandDecision.validate(receipt(state,source,total))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"APPLIED,1,2", "APPLIED,2,1", "APPLIED,3,3", "NOT_APPLIED,2,2"})
    void rejectsWrongCandidateExtent(String state,int source,int total) {
        assertThatThrownBy(()->CommandDecision.validate(receipt(state,source,total)))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
    }

    @Test void acceptsSignedSelectionWhilePreservingCompleteTotal() {
        var receipt=receipt("APPLIED",1,3);selection(receipt,2);
        assertThatCode(()->CommandDecision.validate(receipt)).doesNotThrowAnyException();
        assertThat(receipt.at("/businessDecision/candidates/total").asText()).isEqualTo("3");
    }

    @Test void rejectsClaimedEffectiveTotalWithoutEnoughExclusions() {
        var receipt=receipt("APPLIED",1,3);selection(receipt,1);
        assertThatThrownBy(()->CommandDecision.validate(receipt)).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
    }

    @Test void rejectsExcludingTheSelectedTarget() {
        var receipt=receipt("APPLIED",1,2);selection(receipt,1);
        ((ObjectNode)receipt.at("/businessDecision/selection/exclusions/0"))
            .set("orderId",receipt.at("/businessDecision/submissionReceipt/targetOrderId"));
        assertThatThrownBy(()->CommandDecision.validate(receipt)).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
    }

    @Test void rejectsRepeatedCandidateReferences() {
        var receipt=receipt("APPLIED",1,3);selection(receipt,2);
        ((ObjectNode)receipt.at("/businessDecision/selection/exclusions/1"))
            .set("orderId",receipt.at("/businessDecision/selection/exclusions/0/orderId"));
        assertThatThrownBy(()->CommandDecision.validate(receipt)).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
    }

    private void selection(ObjectNode receipt,int count) {
        var selection=((ObjectNode)receipt.path("businessDecision")).putObject("selection").put("effectiveTotal","1");
        var refs=selection.putArray("exclusions");
        for(int i=1;i<=count;i++)refs.addObject().put("orderId",String.format("00000000-0000-0000-0000-%012d",i))
            .put("receiptId",UUID.randomUUID().toString()).put("originalResponseSha256","c".repeat(64)).put("resultingDecisionVersion",Integer.toString(i));
    }

    private ObjectNode receipt(String state,int source,int total) {
        var receipt=SubmissionJson.object().put("status","RECORDED").put("commandType","LINK");
        receipt.putObject("expected").put("candidateContextSha256","a".repeat(64));
        var decision=receipt.putObject("businessDecision").put("kind","LINK").put("shortShipmentReason","")
            .putNull("shortApprovalReceiptId").putNull("shortApprovalSha256");
        decision.putArray("products").addObject();
        decision.putObject("candidates").put("policyVersion","REVIEW_DEFAULT_V1").put("fromDate","2026-10-01")
            .put("throughDate","2026-10-15").put("total",Integer.toString(total))
            .put("fingerprint","b".repeat(64)).put("contextSha256","a".repeat(64));
        var link=decision.putObject("submissionReceipt").put("applyState",state).put("targetOrderId",UUID.randomUUID().toString())
            .put("targetOrderNo","FH-TEST").put("targetRevision","1").put("targetRowVersion","1")
            .put("sourceMappingVersion",Integer.toString(source)).put("candidateContextSha256","a".repeat(64));
        if(source==0)link.putNull("sourceCanonicalOrderId");else link.put("sourceCanonicalOrderId",UUID.randomUUID().toString());
        if(state.equals("APPLIED"))link.put("mappingId",UUID.randomUUID().toString()).put("mappingVersion",Integer.toString(source+1));
        return receipt;
    }
}
