package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CommandReceiptCodecTest {
    @TempDir static Path directory;
    static OfflineSigner signer;static CommandReceiptCodec codec;
    @BeforeAll static void keys() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        Files.write(directory.resolve("private.pk8"),pair.getPrivate().getEncoded());Files.write(directory.resolve("public.der"),pair.getPublic().getEncoded());
        signer=new OfflineSigner(directory.resolve("private.pk8").toString(),directory.resolve("public.der").toString(),new JsonText(new ObjectMapper()));
        codec=new CommandReceiptCodec(signer);
    }
    @Test void originalBytesAndSignatureProveExactSyntheticContextAndDoNotGrantCapabilities() throws Exception {
        var write=CommandReceiptFixture.write();var result=codec.sign(write);var unsigned=SubmissionJson.parse(result.unsigned());
        CommandSignature.verify(result.token(),result.keyId(),unsigned,signer.publicKey());
        assertThat(unsigned.get("originalContext")).isEqualTo(write.context().json());assertThat(unsigned.get("businessDecision").isNull()).isTrue();
        assertThat(unsigned.get("status").textValue()).isEqualTo("INTERNAL_TEST_ONLY");
        assertThat(result.originalSha256()).isEqualTo(TemporaryProtocol.originalResponseSha256(result.original()));
        byte[] copy=result.original();copy[0]=0;assertThat(result.original()[0]).isEqualTo((byte)'{');
    }
    @ParameterizedTest @ValueSource(strings={"\"1\"","1.0","1.5","4294967297","-1","null","true"})
    void signedWrongVersionTypesCannotBeCoercedIntoVersionOne(String malformed) throws Exception {
        var receipt=(ObjectNode)SubmissionJson.parse(codec.sign(CommandReceiptFixture.write()).unsigned());
        receipt.set("receiptVersion",new ObjectMapper().readTree(malformed));
        assertThatThrownBy(()->CommandReceiptShape.validate(receipt)).isInstanceOf(RuntimeException.class);
    }
    @ParameterizedTest @ValueSource(strings={"principalId","userAuthVersion","deviceId","sessionId","deviceVersion","permissionSnapshot"})
    void malformedActorTypesAreRejectedEvenWhenSigned(String field) {
        var receipt=(ObjectNode)SubmissionJson.parse(codec.sign(CommandReceiptFixture.write()).unsigned());
        ((ObjectNode)receipt.get("actor")).put(field,1);
        var signed=signer.sign(Map.of("iss",TemporaryProtocol.ISSUER,"aud",TemporaryProtocol.RECEIPT_AUDIENCE,"receipt",receipt));
        assertThatCode(()->CommandSignature.verify(signed.token(),signed.keyId(),receipt,signer.publicKey())).doesNotThrowAnyException();
        assertThatThrownBy(()->CommandReceiptShape.validate(receipt)).isInstanceOf(RuntimeException.class);
    }
    @Test void validRsaCannotCoverWrongAudienceOrOuterContent() {
        var result=codec.sign(CommandReceiptFixture.write());var unsigned=SubmissionJson.parse(result.unsigned());
        var wrong=signer.sign(Map.of("iss",TemporaryProtocol.ISSUER,"aud","acrylic-pda-receipt","receipt",unsigned));
        assertThatThrownBy(()->CommandSignature.verify(wrong.token(),wrong.keyId(),unsigned,signer.publicKey())).isInstanceOf(RuntimeException.class);
        ((ObjectNode)unsigned).put("status","SUCCEEDED");
        assertThatThrownBy(()->CommandSignature.verify(result.token(),result.keyId(),unsigned,signer.publicKey())).isInstanceOf(RuntimeException.class);
    }
    @Test void unknownKeyAndNoncanonicalTokenAreClosed() {
        var result=codec.sign(CommandReceiptFixture.write());var unsigned=SubmissionJson.parse(result.unsigned());
        assertThatThrownBy(()->CommandSignature.verify(result.token(),"unknown",unsigned,signer.publicKey())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->CommandSignature.verify(result.token()+"=",result.keyId(),unsigned,signer.publicKey())).isInstanceOf(RuntimeException.class);
    }
    @Test void inputAndWriteDefensiveCopiesCannotAlterSignedCommand() {
        var write=CommandReceiptFixture.write();String digest=write.command().digest();
        write.command().bytes()[0]=0;((ObjectNode)write.command().body()).put("reason","changed");
        ((ObjectNode)write.context().json()).put("decisionVersion","99");((ObjectNode)write.actor()).put("principalId","changed");
        assertThat(write.command().digest()).isEqualTo(digest);assertThatCode(()->codec.sign(write)).doesNotThrowAnyException();
    }
}
