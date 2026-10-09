package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryPreparationPartsTest {
    @Test void refusesMissingNativePart() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); ((ObjectNode)f.origin.path("parts")).remove("policy"); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("CONTROL_FIELDS");
    }

    @Test void refusesRepeatedManifestDescriptor() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); ((ArrayNode)f.manifest.path("parts")).add(f.descriptor("inventory").deepCopy()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_PART_SET");
    }

    @Test void refusesUndeclaredManifestPart() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); ((ArrayNode)f.manifest.path("parts")).remove(0); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_PART_SET");
    }

    @ParameterizedTest @ValueSource(strings = {"compressedBytes", "bytes"})
    void refusesMismatchedByteCount(String field) throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); f.descriptor("inventory").put(field, 1); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_SIZE");
    }

    @Test void refusesOversizedDeclaredOutputBeforeDecompressing() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); f.descriptor("inventory").put("bytes", 16777217); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_SIZE");
    }

    @ParameterizedTest @ValueSource(strings = {"compressedSha256", "sha256"})
    void refusesCorruptDescriptorHash(String field) throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); f.descriptor("inventory").put(field, "a".repeat(64)); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage(field.equals("sha256")
            ? "RECOVERY_SOURCE_PREPARATION_RAW_HASH" : "RECOVERY_SOURCE_PREPARATION_COMPRESSED_HASH");
    }

    @Test void refusesCorruptedNativeCompressedBytes() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); String encoded = f.origin.at("/parts/inventory").asText();
        byte[] bytes = java.util.Base64.getDecoder().decode(encoded); bytes[bytes.length - 1] ^= 1;
        ((ObjectNode)f.origin.path("parts")).put("inventory", java.util.Base64.getEncoder().encodeToString(bytes)); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_COMPRESSED_HASH");
    }

    @Test void refusesGzipCorruptionEvenIfCompressedHashWasUpdated() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); byte[] bytes = java.util.Base64.getDecoder().decode(f.origin.at("/parts/inventory").asText());
        bytes[0] = 0; ((ObjectNode)f.origin.path("parts")).put("inventory", java.util.Base64.getEncoder().encodeToString(bytes));
        f.descriptor("inventory").put("compressedSha256", RecoveryControlFormat.sha256(bytes)); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_GZIP");
    }

    @Test void refusesWrongDescriptorRowCount() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); f.descriptor("inventory").put("rows", 0); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_ROWS");
    }

    @Test void retainsTemporaryAvailabilityForHistoricBalesOutsideTheFixedInventory() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP");
        ((ArrayNode)f.content.get("availability").path("items")).addObject().put("baleId", UUID.randomUUID().toString())
            .put("barcode", "99999999999").put("status", "DISPATCHED").put("dispatchCycle", "1").put("rowVersion", "1");
        f.repack(); f.publish();

        assertThat(f.reader().verify(f.read()).partHashes()).containsKey("availability");
    }

    @Test void refusesDuplicateBaleUnderAnotherAvailabilityBarcode() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); var rows = (ArrayNode)f.content.get("availability").path("items");
        rows.add(((ObjectNode)rows.get(0)).deepCopy().put("barcode", "99999999999")); f.repack(); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_BALE_DUPLICATE");
    }

    @Test void refusesSnapshotChangedOnlyInTheFrozenPayload() throws Exception {
        var f = new RecoveryPreparationFixture("FORMAL"); f.state.payload.put("inventorySnapshotId", UUID.randomUUID().toString()); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_PAYLOAD_BASELINE");
    }

    @Test void refusesDifferentPayloadTotal() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); f.state.payload.put("expectedWeightGrams", "99"); f.publish();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_TOTAL");
    }

    @Test void refusesAnOriginEnvelopeWithUnknownFields() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); f.origin.put("currentAccessAllowed", true); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("CONTROL_FIELDS");
    }

    @Test void refusesPreparationCredentialDifferentFromThePinnedOriginToken() throws Exception {
        var f = new RecoveryPreparationFixture("TEMP"); f.receipt.put("offlineToken", "other"); f.write();

        assertThatThrownBy(() -> f.reader().verify(f.read())).hasMessage("RECOVERY_SOURCE_PREPARATION_TOKEN");
    }
}
