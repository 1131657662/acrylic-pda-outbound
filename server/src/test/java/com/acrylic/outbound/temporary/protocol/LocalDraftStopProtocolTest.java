package com.acrylic.outbound.temporary.protocol;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class LocalDraftStopProtocolTest {
    @Test void verifiesActualAndroidRoomStopExportWithoutManufacturingSubmission() throws Exception {
        var v = new LocalDraftStopVector();
        var result = LocalDraftStopProtocol.verify(v.proofBytes(), v.metadataBytes(), v.expected);
        assertThat(result.evidenceId()).isEqualTo(v.expected.evidenceId());
        assertThat(result.clientOrderId()).isEqualTo(v.expected.clientOrderId());
        assertThat(result.rootSha256()).isEqualTo(v.core().path("rootSha256").asText());
        assertThat(result.captureHighWatermark()).isEqualTo(1);
        assertThat(result.auditThroughSeq()).isGreaterThan(0);
        assertThat(result.recordCount()).isGreaterThan(1);
        assertThat(v.core().has("submissionId")).isFalse();
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4,5,6,7})
    void rejectsWrongTrustedSourceContext(int field) throws Exception {
        var v = new LocalDraftStopVector();
        assertThatThrownBy(() -> LocalDraftStopProtocol.verify(v.proofBytes(), v.metadataBytes(), v.wrongContext(field)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void refusesSelfReportedOrSubstitutedKey() throws Exception {
        var v = new LocalDraftStopVector();
        assertThatThrownBy(() -> LocalDraftStopProtocol.verify(v.proofBytes(), v.metadataBytes(), v.mutableExpected()))
            .isInstanceOf(IllegalArgumentException.class);
        v.proof.put("publicKey", "a self-reported key is not trust"); rejects(v);
    }

    @Test void rejectsExistingSubmissionStopDomain() throws Exception {
        var v = new LocalDraftStopVector(); v.sign(v.mutableKey, "ACRYLIC-R06/E2A/STOP/1\n"); rejectsSigned(v);
    }

    @Test void rejectsNonP256EvenWhenSignatureAndKeyIdMatch() throws Exception {
        var v = new LocalDraftStopVector(); var key = LocalDraftStopVector.keys("secp384r1"); v.sign(key, LocalDraftStopProtocol.DOMAIN);
        assertThatThrownBy(() -> LocalDraftStopProtocol.verify(v.proofBytes(), v.metadataBytes(), v.expected(key.getPublic())))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"rootSha256", "metadataSha256", "creatorDeviceId", "stopEventId"})
    void rejectsChangedSignedCore(String field) throws Exception {
        var v = new LocalDraftStopVector(); v.core().put(field, field.endsWith("Sha256") ? "0".repeat(64) : java.util.UUID.randomUUID().toString()); rejects(v);
    }

    @ParameterizedTest @ValueSource(strings = {"captureHighWatermark", "auditThroughSeq", "sourceRecoveryEpoch", "recordCount", "byteCount", "stopTransactionVersion"})
    void rejectsNumericStringsEvenWhenResigned(String field) throws Exception {
        var v = new LocalDraftStopVector(); v.core().put(field, "1"); v.sign(); rejectsSigned(v);
    }

    @Test void rejectsInvalidBoundsEvenWhenResigned() throws Exception {
        for (String field : new String[]{"sourceRecoveryEpoch", "auditThroughSeq", "recordCount", "byteCount"}) {
            var v = new LocalDraftStopVector(); v.core().put(field, 0); v.sign(); rejectsSigned(v);
        }
        var v = new LocalDraftStopVector(); v.core().put("captureHighWatermark", -1); v.sign(); rejectsSigned(v);
        v = new LocalDraftStopVector(); v.core().put("recordCount", 250001); v.sign(); rejectsSigned(v);
        v = new LocalDraftStopVector(); v.core().put("byteCount", 134217729); v.sign(); rejectsSigned(v);
    }

    @Test void rejectsSubmissionFieldsEvenWithValidDraftDomainSignature() throws Exception {
        var v = new LocalDraftStopVector(); v.core().put("submissionId", java.util.UUID.randomUUID().toString()); v.sign(); rejectsSigned(v);
        v = new LocalDraftStopVector(); v.core().remove("stopEventId"); v.sign(); rejectsSigned(v);
    }

    @Test void rejectsRawMetadataChangeDespiteUnchangedProof() throws Exception {
        var v = new LocalDraftStopVector(); v.metadata.put("preparationReceipt", "changed"); rejects(v);
    }

    @Test void rejectsSignedButInconsistentMetadata() throws Exception {
        var v = new LocalDraftStopVector(); v.metadata.put("captureHighWatermark", "2"); v.bindMetadata(); v.sign(); rejectsSigned(v);
        v = new LocalDraftStopVector(); v.metadata.withObject("/draft").put("warehouseId", java.util.UUID.randomUUID().toString());
        v.bindMetadata(); v.sign(); rejectsSigned(v);
        v = new LocalDraftStopVector(); v.metadata.withObject("/draft").put("state", "FROZEN"); v.bindMetadata(); v.sign(); rejectsSigned(v);
        v = new LocalDraftStopVector(); v.metadata.put("localDeletionAllowed", true); v.bindMetadata(); v.sign(); rejectsSigned(v);
    }

    @Test void rejectsWrongEnvelopeTypesAndMalformedDer() throws Exception {
        var v = new LocalDraftStopVector(); v.proof.put("schemaVersion", "1"); rejects(v);
        v = new LocalDraftStopVector(); v.proof.put("signature", "AA=="); rejects(v);
        v = new LocalDraftStopVector(); v.proof.put("algorithm", "none"); rejects(v);
        v = new LocalDraftStopVector(); v.proof.put("signature", v.proof.path("signature").asText() + "="); rejects(v);
    }

    @Test void rejectsNoncanonicalOrAmbiguousWireBytes() throws Exception {
        var v = new LocalDraftStopVector(); String proof = new String(v.proofBytes(), StandardCharsets.UTF_8);
        for (String altered : new String[]{" " + proof, "\uFEFF" + proof, proof + "{}", proof.replace("{", "{\"schemaVersion\":1,")}) {
            assertThatThrownBy(() -> LocalDraftStopProtocol.verify(altered.getBytes(StandardCharsets.UTF_8), v.metadataBytes(), v.expected))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> LocalDraftStopProtocol.verify(new byte[]{(byte)0xc0, (byte)0x80}, v.metadataBytes(), v.expected))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LocalDraftStopProtocol.verify(v.proofBytes(), new byte[131073], v.expected))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static void rejects(LocalDraftStopVector v) {
        assertThatThrownBy(() -> LocalDraftStopProtocol.verify(v.proofBytes(), v.metadataBytes(), v.expected)).isInstanceOf(IllegalArgumentException.class);
    }
    private static void rejectsSigned(LocalDraftStopVector v) {
        assertThatThrownBy(() -> LocalDraftStopProtocol.verify(v.proofBytes(), v.metadataBytes(), v.mutableExpected())).isInstanceOf(IllegalArgumentException.class);
    }
}
