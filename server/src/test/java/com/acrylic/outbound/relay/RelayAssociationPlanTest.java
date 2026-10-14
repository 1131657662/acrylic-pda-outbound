package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RelayAssociationPlanTest {
    private RelayAssociationFixture shortfall() {
        var f=new RelayAssociationFixture();
        ((ObjectNode)f.context.path("original").path("closure")).put("plannedTotal","3").put("shortfall","2").put("shortfallReason","原独立计划短装");
        f.candidate().put("plannedTotal","2");((ObjectNode)f.candidate().path("products").get(0)).put("plannedCount","2");f.refresh();return f;
    }
    private void approve(RelayAssociationFixture f) {
        f.request.putObject("shortfallApproval").put("originalShortfall","2").put("targetShortfall","1").put("reason","独立主管逐包核对两种差额");
    }
    @Test void explicitApprovalPreservesBothOriginalAndTargetShortages() {
        var f=shortfall();approve(f);var before=f.context.deepCopy();f.complete();assertThat(f.context).isEqualTo(before);
    }
    @Test void originalShortfallCannotHideBehindAFullTargetPlan() {
        var f=shortfall();f.candidate().put("plannedTotal","1");((ObjectNode)f.candidate().path("products").get(0)).put("plannedCount","1");f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
        approve(f);((ObjectNode)f.request.path("shortfallApproval")).put("targetShortfall","0");assertThatCode(f::complete).doesNotThrowAnyException();
    }
    @Test void omittedApprovalRefusesShortfall() { assertThatThrownBy(shortfall()::complete).isInstanceOf(BusinessException.class); }
    @ParameterizedTest @ValueSource(strings={"originalShortfall","targetShortfall"})
    void inaccurateApprovalRefusesChangedFacts(String field) {
        var f=shortfall();approve(f);((ObjectNode)f.request.path("shortfallApproval")).put(field,"0");
        assertThatThrownBy(f::complete).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("INDEPENDENT_REVIEW_CONTEXT_CHANGED"));
    }
    @Test void fullTaskCannotAddUnrelatedApproval() {
        var f=new RelayAssociationFixture();approve(f);assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings={"-1","01","100001","1.0"})
    void shortageCountsMustBeExactBoundedIntegers(String count) {
        var f=shortfall();approve(f);((ObjectNode)f.request.path("shortfallApproval")).put("originalShortfall",count);
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
    }
    @Test void approvalCannotIgnoreStockOrPhysicalConflict() {
        var f=shortfall();approve(f);((ArrayNode)f.context.path("inventory").get(0).path("blockers")).add("COMMITMENT_EXISTS");f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
        var other=shortfall();approve(other);other.physical.observation().put("status","NOT_FOUND").put("note","仍需核原");
        assertThatThrownBy(other::complete).isInstanceOf(BusinessException.class);
    }
    @Test void otherProductsAndOverfillCannotUseShortfallApproval() {
        var f=shortfall();approve(f);((ObjectNode)f.candidate().path("products").get(0)).put("model","OTHER");f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
        var other=shortfall();approve(other);((ObjectNode)other.candidate().path("products").get(0)).put("plannedCount","0");other.refresh();
        assertThatThrownBy(other::complete).isInstanceOf(BusinessException.class);
    }
    @Test void inconsistentOriginalClosureAndTargetTotalAreRefused() {
        var f=shortfall();approve(f);((ObjectNode)f.context.path("original").path("closure")).put("active","2");f.refresh();
        assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
        var other=shortfall();approve(other);other.candidate().put("plannedTotal","3");other.refresh();assertThatThrownBy(other::complete).isInstanceOf(BusinessException.class);
    }
    @Test void approvalRequiresAnExplanationAndNoHiddenCapabilities() {
        var f=shortfall();approve(f);((ObjectNode)f.request.path("shortfallApproval")).put("reason"," ");assertThatThrownBy(f::complete).isInstanceOf(BusinessException.class);
        var other=shortfall();approve(other);((ObjectNode)other.request.path("shortfallApproval")).put("releaseAllowed",true);assertThatThrownBy(other::complete).isInstanceOf(BusinessException.class);
    }
    @Test void dedicatedGateDefaultsOffButDoesNotBlockOrdinaryRequests() {
        var gate=new RelayAssociationShortfall(false);var f=shortfall();approve(f);
        assertThatThrownBy(()->gate.require(f.request)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("INDEPENDENT_SHORTFALL_DISABLED"));
        assertThatCode(()->gate.require(new RelayAssociationFixture().request)).doesNotThrowAnyException();
        assertThatCode(()->new RelayAssociationShortfall(true).require(f.request)).doesNotThrowAnyException();
    }
}
