package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class RelayAssociationConflictPolicyTest {
    private ObjectNode proof(RelayConflictFixture f) {
        var row=f.row();var result=object();result.set("evidence",row.request().deepCopy());result.set("originalContext",row.context().deepCopy());return result;
    }
    private void complete(RelayConflictFixture f,ObjectNode proof) {
        RelayAssociationConflictPolicy.complete(f.selected.context,proof,f.selected.supervisor,RelayPhysicalFixture.NOW);
    }
    @Test void exactAllPartyDecisionCanBeConsumedWithoutChangingOriginalFacts() {
        var f=new RelayConflictFixture();var original=f.selected.context.deepCopy();var decision=proof(f);var before=decision.deepCopy();
        assertThatCode(()->complete(f,decision)).doesNotThrowAnyException();
        assertThat(f.selected.context).isEqualTo(original);assertThat(decision).isEqualTo(before);
    }
    @Test void aConflictCannotBeResolvedByOnlyAnAssociationOrPhysicalRecord() {
        var f=new RelayConflictFixture();assertThatThrownBy(()->complete(f,null)).isInstanceOf(BusinessException.class);
    }
    @Test void unrelatedDecisionCannotBeAttachedToAnUnconflictedTask() {
        var f=new RelayConflictFixture();var decision=proof(f);((ArrayNode)f.selected.context.path("peers")).removeAll();
        assertThatThrownBy(()->complete(f,decision)).isInstanceOf(BusinessException.class);
        assertThatCode(()->complete(f,null)).doesNotThrowAnyException();
    }
    @Test void laterInventoryOrOriginalOrCandidateFactsRequireFreshCompleteEvidence() {
        var f=new RelayConflictFixture();var decision=proof(f);f.selected.context.put("newFact",true);
        assertThatThrownBy(()->complete(f,decision)).isInstanceOf(BusinessException.class);
    }
    @Test void addedThirdPartyCannotInheritATwoPartyDecision() {
        var f=new RelayConflictFixture();var decision=proof(f);((ArrayNode)f.selected.context.path("peers")).add(f.group.deepCopy().put("taskId",java.util.UUID.randomUUID().toString()));
        f.refresh();assertThatThrownBy(()->complete(f,decision)).isInstanceOf(BusinessException.class);
    }
    @Test void supervisorCannotConsumeAValidProofForTheirOwnPeerTask() {
        var f=new RelayConflictFixture();((ObjectNode)f.peer.context.path("original")).put("operatorId",f.selected.supervisor.toString());f.refresh();
        var decision=proof(f);assertThatThrownBy(()->complete(f,decision)).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"LOADED","NOT_FOUND"})
    void alteredOtherPartyPhysicalObservationCannotTransferOwnership(String status) {
        var f=new RelayConflictFixture();var decision=proof(f);
        ((ObjectNode)decision.path("originalContext").path("peers").get(0).path("physical").path("evidence").path("packages").get(0)).put("status",status);
        assertThatThrownBy(()->complete(f,decision)).isInstanceOf(BusinessException.class);
    }
    @Test void everyPeerAndEveryCycleClaimMustRemainPresent() {
        var f=new RelayConflictFixture();var decision=proof(f);((ArrayNode)decision.path("originalContext").path("claims")).removeAll();
        assertThatThrownBy(()->complete(f,decision)).isInstanceOf(BusinessException.class);
        var other=proof(f);((ArrayNode)other.path("originalContext").path("peers")).removeAll();
        assertThatThrownBy(()->complete(f,other)).isInstanceOf(BusinessException.class);
    }
    @Test void decisionDoesNotOverrideClosureDivergenceOrPhysicalExpiry() {
        var f=new RelayConflictFixture();f.group.put("closureDivergence",true);f.refresh();
        assertThatThrownBy(()->complete(f,proof(f))).isInstanceOf(BusinessException.class);
        var other=new RelayConflictFixture();var decision=proof(other);
        assertThatThrownBy(()->RelayAssociationConflictPolicy.complete(other.selected.context,decision,other.selected.supervisor,RelayPhysicalFixture.NOW.plusSeconds(86401)))
            .isInstanceOf(BusinessException.class);
    }
    @Test void optionalDecisionIdentifierMustBeCanonicalAndCannotGrantCapability() {
        var f=new RelayAssociationFixture();f.request.put("conflictDecisionId",java.util.UUID.randomUUID().toString());
        assertThatCode(()->RelayAssociationPolicy.validate(f.request)).doesNotThrowAnyException();
        f.request.put("conflictDecisionId","bad-id");assertThatThrownBy(()->RelayAssociationPolicy.validate(f.request)).isInstanceOf(BusinessException.class);
    }
}
