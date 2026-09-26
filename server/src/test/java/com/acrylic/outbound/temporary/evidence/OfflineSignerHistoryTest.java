package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionArchiveProofs;
import com.acrylic.outbound.temporary.command.CommandSignature;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.acrylic.outbound.submissions.SubmissionJson.object;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfflineSignerHistoryTest {
    @TempDir Path directory;

    @Test void rotatedPublicKeyVerifiesStoredReceiptWithoutChangingOriginalBytes() throws Exception {
        var former=signer("former", "");
        var receipt=object().put("submissionId", "original");
        var claims=Map.of("iss", "acrylic-outbound", "aud", "acrylic-pda-receipt", "receipt", receipt.deepCopy());
        receipt.put("signedReceipt", former.sign(claims).token());
        var original=receipt.deepCopy();
        var current=signer("current", directory.resolve("former.der").toString());

        assertThat(current.publicKeyFor(former.publicKey().keyId())).isEqualTo(former.publicKey());
        assertThat(current.publicKeyFor(current.publicKey().keyId())).isEqualTo(current.publicKey());
        assertThat(SourceEvidenceProof.verify(receipt,current::publicKeyFor)).isEqualTo("VERIFIED");
        assertThat(receipt).isEqualTo(original);
        var intake=object().put("receiptVersion",1);
        intake.put("signedReceipt",former.sign(Map.of("iss","acrylic-outbound", "aud",
            "acrylic-r06-e2a-intake-v1", "receipt",intake.deepCopy())).token());
        assertThat(E2aProtocol.verifyRsaReceipt(intake,current.publicKeyFor(former.publicKey().keyId())))
            .isEqualTo("VERIFIED");
        var archive=new SubmissionArchiveProofs(former).sign(object().put("archiveVersion",1));
        new SubmissionArchiveProofs(current).verify(archive,"signedArchive","acrylic-pda-archive","archive");
        var command=object().put("receiptVersion",1);
        var commandToken=former.sign(Map.of("iss",TemporaryProtocol.ISSUER,"aud",
            TemporaryProtocol.RECEIPT_AUDIENCE,"receipt",command.deepCopy()));
        new CommandSignature(current).verify(commandToken.token(),commandToken.keyId(),command);
        assertThatThrownBy(() -> new SubmissionArchiveProofs(signer("unconfigured", ""))
            .verify(archive,"signedArchive","acrylic-pda-archive","archive"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("ARCHIVE_EVIDENCE_UNAVAILABLE"));
        assertThat(current.publicKeyFor("offline-unknown")).isNull();
        receipt.put("submissionId", "altered");
        assertThat(SourceEvidenceProof.verify(receipt,current::publicKeyFor)).isEqualTo("INVALID_SIGNATURE");
    }

    @Test void missingHistoricalKeyFileNeverTrustsAnUnknownKey() throws Exception {
        var current=signer("current", directory.resolve("missing.der").toString());

        assertThatThrownBy(() -> current.publicKeyFor("offline-unknown"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("SIGNING_KEY_UNAVAILABLE"));
    }

    private OfflineSigner signer(String name,String historical) throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair=generator.generateKeyPair();
        Path privateKey=directory.resolve(name+".pk8"), publicKey=directory.resolve(name+".der");
        Files.write(privateKey,pair.getPrivate().getEncoded());
        Files.write(publicKey,pair.getPublic().getEncoded());
        return new OfflineSigner(privateKey.toString(),publicKey.toString(),historical,new JsonText(new ObjectMapper()));
    }
}
