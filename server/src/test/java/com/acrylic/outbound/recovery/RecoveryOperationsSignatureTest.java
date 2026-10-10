package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.Signature;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryOperationsSignatureTest {
    @Test void verifiesExactCanonicalBytesAndRejectsTampering() throws Exception {
        var f=RecoveryOperationsFixture.create(); var approval=SubmissionJson.object().put("purpose","synthetic-test");
        var bytes=f.sign(approval); assertThat(RecoveryOperationsSignature.verify(bytes,f.authority())).isEqualTo(approval);
        var tampered=(ObjectNode)readCanonical(bytes); ((ObjectNode)tampered.path("approval")).put("purpose","changed");
        assertThatThrownBy(()->RecoveryOperationsSignature.verify(RecoveryControlJson.encode(tampered),f.authority()))
            .hasMessage("RECOVERY_OPERATIONS_SIGNATURE_INVALID");
    }
    @Test void rejectsEnvelopeAlgorithmKeyDomainAndExtraFields() throws Exception {
        var f=RecoveryOperationsFixture.create(); var envelope=(ObjectNode)readCanonical(f.sign(SubmissionJson.object().put("value",1)));
        for(String algorithm:List.of("none","HS256","PS256")) {
            var altered=envelope.deepCopy().put("algorithm",algorithm);
            assertThatThrownBy(()->RecoveryOperationsSignature.verify(RecoveryControlJson.encode(altered),f.authority()))
                .hasMessage("RECOVERY_OPERATIONS_ALGORITHM");
        }
        var other=RecoveryOperationsFixture.create();
        assertThatThrownBy(()->RecoveryOperationsSignature.verify(RecoveryControlJson.encode(envelope),other.authority()))
            .hasMessage("RECOVERY_OPERATIONS_KEY_MISMATCH");
        envelope.put("trusted",true);
        assertThatThrownBy(()->RecoveryOperationsSignature.verify(RecoveryControlJson.encode(envelope),f.authority())).hasMessage("CONTROL_FIELDS");
    }
    @Test void rejectsPaddedSignatureAndSignatureOverBytesWithoutTerminalLf() throws Exception {
        var f=RecoveryOperationsFixture.create(); var body=SubmissionJson.object().put("value",1);
        var envelope=(ObjectNode)readCanonical(f.sign(body)); var padded=envelope.deepCopy().put("signature",text(envelope,"signature")+"=");
        assertThatThrownBy(()->RecoveryOperationsSignature.verify(RecoveryControlJson.encode(padded),f.authority()))
            .hasMessage("RECOVERY_OPERATIONS_SIGNATURE_ENCODING");
        var signer=Signature.getInstance("SHA256withRSA"); signer.initSign(f.key.getPrivate()); var bytes=RecoveryControlJson.encode(body);
        signer.update(Arrays.copyOf(bytes,bytes.length-1)); envelope.put("signature",Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        assertThatThrownBy(()->RecoveryOperationsSignature.verify(RecoveryControlJson.encode(envelope),f.authority()))
            .hasMessage("RECOVERY_OPERATIONS_SIGNATURE_INVALID");
    }
}
