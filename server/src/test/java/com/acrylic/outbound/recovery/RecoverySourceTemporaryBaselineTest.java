package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourceTemporaryBaselineTest {
    @Test void provesOriginalNonemptyPolicyRulesMappingsAndPlanWithoutNewAccess() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture();

        var proof = f.reader().verify(f.prep.read());

        assertThat(proof.planSha256()).isEqualTo(f.prep.state.payload.path("planSha256").asText());
        assertThat(f.prep.read().ordinaryWritesAllowed()).isFalse(); assertThat(f.prep.read().localDeletionAllowed()).isFalse();
    }

    @Test void provesBothOriginalDeductionModes() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.policy.put("autoDeductInventory", true);
        f.prep.state.payload.put("autoDeductInventory", true); f.publish();

        assertThat(f.reader().verify(f.prep.read()).preparation().partHashes()).containsKey("policy");
    }

    @Test void refusesDeductionChangedOnlyInFrozenPayload() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.prep.state.payload.put("autoDeductInventory", true); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_POLICY_DEDUCTION");
    }

    @ParameterizedTest @ValueSource(strings = {"policy", "payload"})
    void refusesDifferentPolicyVersion(String location) throws Exception {
        var f = new RecoveryTemporaryBaselineFixture();
        (location.equals("policy") ? f.policy : f.prep.state.payload).put(location.equals("policy") ? "version" : "policyVersion", "2"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_POLICY_VERSION");
    }

    @ParameterizedTest @ValueSource(strings = {"shortShipmentPolicy", "concurrencyMode", "scanMode"})
    void refusesUnsupportedPolicyModesDespiteIssuerSignature(String field) throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.policy.put(field, "ALLOW_ALL"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_POLICY_MODE");
    }

    @ParameterizedTest @ValueSource(strings = {"schemaVersion", "offlineHours"})
    void refusesUnsupportedPolicyNumbers(String field) throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.policy.put(field, 99); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_POLICY_MODE");
    }

    @ParameterizedTest @ValueSource(strings = {"barcodePattern", "weightUnit", "scanMode"})
    void refusesRulesThatChangeOriginalParsing(String field) throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.rules.put(field, "OTHER"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_RULES");
    }

    @Test void refusesAvailabilityCursorFromAnotherWarehouse() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); ((ObjectNode)f.prep.content.get("availability")).put("nextCursor", "another"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_AVAILABILITY_CURSOR");
    }

    @Test void refusesInventoryDecimalWeightInconsistentWithGrams() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.inventory().put("rawWeight", "2.345");
        ((ObjectNode)f.prep.state.payload.path("scans").get(0)).put("rawWeight", "2.345"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_INVENTORY_WEIGHT");
    }

    @Test void refusesDisabledOriginalMapping() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.mapping.put("enabled", false); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_PLAN_MAPPING");
    }

    @Test void refusesDuplicateOriginalMapping() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); ((ArrayNode)f.prep.content.get("mapping")).add(f.mapping.deepCopy()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_MAPPING");
    }

    @ParameterizedTest @ValueSource(strings = {"productId", "model", "name"})
    void refusesPlanProductsDifferentFromOriginalMapping(String field) throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.line().put(field, field.equals("productId") ? UUID.randomUUID().toString() : "Other"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_PLAN_PRODUCT");
    }

    @Test void refusesPlanHashChangedOnlyInPayload() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.prep.state.payload.put("planSha256", "a".repeat(64)); f.prep.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_PLAN_HASH");
    }

    @Test void refusesForeignOriginalLocalPlanIdentity() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.plan.put("clientOrderId", UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_PLAN_SOURCE");
    }

    @Test void refusesPlanMissingAScannedProduct() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.line().put("deliveryCode", "999"); f.mapping.put("deliveryCode", "999"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_PLAN_SCAN");
    }

    @Test void refusesInventoryAndScanProductThatDifferFromTheOriginalPlan() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.inventory().put("model", "Other");
        ((ObjectNode)f.prep.state.payload.path("scans").get(0)).put("model", "Other"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_PLAN_SCAN_PRODUCT");
    }

    @Test void refusesShortPlanWithoutOriginalFreezeReason() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.line().put("plannedCount", "2");
        f.prep.state.payload.put("shortShipmentReason", ""); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_PLAN_TEXT");
    }

    @Test void acceptsOriginalShortPlanReasonWithoutGrantingApproval() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.line().put("plannedCount", "2");
        f.prep.state.payload.put("shortShipmentReason", "One bale unavailable"); f.publish();

        assertThat(f.reader().verify(f.prep.read()).planSha256()).isNotBlank();
        assertThat(f.prep.read().ordinaryWritesAllowed()).isFalse();
    }

    @Test void refusesMoreActiveBalesThanOriginalProductPlan() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.prep.state.secondScan();
        ((ObjectNode)f.prep.state.payload.path("captures").get(1)).put("snapshotId", f.prep.manifest.path("inventorySnapshotId").asText())
            .put("orderRevision", "1");
        var scans = f.prep.state.payload.path("scans");
        ((ObjectNode)scans.get(1)).put("deliveryCode", "012").put("model", "MODEL").put("name", "Product")
            .put("rawWeight", "1.234").put("weightGrams", "1234");
        ((ArrayNode)f.prep.content.get("inventory")).add(f.inventory().deepCopy().put("barcode", "01234567891"));
        var availability = (ArrayNode)f.prep.content.get("availability").path("items");
        availability.add(((ObjectNode)availability.get(0)).deepCopy().put("barcode", "01234567891").put("baleId", scans.get(1).path("baleId").asText()));
        f.prep.state.payload.put("expectedCount", "2").put("expectedWeightGrams", "2468"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_PLAN_OVERLOAD");
    }

    @Test void refusesDifferentNativeInitialCredentialDuration() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture(); f.prep.manifest.put("expiresAt", "2026-09-21T00:00:00Z");
        f.prep.state.authority.claims.put("exp", java.time.Instant.parse("2026-09-21T00:00:00Z").getEpochSecond()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_BASELINE_WINDOW");
    }

    @Test void refusesOriginalCaptureFromAnotherSnapshot() throws Exception {
        var f = new RecoveryTemporaryBaselineFixture();
        ((ObjectNode)f.prep.state.payload.path("captures").get(0)).put("snapshotId", UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.prep.read())).hasMessage("RECOVERY_TEMP_BASELINE_CAPTURE");
    }
}
