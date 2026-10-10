package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryReleaseFenceSignatureTest {
    @Test void bindsOriginalHandoverReceiptToIndependentPublicKey() throws Exception {
        var key=new RecoveryReceiptFixture(); var receipt=signed(key,"RS256","acrylic-outbound-handover-receipt");
        var expected=receipt.deepCopy(); expected.remove("signedReceipt");
        assertThat(RecoveryReleaseFenceSignature.verify(RecoveryReceiptFixture.bytes(receipt),keys(key))).isEqualTo(expected);
    }
    @Test void rejectsUnknownKey() throws Exception {
        var key=new RecoveryReceiptFixture(); var other=new RecoveryReceiptFixture(); var receipt=signed(key,"RS256","acrylic-outbound-handover-receipt");
        assertThatThrownBy(()->RecoveryReleaseFenceSignature.verify(RecoveryReceiptFixture.bytes(receipt),keys(other))).hasMessage("RECOVERY_RECEIPT_KEY_UNKNOWN");
    }
    @Test void rejectsUnsignedChanges() throws Exception {
        var key=new RecoveryReceiptFixture(); var receipt=signed(key,"RS256","acrylic-outbound-handover-receipt"); receipt.put("status","RELEASED");
        assertThatThrownBy(()->RecoveryReleaseFenceSignature.verify(RecoveryReceiptFixture.bytes(receipt),keys(key))).hasMessage("RECOVERY_SCOPE_RECEIPT_CONTENT");
    }
    @Test void rejectsDifferentReceiptPurpose() throws Exception {
        var key=new RecoveryReceiptFixture(); var receipt=signed(key,"RS256","acrylic-pda-receipt");
        assertThatThrownBy(()->RecoveryReleaseFenceSignature.verify(RecoveryReceiptFixture.bytes(receipt),keys(key))).hasMessage("RECOVERY_SCOPE_RECEIPT_PURPOSE");
    }
    @Test void rejectsNoncanonicalOriginalBytes() throws Exception {
        var key=new RecoveryReceiptFixture(); var receipt=signed(key,"RS256","acrylic-outbound-handover-receipt");
        byte[] changed=(SubmissionJson.canonical(receipt)+" ").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(()->RecoveryReleaseFenceSignature.verify(changed,keys(key))).hasMessage("RECOVERY_SCOPE_RECEIPT_CANONICAL");
    }
    @ParameterizedTest @ValueSource(strings={"none","HS256","ES256"})
    void rejectsOtherAlgorithms(String algorithm) throws Exception {
        var key=new RecoveryReceiptFixture(); var receipt=signed(key,algorithm,"acrylic-outbound-handover-receipt");
        assertThatThrownBy(()->RecoveryReleaseFenceSignature.verify(RecoveryReceiptFixture.bytes(receipt),keys(key))).hasMessage("RECOVERY_SCOPE_RECEIPT_ALGORITHM");
    }
    private ObjectNode signed(RecoveryReceiptFixture key,String algorithm,String audience) throws Exception {
        var unsigned=SubmissionJson.object().put("status","RECORDED").put("commandType","FAULT_FENCE");
        var claims=SubmissionJson.object().put("iss","acrylic-outbound-server").put("aud",audience); claims.set("receipt",unsigned.deepCopy());
        var header=SubmissionJson.object().put("alg",algorithm).put("typ","JWT").put("kid",key.kid);
        return unsigned.put("signedReceipt",key.token(header,claims));
    }
    private RecoveryReceiptKeys keys(RecoveryReceiptFixture key) { return new RecoveryReceiptKeys(List.of(key.pair.getPublic().getEncoded())); }
}
