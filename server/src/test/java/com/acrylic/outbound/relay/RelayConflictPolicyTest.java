package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RelayConflictPolicyTest {
    @Test void selectedLoadedAndOtherUnloadedMayRecordOwnershipWithoutChangingEitherOriginal() {
        var f=new RelayConflictFixture();var before=f.selected.context.deepCopy();var peer=f.peer.context.deepCopy();f.complete();
        assertThat(f.selected.context).isEqualTo(before);assertThat(f.peer.context).isEqualTo(peer);
        assertThat(RelayConflictPolicy.claims(f.selected.context,f.physical(f.selected),f.peers())).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings={"LOADED","NOT_FOUND"})
    void anotherTaskStillLoadedOrOnlyMissingDoesNotResolvePhysicalOverlap(String status) {
        var f=new RelayConflictFixture();f.peer.observation().put("status",status);assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void selectedTaskMustActuallyHoldTheRepeatedPackage() {
        var f=new RelayConflictFixture();f.selected.observation().put("status","UNLOADED").put("note","已卸");assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void missingUnloadedReasonCannotBeReplacedBySupervisorApproval() {
        var f=new RelayConflictFixture();f.peer.observation().put("note","");assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void everyConflictingTaskMustBePresentExactlyOnce() {
        var f=new RelayConflictFixture();((ArrayNode)f.request.path("peerChecks")).removeAll();assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
        var other=new RelayConflictFixture();var rows=(ArrayNode)other.request.path("peerChecks");rows.add(rows.get(0).deepCopy());
        assertThatThrownBy(other::complete).isInstanceOf(BusinessException.class);
    }
    @Test void untrustedOrUnclosedPeerCannotBeIgnored() {
        var f=new RelayConflictFixture();((ObjectNode)f.group.path("originals").get(0)).put("sourceTrust","UNVERIFIED");f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
        var other=new RelayConflictFixture();((ObjectNode)other.group.path("originals").get(0)).put("completeBoundaryVerified",false);other.refresh();
        assertThatThrownBy(other::complete).isInstanceOf(BusinessException.class);
    }
    @Test void differentSignedClosuresNeedTheirOwnInvestigation() {
        var f=new RelayConflictFixture();f.group.put("closureDivergence",true);f.refresh();assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void staleCompleteContextOrPhysicalRecordCannotBeRebound() {
        var f=new RelayConflictFixture();f.selected.context.put("changed",true);assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
        var other=new RelayConflictFixture();var physical=other.physical(other.peer);((ObjectNode)physical.path("originalContext")).put("hiddenChange",true);
        assertThatThrownBy(()->RelayConflictPolicy.check(other.peer.context,physical,new RelayConflictPolicy.Reviewer(other.selected.supervisor,RelayPhysicalFixture.NOW)))
            .isInstanceOf(BusinessException.class);
    }
    @Test void supervisorMustBeIndependentOfEveryCollector() {
        var f=new RelayConflictFixture();((ObjectNode)f.peer.context.path("original")).put("operatorId",f.selected.supervisor.toString());f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void liveStockBlockerCannotBeClearedByAConflictDecision() {
        var f=new RelayConflictFixture();((ArrayNode)f.selected.context.path("inventory").get(0).path("blockers")).add("COMMITMENT_EXISTS");f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void selectedPeerMustMatchBothDeviceAndTaskIdentity() {
        var f=new RelayConflictFixture();f.group.put("taskId",java.util.UUID.randomUUID().toString());f.refresh();assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void responseCapabilitiesAndUnknownRequestFieldsAreNotInputs() {
        var f=new RelayConflictFixture();f.request.put("releaseAllowed",true);assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
}
