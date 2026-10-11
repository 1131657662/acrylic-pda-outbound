package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryRecipientDownloadArchiveTest {
    @Test void acceptsPinnedDeviceKeyFollowedBySiblingField() throws Exception {
        var input=input(); var decoded=RecoveryRecipientDownloadArchive.descriptor(input,Binding.class);
        assertThat(decoded.key().getAlgorithm()).isEqualTo("EC"); assertThat(decoded.sibling()).isEqualTo("retained");
    }
    @Test void rejectsUnknownDeviceKeyAlgorithm() throws Exception {
        var input=input(); ((ObjectNode)input.path("key")).put("algorithm","RSA");
        assertThatThrownBy(()->RecoveryRecipientDownloadArchive.descriptor(input,Binding.class))
            .hasMessage("RECOVERY_DOWNLOAD_DESCRIPTOR_INVALID");
    }
    @Test void rejectsAdditionalKeyField() throws Exception {
        var input=input(); ((ObjectNode)input.path("key")).put("trusted",true);
        assertThatThrownBy(()->RecoveryRecipientDownloadArchive.descriptor(input,Binding.class))
            .hasMessage("RECOVERY_DOWNLOAD_DESCRIPTOR_INVALID");
    }
    @Test void rejectsMalformedDeviceKey() throws Exception {
        var input=input(); ((ObjectNode)input.path("key")).put("encoded","AA==");
        assertThatThrownBy(()->RecoveryRecipientDownloadArchive.descriptor(input,Binding.class))
            .hasMessage("RECOVERY_DOWNLOAD_DESCRIPTOR_INVALID");
    }
    private ObjectNode input() throws Exception {
        var generator=KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1"));
        var result=SubmissionJson.object(); result.putObject("key").put("algorithm","EC")
            .put("encoded",Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded()));
        return result.put("sibling","retained");
    }
    record Binding(PublicKey key,String sibling) { }
}
