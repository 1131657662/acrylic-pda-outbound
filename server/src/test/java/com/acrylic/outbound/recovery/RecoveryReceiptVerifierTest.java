package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryReceiptVerifierTest {
    @Test void verifiesAnOriginalReceiptWithOnlyPublicDer() throws Exception {
        var key = new RecoveryReceiptFixture(); var f = RecoverySourceFixture.create();
        var signed = key.signed(key.receipt(f, "ABORTED"));

        assertThat(key.verifier.verify(RecoveryReceiptFixture.bytes(signed))).isEqualTo(signed);
    }

    @Test void acceptsAnExplicitHistoricalPublicKeyWithoutAnyPrivateKeyFile() throws Exception {
        var historical = new RecoveryReceiptFixture(); var current = new RecoveryReceiptFixture();
        var verifier = new RecoveryReceiptVerifier(new RecoveryReceiptKeys(List.of(
            current.pair.getPublic().getEncoded(), historical.pair.getPublic().getEncoded())));
        var f = RecoverySourceFixture.create(); var signed = historical.signed(historical.receipt(f, "SUCCEEDED"));

        assertThat(verifier.verify(RecoveryReceiptFixture.bytes(signed))).isEqualTo(signed);
    }

    @Test void rejectsUnknownHistoricalKeyInsteadOfTrustingTheTokenHeader() throws Exception {
        var key = new RecoveryReceiptFixture(); var different = new RecoveryReceiptFixture();
        var signed = key.signed(key.receipt(RecoverySourceFixture.create(), "ABORTED"));

        assertThatThrownBy(() -> different.verifier.verify(RecoveryReceiptFixture.bytes(signed)))
            .hasMessage("RECOVERY_RECEIPT_KEY_UNKNOWN");
    }

    @Test void rejectsUnsignedContentChangesEvenWithAnUnmodifiedValidSignature() throws Exception {
        var key = new RecoveryReceiptFixture(); var signed = key.signed(key.receipt(RecoverySourceFixture.create(), "ABORTED"));
        signed.put("status", "SUCCEEDED");

        assertThatThrownBy(() -> key.verifier.verify(RecoveryReceiptFixture.bytes(signed))).hasMessage("RECOVERY_RECEIPT_CONTENT");
    }

    @Test void rejectsCorruptSignature() throws Exception {
        var key = new RecoveryReceiptFixture(); var signed = key.signed(key.receipt(RecoverySourceFixture.create(), "ABORTED"));
        var parts = signed.path("signedReceipt").asText().split("\\.");
        byte[] signature = java.util.Base64.getUrlDecoder().decode(parts[2]); signature[0] ^= 1;
        signed.put("signedReceipt", parts[0] + "." + parts[1] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signature));

        assertThatThrownBy(() -> key.verifier.verify(RecoveryReceiptFixture.bytes(signed))).hasMessage("RECOVERY_RECEIPT_SIGNATURE");
    }

    @ParameterizedTest @ValueSource(strings = {"none", "HS256", "ES256"})
    void refusesAlgorithmConfusionEvenIfTheTokenHasARsaSignature(String algorithm) throws Exception {
        var key = new RecoveryReceiptFixture(); var receipt = key.receipt(RecoverySourceFixture.create(), "ABORTED");
        var claims = SubmissionJson.object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-receipt"); claims.set("receipt", receipt.deepCopy());
        var header = SubmissionJson.object().put("alg", algorithm).put("typ", "JWT").put("kid", key.kid);
        receipt.put("signedReceipt", key.token(header, claims));

        assertThatThrownBy(() -> key.verifier.verify(RecoveryReceiptFixture.bytes(receipt))).hasMessage("RECOVERY_RECEIPT_ALGORITHM");
    }

    @Test void refusesValidSignatureForTheArchiveAudience() throws Exception {
        var key = new RecoveryReceiptFixture(); var receipt = key.receipt(RecoverySourceFixture.create(), "ABORTED");
        var claims = SubmissionJson.object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-archive"); claims.set("receipt", receipt.deepCopy());
        var header = SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", key.kid);
        receipt.put("signedReceipt", key.token(header, claims));

        assertThatThrownBy(() -> key.verifier.verify(RecoveryReceiptFixture.bytes(receipt))).hasMessage("RECOVERY_RECEIPT_PURPOSE");
    }

    @Test void rejectsUnknownHeaderExtensions() throws Exception {
        var key = new RecoveryReceiptFixture(); var receipt = key.receipt(RecoverySourceFixture.create(), "ABORTED");
        var claims = SubmissionJson.object().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-receipt"); claims.set("receipt", receipt.deepCopy());
        var header = SubmissionJson.object().put("alg", "RS256").put("typ", "JWT").put("kid", key.kid).put("jku", "https://untrusted.invalid");
        receipt.put("signedReceipt", key.token(header, claims));

        assertThatThrownBy(() -> key.verifier.verify(RecoveryReceiptFixture.bytes(receipt))).hasMessage("CONTROL_FIELDS");
    }

    @Test void refusesEmptyDuplicateOrMalformedTrustedKeys() throws Exception {
        var key = new RecoveryReceiptFixture(); byte[] der = key.pair.getPublic().getEncoded();

        assertThatThrownBy(() -> new RecoveryReceiptKeys(List.of())).hasMessage("RECOVERY_RECEIPT_KEYS_REQUIRED");
        assertThatThrownBy(() -> new RecoveryReceiptKeys(List.of(der, der))).hasMessage("RECOVERY_RECEIPT_KEY_DUPLICATE");
        assertThatThrownBy(() -> new RecoveryReceiptKeys(List.of(new byte[]{1}))).hasMessage("RECOVERY_RECEIPT_KEY_INVALID");
    }
}
