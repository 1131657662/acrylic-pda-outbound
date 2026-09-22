package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class E2aProductionVectorTest {
    @Test void fixedEmptySetVectorRecomputesAndVerifies() throws Exception {
        JsonNode vector=SubmissionJson.parse(resource());
        var page=(com.fasterxml.jackson.databind.node.ObjectNode)SubmissionJson.parse(vector.path("pageCoreUtf8").asText());
        assertThat(E2aProtocol.pageHash(page)).isEqualTo(vector.path("pageHash").asText());
        var manifest=(com.fasterxml.jackson.databind.node.ObjectNode)SubmissionJson.parse(vector.path("manifestCoreUtf8").asText());
        var hashes=E2aProtocol.manifestHashes(manifest);
        assertThat(hashes.manifestBytesHash()).isEqualTo(vector.path("manifestBytesHash").asText());
        assertThat(hashes.setHash()).isEqualTo(vector.path("setHash").asText());
        var envelope=SubmissionJson.object().put("schemaVersion",1).put("algorithm",vector.path("stopAlgorithm").asText())
            .put("keyId",vector.path("ecKeyId").asText());
        envelope.set("core",SubmissionJson.parse(vector.path("stopCoreUtf8").asText()));
        envelope.put("signature",vector.path("stopSignatureDerBase64").asText());
        var key=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(vector.path("ecPublicKeySpkiBase64").asText())));
        assertThat(E2aProtocol.verifyStop(envelope,key)).isTrue();
        var receipt=(com.fasterxml.jackson.databind.node.ObjectNode)SubmissionJson.parse(vector.path("unsignedReceiptUtf8").asText());
        receipt.put("signedReceipt",vector.path("signedReceiptJwt").asText());
        var rsa=new OfflineSigner.PublicKeyView(vector.path("rsaKeyId").asText(),"RS256",vector.path("rsaPublicKeySpkiBase64").asText());
        assertThat(E2aProtocol.verifyRsaReceipt(receipt,rsa)).isEqualTo("VERIFIED");
        receipt.put("status","CONFLICT");assertThat(E2aProtocol.verifyRsaReceipt(receipt,rsa)).isEqualTo("INVALID_SIGNATURE");
    }
    private static byte[] resource() throws IOException {
        try(InputStream input=E2aProductionVectorTest.class.getResourceAsStream("/e2a/e2a-v1-empty-set-vector.json")) {
            if(input==null)throw new FileNotFoundException("e2a production vector");return input.readAllBytes();
        }
    }
}
