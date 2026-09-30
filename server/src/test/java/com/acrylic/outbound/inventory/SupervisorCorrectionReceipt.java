package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Independent JCA verification against the provisioned test public key, never the signing service. */
final class SupervisorCorrectionReceipt {
    static JsonNode verify(byte[] response, Path publicKey) throws Exception {
        var body = (ObjectNode) SubmissionJson.parse(response);
        String[] parts = body.path("signedReceipt").asText().split("\\.");
        assertThat(parts).hasSize(3);
        var decoder = Base64.getUrlDecoder(); var header = SubmissionJson.parse(decoder.decode(parts[0]));
        assertThat(header.path("alg").asText()).isEqualTo("RS256");
        byte[] der = Files.readAllBytes(publicKey);
        var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        var verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(decoder.decode(parts[2]))).isTrue();
        String keyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(Base64.getEncoder().encodeToString(der).getBytes(StandardCharsets.UTF_8)));
        assertThat(header.path("kid").asText()).isEqualTo("offline-" + keyHash.substring(0, 32));
        var claims = SubmissionJson.parse(decoder.decode(parts[1])); var unsigned = body.deepCopy(); unsigned.remove("signedReceipt");
        assertThat(claims.path("iss").asText()).isEqualTo("acrylic-outbound-server");
        assertThat(claims.path("aud").asText()).isEqualTo("acrylic-outbound-correction-receipt");
        assertThat(claims.path("receipt")).isEqualTo(unsigned);
        assertThat(body.path("capabilities")).isEqualTo(SubmissionJson.object().put("writeConfirmationAvailable", false)
            .put("releaseAllowed", false).put("mayResumeScanning", false).put("stockReleased", false).put("localDeletionAllowed", false));
        return body;
    }
}
