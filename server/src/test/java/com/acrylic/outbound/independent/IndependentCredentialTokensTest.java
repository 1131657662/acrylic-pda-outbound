package com.acrylic.outbound.independent;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndependentCredentialTokensTest {
    @TempDir Path temporary;
    @Test void signedIndependentVectorCarriesOnlyBoundedCaptureCapability() throws Exception {
        var pair=KeyPairGenerator.getInstance("RSA");pair.initialize(2048);var keys=pair.generateKeyPair();
        Path privateKey=temporary.resolve("private.der"),publicKey=temporary.resolve("public.der");
        Files.write(privateKey,keys.getPrivate().getEncoded());Files.write(publicKey,keys.getPublic().getEncoded());
        var mapper=new ObjectMapper();var signer=new OfflineSigner(privateKey.toString(),publicKey.toString(),new JsonText(mapper));
        var actor=new PdaSessions.Principal(UUID.randomUUID(),UUID.randomUUID(),"synthetic-operator",UUID.randomUUID(),UUID.randomUUID(),"1","1");
        var input=new IndependentCredentialTokens.Input(UUID.randomUUID(),actor,"1","a".repeat(64),Instant.parse("2026-10-13T10:00:00Z"));
        String token=new IndependentCredentialTokens(signer).sign(input);String[] parts=token.split("\\.");
        var verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(keys.getPublic());
        verifier.update((parts[0]+"."+parts[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        var claims=mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.path("aud").asText()).isEqualTo("acrylic-pda-independent-v1");
        assertThat(claims.path("operatorId").asText()).isEqualTo(actor.userId().toString());
        assertThat(Duration.between(Instant.parse(claims.path("issuedAt").asText()),Instant.parse(claims.path("expiresAt").asText()))).isEqualTo(Duration.ofHours(48));
        assertThat(claims.path("capabilities").path("mayCollectIndependent").asBoolean()).isTrue();
        for(String field:List.of("maySubmit","mayConfirm","stockAvailable","localDeletionAllowed"))
            assertThat(claims.path("capabilities").path(field).asBoolean()).isFalse();
        String output=System.getenv("E06_CREDENTIAL_VECTOR_OUTPUT");
        if(output!=null)Files.writeString(Path.of(output),mapper.writeValueAsString(Map.of("signedCredential",token,
            "signingKey",signer.publicKey(),"claims",claims,"identityClass","synthetic server unit vector")));
    }
}
