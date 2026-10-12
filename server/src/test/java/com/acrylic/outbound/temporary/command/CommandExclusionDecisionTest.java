package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.temporary.protocol.TemporaryExclusionShape;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CommandExclusionDecisionTest {
    @Test void retainsFullCandidateExtentInsteadOfRewritingItToEffectiveCount() {
        var receipt=TemporaryExclusionFixture.receipt();
        assertThatCode(()->CommandDecision.validate(receipt)).doesNotThrowAnyException();
        assertThat(receipt.at("/businessDecision/candidates/total").asText()).isEqualTo("3");
    }
    @ParameterizedTest @ValueSource(strings={"orderId","revision","rowVersion","basis","evidence","withdrawalRequestId"})
    void missingRequiredReviewFieldCannotBeSigned(String field) {
        var receipt=TemporaryExclusionFixture.receipt();((ObjectNode)receipt.at("/businessDecision/exclusion")).remove(field);
        assertThatThrownBy(()->CommandDecision.validate(receipt)).isInstanceOf(RuntimeException.class);
    }
    @Test void changedCandidateVersionCannotReuseSignedReview() {
        var receipt=TemporaryExclusionFixture.receipt();((ObjectNode)receipt.at("/businessDecision/exclusion/candidate/order")).put("rowVersion","3");
        assertThatThrownBy(()->CommandDecision.validate(receipt)).isInstanceOf(RuntimeException.class);
    }
    @Test void receiptCannotReplaceOriginalNarrativeOrCandidateIdentity() {
        var receipt=TemporaryExclusionFixture.receipt();var decision=receipt.path("businessDecision");
        var input=(ObjectNode)decision.path("exclusion").deepCopy();input.remove(java.util.List.of("candidate","work","withdrawalReceipt"));
        var body=SubmissionJson.object();body.set("exclusion",input);
        assertThatCode(()->CommandExclusionDecision.bind(body,decision)).doesNotThrowAnyException();
        input.put("evidence","另一个依据");
        assertThatThrownBy(()->CommandExclusionDecision.bind(body,decision)).isInstanceOf(RuntimeException.class);
    }
    @ParameterizedTest @ValueSource(strings={""," ","换行\n依据"})
    void blankOrControlEvidenceIsRejected(String text) {
        var input=TemporaryExclusionFixture.input().put("evidence",text);
        assertThatThrownBy(()->TemporaryExclusionShape.validate(input)).isInstanceOf(RuntimeException.class);
    }
    @Test void unrelatedReviewCannotSmuggleAWithdrawalReference() {
        var input=TemporaryExclusionFixture.input().put("withdrawalRequestId",java.util.UUID.randomUUID().toString());
        assertThatThrownBy(()->TemporaryExclusionShape.validate(input)).isInstanceOf(RuntimeException.class);
    }
}
