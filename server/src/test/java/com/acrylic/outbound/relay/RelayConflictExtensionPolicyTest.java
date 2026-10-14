package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class RelayConflictExtensionPolicyTest {
    private RelayConflictStore.Row previous() {
        var row=new RelayConflictFixture().row();
        return new RelayConflictStore.Row(row.id(),row.scope(),row.request(),row.context(),object().put("decisionId",row.id().toString()),row.at());
    }
    private ObjectNode enlarged(RelayConflictStore.Row row) {
        var next=(ObjectNode)row.context().deepCopy();
        ((ObjectNode)next.path("selected").path("context")).put("newTrustedPeer",true);
        ((ArrayNode)next.path("peers")).add(next.path("peers").get(0).deepCopy());return next;
    }
    @Test void newAllPartyEvidenceRetainsOriginalOwnerClaimsAndOriginals() {
        var prior=previous();var original=prior.context().deepCopy();var next=enlarged(prior);
        assertThatCode(()->RelayConflictExtensionPolicy.complete(next,prior)).doesNotThrowAnyException();
        assertThat(prior.context()).isEqualTo(original);
    }
    @Test void sameFactsRequireOriginalKeyInsteadOfAnotherDecision() {
        var prior=previous();assertThatThrownBy(()->RelayConflictExtensionPolicy.complete(prior.context(),prior)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"relayId","deviceId","taskId","closureSha256"})
    void selectedOriginalCannotBeChanged(String field) {
        var prior=previous();var next=enlarged(prior);((ObjectNode)next.path("selected").path("context").path("original")).put(field,"changed");
        assertThatThrownBy(()->RelayConflictExtensionPolicy.complete(next,prior)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"claims","peers"})
    void previousClaimsAndAllPartyOriginalsCannotBeRemoved(String field) {
        var prior=previous();var next=enlarged(prior);((ArrayNode)next.path(field)).removeAll();
        assertThatThrownBy(()->RelayConflictExtensionPolicy.complete(next,prior)).isInstanceOf(BusinessException.class);
    }
    @Test void aNewCycleDoesNotReplaceAnOldCycleClaim() {
        var prior=previous();var next=enlarged(prior);((ObjectNode)next.path("claims").get(0)).put("dispatchCycle","2");
        assertThatThrownBy(()->RelayConflictExtensionPolicy.complete(next,prior)).isInstanceOf(BusinessException.class);
    }
    @Test void requestRelationalPredecessorAndFullOriginalReceiptMustMatch() {
        var prior=previous();var next=enlarged(prior);next.set("predecessorReceipt",prior.receipt().deepCopy());
        var body=prior.request().deepCopy();((ObjectNode)body).put("predecessorDecisionId",prior.id().toString());
        var row=new RelayConflictStore.Row(UUID.randomUUID(),prior.scope(),body,next,null,prior.at());
        assertThatCode(()->RelayConflictExtensionPolicy.reference(row,prior)).doesNotThrowAnyException();
        ((ObjectNode)next.path("predecessorReceipt")).put("decisionId",UUID.randomUUID().toString());
        assertThatThrownBy(()->RelayConflictExtensionPolicy.reference(row,prior)).isInstanceOf(BusinessException.class);
    }
    @Test void optionalPredecessorMustBeACanonicalIdentifier() {
        var request=new RelayConflictFixture().request;request.put("predecessorDecisionId",UUID.randomUUID().toString());
        assertThatCode(()->RelayConflictPolicy.validate(request)).doesNotThrowAnyException();request.put("predecessorDecisionId"," ");
        assertThatThrownBy(()->RelayConflictPolicy.validate(request)).isInstanceOf(BusinessException.class);
    }
}
