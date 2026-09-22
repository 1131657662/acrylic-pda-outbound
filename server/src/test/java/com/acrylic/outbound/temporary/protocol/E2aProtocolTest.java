package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class E2aProtocolTest {
    @Test void pageAndManifestHashesExcludeDerivedFields() {
        ObjectNode page=SubmissionJson.object().put("manifestId","m").put("manifestVersion",1).put("snapshotId","s")
            .put("pageNo",1).put("pageCount",1).put("isFinal",true).put("itemCount",0).put("byteCount",0).putNull("prevPageHash"); page.putArray("items");
        String pageHash=E2aProtocol.pageHash(page);assertThat(pageHash).hasSize(64);
        ObjectNode manifest=SubmissionJson.object().put("schemaVersion",1).put("manifestId","m").put("manifestVersion",1)
            .put("snapshotId","s").put("source","TEMP").put("workRound","1").put("pageCount",1).put("entryCount",0).put("byteCount",0).put("endPageNo",1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo",1).put("pageHash",pageHash));
        var first=E2aProtocol.manifestHashes(manifest);assertThat(first.manifestBytesHash()).isNotEqualTo(first.setHash());
        manifest.put("setHash",first.setHash());
        assertThatThrownBy(() -> E2aProtocol.manifestHashes(manifest)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void pageRejectsUnknownDerivedField() {
        ObjectNode page=SubmissionJson.object().put("manifestId","m").put("manifestVersion",1).put("snapshotId","s").put("pageNo",1).put("pageCount",1).put("isFinal",true).put("itemCount",0).put("byteCount",0).putNull("prevPageHash"); page.putArray("items");
        page.putArray("items"); page.put("nextHash","bad");
        assertThatThrownBy(() -> E2aProtocol.pageHash(page)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void stopSignatureUsesDerAndRejectsMutation() throws Exception {
        var keys=KeyPairGenerator.getInstance("EC");keys.initialize(256);KeyPair pair=keys.generateKeyPair();
        ObjectNode core=SubmissionJson.object().put("manifestId","m").put("manifestVersion",1).put("setHash","a".repeat(64));
        ObjectNode envelope=E2aProtocol.signStop(core,pair.getPrivate(),com.acrylic.outbound.inventory.ImportFiles.sha256(pair.getPublic().getEncoded()));
        assertThat(envelope.path("algorithm").asText()).isEqualTo("ECDSA_P256_SHA256_DER");assertThat(E2aProtocol.verifyStop(envelope,pair.getPublic())).isTrue();
        ((ObjectNode)envelope.path("core")).put("manifestId","changed");assertThat(E2aProtocol.verifyStop(envelope,pair.getPublic())).isFalse();
        envelope.put("extra","reject");assertThat(E2aProtocol.verifyStop(envelope,pair.getPublic())).isFalse();
        envelope.remove("extra");envelope.put("keyId","0".repeat(64));assertThat(E2aProtocol.verifyStop(envelope,pair.getPublic())).isFalse();
    }
    @Test void stopRejectsNonP256Key() throws Exception {
        var keys=KeyPairGenerator.getInstance("EC");keys.initialize(384);KeyPair pair=keys.generateKeyPair();
        ObjectNode core=SubmissionJson.object().put("manifestId","m");
        assertThatThrownBy(() -> E2aProtocol.signStop(core,pair.getPrivate(),"a".repeat(64))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rsaReceiptRejectsFactMutationAndUnknownKey() throws Exception {
        var keys=KeyPairGenerator.getInstance("RSA");keys.initialize(2048);KeyPair pair=keys.generateKeyPair();
        ObjectNode receipt=SubmissionJson.object().put("receiptVersion",1).put("status","RECEIVED");
        String header=encode(SubmissionJson.object().put("alg","RS256").put("typ","JWT").put("kid","k"));
        ObjectNode claims=SubmissionJson.object().put("iss",E2aProtocol.ISSUER).put("aud",E2aProtocol.AUDIENCE).set("receipt",receipt.deepCopy());
        String payload=header+"."+encode(claims);var signer=Signature.getInstance("SHA256withRSA");signer.initSign(pair.getPrivate());signer.update(payload.getBytes(StandardCharsets.US_ASCII));
        receipt.put("signedReceipt",payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        var view=new OfflineSigner.PublicKeyView("k","RS256",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        assertThat(E2aProtocol.verifyRsaReceipt(receipt,view)).isEqualTo("VERIFIED");receipt.put("status","CONFLICT");assertThat(E2aProtocol.verifyRsaReceipt(receipt,view)).isEqualTo("INVALID_SIGNATURE");
        receipt.put("status","RECEIVED");String publicDer=view.publicKey();String altered=publicDer.substring(0,publicDer.length()-1)+(publicDer.endsWith("A")?"B":"A");var nonCanonical=new OfflineSigner.PublicKeyView("k","RS256",altered);
        assertThat(E2aProtocol.verifyRsaReceipt(receipt,nonCanonical)).isEqualTo("INVALID_SIGNATURE");
        assertThat(E2aProtocol.trustedFacts(receipt,receipt.deepCopy())).isTrue();assertThat(E2aProtocol.trustedFacts(receipt,SubmissionJson.object())).isFalse();
    }
    private static String encode(ObjectNode value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8)); }
}
