package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RelayPhysicalSignatureTest {
    @TempDir Path temporary;
    private RelayPhysicalSignature signature;
    private RelayPhysicalStore.Check row;
    @BeforeEach void signedEvidence() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var keys=generator.generateKeyPair();
        Files.write(temporary.resolve("private.der"),keys.getPrivate().getEncoded());Files.write(temporary.resolve("public.der"),keys.getPublic().getEncoded());
        signature=new RelayPhysicalSignature(new OfflineSigner(temporary.resolve("private.der").toString(),temporary.resolve("public.der").toString(),new JsonText(new ObjectMapper())));
        var f=new RelayPhysicalFixture();var id=UUID.randomUUID();var receipt=signature.create(id,f.scope,f.request,f.context,f.NOW);
        row=new RelayPhysicalStore.Check(id,f.scope,f.request,f.context,receipt,f.NOW);
    }
    @Test void receiptVerifiesWithEveryProcessingCapabilityClosed() {
        assertThatCode(()->signature.verify(row)).doesNotThrowAnyException();
        for(String flag:new String[]{"associationAuthorized","stockAvailable","businessProcessed","releaseAllowed","localDeletionAllowed"})
            assertThat(row.receipt().path(flag).asBoolean()).isFalse();
        assertThat(row.receipt().path("pendingReconciliation").asBoolean()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"receiptId","warehouseId","relayId","supervisorId","taskId","archiveSha256","closureSha256","requestSha256","contextSha256","recordedAt","signedProof","signingKeyId"})
    void alteredReceiptCannotInheritOriginalSignature(String field) {
        ((com.fasterxml.jackson.databind.node.ObjectNode)row.receipt()).put(field,"changed");
        assertThatThrownBy(()->signature.verify(row)).isInstanceOf(BusinessException.class);
    }
    @Test void modifiedOriginalObservationIsDetected() {
        ((com.fasterxml.jackson.databind.node.ObjectNode)row.request()).put("reason","altered");
        assertThatThrownBy(()->signature.verify(row)).isInstanceOf(BusinessException.class);
    }
    @Test void modifiedOriginalContextIsDetected() {
        ((com.fasterxml.jackson.databind.node.ObjectNode)row.context()).put("hiddenCandidateVersion","2");
        assertThatThrownBy(()->signature.verify(row)).isInstanceOf(BusinessException.class);
    }
}
