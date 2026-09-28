package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

class TemporarySourceEvidenceProofTest {
    static KeyPair keys;
    @BeforeAll static void keys() throws Exception { var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);keys=generator.generateKeyPair(); }
    private OfflineSigner.PublicKeyView view() { return new OfflineSigner.PublicKeyView("test-key","RS256",Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())); }
    private ObjectNode signed(String keyId) throws Exception {
        var receipt=object().put("submissionId","original").put("payloadPresent",false).putNull("payloadSha256");
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-receipt");claims.set("receipt",receipt.deepCopy());
        String prefix=encode(canonical(object().put("alg","RS256").put("typ","JWT").put("kid",keyId)))+"."+encode(canonical(claims));
        var signer=Signature.getInstance("SHA256withRSA");signer.initSign(keys.getPrivate());signer.update(prefix.getBytes(StandardCharsets.US_ASCII));
        return receipt.put("signedReceipt",prefix+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
    }
    private static String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    @Test void genuineStoredReceiptVerifiesWithoutRewriting() throws Exception {
        var receipt=signed("test-key");String before=canonical(receipt);
        assertThat(SourceEvidenceProof.verify(receipt,view())).isEqualTo("VERIFIED");assertThat(canonical(receipt)).isEqualTo(before);
    }
    @Test void unknownHistoricalKeyIsAnExplicitGap() throws Exception {
        assertThat(SourceEvidenceProof.verify(signed("older-key"),view())).isEqualTo("UNVERIFIABLE_KEY");
    }
    @Test void outerReceiptMutationCannotPassValidToken() throws Exception {
        var receipt=signed("test-key");receipt.put("payloadPresent",true);
        assertThat(SourceEvidenceProof.verify(receipt,view())).isEqualTo("INVALID_SIGNATURE");
    }
    @Test void malformedAndForgedSignaturesFailClosed() throws Exception {
        for(String token:new String[]{"bad","a.b.c","","e30.e30.Zm9yZ2Vk","invalid.token.signature"}) {
            var receipt=signed("test-key").put("signedReceipt",token);
            assertThat(SourceEvidenceProof.verify(receipt,view())).isEqualTo("INVALID_SIGNATURE");
        }
    }
    @Test void malformedClaimsRemainInvalidEvenWithAValidSignature() throws Exception {
        String prefix=encode(canonical(object().put("alg","RS256").put("typ","JWT").put("kid","test-key")))+"."+encode("{broken");
        var signer=Signature.getInstance("SHA256withRSA");signer.initSign(keys.getPrivate());
        signer.update(prefix.getBytes(StandardCharsets.US_ASCII));
        String token=prefix+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        assertThat(SourceEvidenceProof.verify(object().put("signedReceipt",token),view())).isEqualTo("INVALID_SIGNATURE");
    }
    @Test void unavailableTrustedKeyKeepsItsOriginalFailure() throws Exception {
        var receipt=signed("test-key");
        var unavailable=new BusinessException(503,"SIGNING_KEY_UNAVAILABLE","test key unavailable");
        assertThatThrownBy(()->SourceEvidenceProof.verify(receipt,kid->{throw unavailable;})).isSameAs(unavailable);
    }
}
