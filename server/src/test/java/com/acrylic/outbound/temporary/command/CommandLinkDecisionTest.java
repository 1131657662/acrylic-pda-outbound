package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
