package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RelayAssociationPolicyTest {
    @Test void fullLoadedTaskMayBeExplicitlyLinkedWithoutChangingOriginals() {
        var f=new RelayAssociationFixture();var before=f.context.deepCopy();f.complete();assertThat(f.context).isEqualTo(before);
    }
    @Test void remainingCandidatesMustEachBeExcludedWithReasons() {
        var f=new RelayAssociationFixture();UUID other=UUID.randomUUID();
        ((ArrayNode)f.context.path("candidates").path("items")).add(f.candidate().deepCopy().put("id",other.toString()));f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
        ((ArrayNode)f.request.path("candidateExclusions")).addObject().put("orderId",other.toString()).put("reason","核实为另一车次");
        assertThatCode(f::complete).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={"source","status","vehicleNo","inventorySnapshotId"})
    void anotherSourceOrVehicleOrUsedStateOrSnapshotCannotBeLinked(String field) {
        var f=new RelayAssociationFixture();f.candidate().put(field,"OTHER");f.refresh();assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void existingFormalWorkCannotBeHiddenByAnEmptyBlockerList() {
        var f=new RelayAssociationFixture();((ArrayNode)f.candidate().path("workFacts")).addObject().put("kind","ASSIGNMENT");
        f.refresh();assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"crossTaskConflict","closureDivergence"})
    void unresolvedPeersRequireTheirOwnDisposition(String field) {
        var f=new RelayAssociationFixture();((ArrayNode)f.context.path("peers")).addObject().put(field,true);f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void stockConflictCannotBeClearedByACompletePhysicalCheck() {
        var f=new RelayAssociationFixture();((ArrayNode)f.context.path("inventory").get(0).path("blockers")).add("COMMITMENT_EXISTS");f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void shortfallIsNotSilentlyAcceptedAsACompleteTargetPlan() {
        var f=new RelayAssociationFixture();((ObjectNode)f.candidate().path("products").get(0)).put("plannedCount","2");f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void aDifferentOriginalContextInvalidatesBothAssociationAndPhysicalIntent() {
        var f=new RelayAssociationFixture();f.context.put("laterFact",true);
        assertThatThrownBy(f::complete).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("INDEPENDENT_REVIEW_CONTEXT_CHANGED"));
    }
    @Test void collectorCannotApproveOwnAssociation() {
        var f=new RelayAssociationFixture();assertThatThrownBy(()->RelayAssociationPolicy.complete(f.request,f.context,f.physical.request,f.physical.collector,f.NOW))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("INDEPENDENT_ASSOCIATION_SELF_REVIEW"));
    }
    @Test void missingPhysicalPackagesCannotSatisfyTheLink() {
        var f=new RelayAssociationFixture();((ArrayNode)f.physical.request.path("packages")).removeAll();assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void requestCannotAddCapabilitiesOrReuseCandidateIdentifiers() {
        var f=new RelayAssociationFixture();f.request.put("releaseAllowed",true);assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
        var other=new RelayAssociationFixture();var rows=(ArrayNode)other.request.path("candidateExclusions");
        var row=rows.addObject().put("orderId",UUID.randomUUID().toString()).put("reason","合成排除");rows.add(row.deepCopy());
        assertThatThrownBy(other::complete).isInstanceOf(BusinessException.class);
    }
}
