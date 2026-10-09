package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoverySourcePreparationTest {
    @ParameterizedTest @ValueSource(strings = {"FORMAL", "TEMP"})
    void provesNativeOriginalPreparationAgainstOriginalScanWithoutAccessGrant(String kind) throws Exception {
        var f = new RecoveryPreparationFixture(kind); var source = f.read();

        var proof = f.reader().verify(source);

        assertThat(proof.manifestSha256()).isEqualTo(f.receipt.path("manifestSha256").asText());
        assertThat(proof.partHashes()).hasSize(kind.equals("FORMAL") ? 3 : 5);
        assertThat(source.ordinaryWritesAllowed()).isFalse(); assertThat(source.localDeletionAllowed()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"assignmentId", "orderId", "deviceId", "operatorId"})
    void refusesForeignFormalManifestDespiteValidSignature(String field) throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); f.manifest.put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_IDENTITY");
    }

    @ParameterizedTest @ValueSource(strings = {"warehouseId", "deviceId", "userId", "authorizationId"})
    void refusesForeignTemporaryManifestDespiteValidSignature(String field) throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); ((ObjectNode)f.manifest.path("identity")).put(field, UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_IDENTITY");
    }

    @Test void refusesRawManifestBytesChangedAfterSigning() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); f.receipt.put("manifest", f.receipt.path("manifest").asText() + " "); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_MANIFEST_HASH");
    }

    @Test void refusesAnotherSignedManifestReceipt() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); var other = new RecoveryPreparationFixture("FORMAL");
        f.receipt.put("signedManifest", other.receipt.path("signedManifest").asText()); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_RECEIPT_KEY_UNKNOWN");
    }

    @Test void refusesManifestSignedWithTheWrongPurpose() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); var proof = com.acrylic.outbound.submissions.SubmissionJson.object()
            .put("iss", "acrylic-outbound").put("aud", "acrylic-pda-receipt").put("manifestSha256", f.receipt.path("manifestSha256").asText());
        var header = com.acrylic.outbound.submissions.SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", f.state.authority.key.kid);
        f.receipt.put("signedManifest", f.state.authority.key.token(header, proof)); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_INVALID");
    }

    @ParameterizedTest @ValueSource(strings = {"inventorySnapshotId", "assignmentEpoch", "revision"})
    void refusesAnotherFormalBaseline(String field) throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL");
        if (field.equals("revision")) f.manifest.put(field, 9); else f.manifest.put(field, field.endsWith("Id") ? UUID.randomUUID().toString() : "9"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_BASELINE");
    }

    @Test void refusesPreparationCapturedAfterOriginalConfirmation() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); f.manifest.put("stateCapturedAt", "2026-09-20T00:30:01Z"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_CAPTURE_TIME");
    }

    @Test void refusesAnotherOriginalCycle() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); ((ObjectNode)f.content.get("availability").path("items").get(0)).put("dispatchCycle", "9");
        f.repack(); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_CYCLE");
    }

    @ParameterizedTest @ValueSource(strings = {"deliveryCode", "model", "name", "rawWeight", "weightGrams"})
    void refusesOriginalScanWithAnotherInventoryField(String field) throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); ((ObjectNode)f.state.payload.path("scans").get(0)).put(field, "99"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_SCAN_BASELINE");
    }

    @Test void refusesProductOutsideTheSignedOriginalPlan() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); ((ObjectNode)f.content.get("plan").at("/order/lines/0")).put("deliveryCode", "999");
        f.repack(); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_PLAN");
    }

    @Test void refusesDuplicateInventoryBarcodes() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); var rows = (ArrayNode)f.content.get("inventory"); rows.add(rows.get(0).deepCopy());
        f.repack(); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_BARCODE");
    }

    @Test void refusesMissingAvailabilityRow() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); ((ArrayNode)f.content.get("availability")).removeAll(); f.repack(); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_ROW_SET");
    }

    @Test void refusesInitialStockUnavailableAtPreparation() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); ((ObjectNode)f.content.get("availability").path("items").get(0)).put("status", "DISPATCHED");
        f.repack(); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_AVAILABILITY");
    }

    @Test void acceptsNativeNanosecondPreparationCaptureTime() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); f.manifest.put("stateCapturedAt", "2026-09-20T00:01:00.123456789Z"); f.publish();

        assertThat(f.reader().verify(f.read()).partHashes()).containsKey("plan");
    }

    @Test void refusesRenewalWithoutTheOriginalPreparationCredentialChain() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL");
        f.state.authority.claims.put("renewedFromAuthorizationId", UUID.randomUUID().toString()).put("authorizationSequence", "2");
        f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_RENEWAL_UNSUPPORTED");
    }
}
