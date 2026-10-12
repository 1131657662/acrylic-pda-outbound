package com.acrylic.outbound.temporary.command;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TemporaryExclusionPolicyTest {
    @Test void allowsSupervisorReviewedDifferentVehicleAndDifferentCompletePlan() {
        var body=TemporaryExclusionFixture.body();var exclusion=TemporaryExclusionFixture.exclusion(body);
        assertThatCode(()->TemporaryExclusionPolicy.validate(body,exclusion)).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={"activeAssignment","unresolvedPc","unresolvedSubmission","stockConflict","hasResult","openSlot"})
    void narrativeCannotExcludeWorkOrStockConflict(String field) {
        var body=TemporaryExclusionFixture.body();var exclusion=TemporaryExclusionFixture.exclusion(body);
        ((ObjectNode)exclusion.path("work")).put(field,true);
        assertThatThrownBy(()->TemporaryExclusionPolicy.validate(body,exclusion))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error->assertThat(error.code()).isEqualTo("TEMPORARY_EXCLUSION_WORK_CONFLICT"));
    }
    @Test void sameVehicleCannotBeDeclaredUnrelated() {
        var body=TemporaryExclusionFixture.body();var exclusion=TemporaryExclusionFixture.exclusion(body);
        ((ObjectNode)exclusion.at("/candidate/order")).put("vehicleNo",body.at("/temporaryOrder/vehicleNo").asText());
        assertThatThrownBy(()->TemporaryExclusionPolicy.validate(body,exclusion)).isInstanceOf(RuntimeException.class);
    }
    @Test void differentVehicleWithExactPlanStillRequiresDuplicateReview() {
        var body=TemporaryExclusionFixture.body();var exclusion=TemporaryExclusionFixture.exclusion(body);
        ((ObjectNode)exclusion.path("candidate")).set("products",body.at("/temporaryOrder/lines").deepCopy());
        assertThatThrownBy(()->TemporaryExclusionPolicy.validate(body,exclusion)).isInstanceOf(RuntimeException.class);
    }
    @Test void cancelledCandidateCanBeExcludedWithoutErasingHistoricalAssignment() {
        var body=TemporaryExclusionFixture.body();var exclusion=TemporaryExclusionFixture.exclusion(body);
        exclusion.put("basis","CANCELLED");((ObjectNode)exclusion.at("/candidate/order")).put("status","CANCELLED").put("hasAssignment",true);
        assertThatCode(()->TemporaryExclusionPolicy.validate(body,exclusion)).doesNotThrowAnyException();
    }
    @Test void readyCandidateIsNotInvalidMerelyBecauseSupervisorSaysCancelled() {
        var body=TemporaryExclusionFixture.body();var exclusion=TemporaryExclusionFixture.exclusion(body);
        exclusion.put("basis","CANCELLED");
        assertThatThrownBy(()->TemporaryExclusionPolicy.validate(body,exclusion)).isInstanceOf(RuntimeException.class);
    }
    @Test void withdrawalRequiresOriginalProofForTheExactRestartRevision() {
        var body=TemporaryExclusionFixture.body();var exclusion=TemporaryExclusionFixture.exclusion(body);
        exclusion.put("basis","WITHDRAWN").put("withdrawalRequestId",java.util.UUID.randomUUID().toString());
        ((ObjectNode)exclusion.at("/candidate/order")).put("status","DRAFT").put("revision",2);
        var proof=exclusion.putObject("withdrawalReceipt").put("status","WITHDRAWN").put("orderStatus","DRAFT").put("restartRevision",2);
        proof.set("orderId",exclusion.get("orderId"));proof.set("changeRequestId",exclusion.get("withdrawalRequestId"));
        assertThatCode(()->TemporaryExclusionPolicy.validate(body,exclusion)).doesNotThrowAnyException();
        proof.put("restartRevision",3);
        assertThatThrownBy(()->TemporaryExclusionPolicy.validate(body,exclusion)).isInstanceOf(RuntimeException.class);
    }
}
