package com.acrylic.outbound.independent;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.pda.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class IndependentCredentialProofTest {
    @TempDir Path temporary;
    private OfflineSigner signer;private ObjectNode original;private IndependentCredentialHistory.Record record;
    @BeforeEach void issuance() throws Exception {
        var pair=KeyPairGenerator.getInstance("RSA");pair.initialize(2048);var keys=pair.generateKeyPair();
        Files.write(temporary.resolve("private.der"),keys.getPrivate().getEncoded());Files.write(temporary.resolve("public.der"),keys.getPublic().getEncoded());
        signer=new OfflineSigner(temporary.resolve("private.der").toString(),temporary.resolve("public.der").toString(),new JsonText(new ObjectMapper()));
        var actor=new PdaSessions.Principal(UUID.randomUUID(),UUID.randomUUID(),"synthetic",UUID.randomUUID(),UUID.randomUUID(),"1","1");
        String token=new IndependentCredentialTokens(signer).sign(new IndependentCredentialTokens.Input(UUID.randomUUID(),actor,"1","a".repeat(64),Instant.parse("2020-01-01T00:00:00Z")));
        original=(ObjectNode)new ObjectMapper().readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        original.remove(List.of("iss","aud","purpose","scope","capabilities"));original.put("credentialSha256",DeviceCrypto.sha(token));
        record=new IndependentCredentialHistory.Record(original.deepCopy(),token);
    }
    @Test void expiredOriginalIssuanceRemainsVerifiableWithoutRenewal() {
        var result=new IndependentCredentialProof(signer).verify(original,record);
        assertThat(result).isEqualTo(new IndependentCredentialProof.Result("VERIFIED","ORIGINAL_ISSUANCE_VERIFIED"));
        assertThat(original.path("expiresAt").asText()).isEqualTo("2020-01-03T00:00:00Z");
    }
    @Test void missingServerOriginalIsUnverified() {
        assertThat(new IndependentCredentialProof(signer).verify(original,null).reason()).isEqualTo("CREDENTIAL_NOT_FOUND");
    }
    @ParameterizedTest @ValueSource(strings={"credentialId","operatorId","deviceId","warehouseId","deviceKeySha256","serverRecoveryEpoch","userAuthVersion","deviceVersion","issuedAt","expiresAt","credentialSha256"})
    void changedPublicClaimCannotInheritOriginalAuthorization(String field) {
        original.put(field,"changed");assertThat(new IndependentCredentialProof(signer).verify(original,record).state()).isEqualTo("UNVERIFIED");
    }
    @Test void alteredStoredSignatureCannotCertifyIssuanceEvenWithMatchingDatabaseDigest() {
        String token=record.token().substring(0,record.token().length()-4)+"aaaa";
        original.put("credentialSha256",DeviceCrypto.sha(token));record=new IndependentCredentialHistory.Record(original.deepCopy(),token);
        assertThat(new IndependentCredentialProof(signer).verify(original,record).reason()).isEqualTo("CREDENTIAL_SIGNATURE_INVALID");
    }
    @Test void unavailableHistoricalKeyDoesNotUsePackageKey() {
        var absent=new OfflineSigner("","",new JsonText(new ObjectMapper()));
        assertThat(new IndependentCredentialProof(absent).verify(original,record).reason()).isEqualTo("HISTORICAL_KEY_UNAVAILABLE");
    }
    @Test void configuredHistoricalPublicKeyVerifiesOriginalAfterRotation() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var next=generator.generateKeyPair();
        Files.write(temporary.resolve("next-private.der"),next.getPrivate().getEncoded());Files.write(temporary.resolve("next-public.der"),next.getPublic().getEncoded());
        var rotated=new OfflineSigner(temporary.resolve("next-private.der").toString(),temporary.resolve("next-public.der").toString(),
            temporary.resolve("public.der").toString(),new JsonText(new ObjectMapper()));
        assertThat(new IndependentCredentialProof(rotated).verify(original,record).state()).isEqualTo("VERIFIED");
    }
}
