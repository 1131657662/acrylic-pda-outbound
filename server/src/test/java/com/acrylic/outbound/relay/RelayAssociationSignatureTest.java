package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RelayAssociationSignatureTest {
    @TempDir Path temporary;
    private RelayAssociationSignature signature;private RelayAssociationStore.Row row;
    @BeforeEach void signedEvidence() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var keys=generator.generateKeyPair();
        Files.write(temporary.resolve("private.der"),keys.getPrivate().getEncoded());Files.write(temporary.resolve("public.der"),keys.getPublic().getEncoded());
        signature=new RelayAssociationSignature(new OfflineSigner(temporary.resolve("private.der").toString(),temporary.resolve("public.der").toString(),new JsonText(new ObjectMapper())));
        var unsigned=new RelayAssociationFixture().row();row=new RelayAssociationStore.Row(unsigned.id(),unsigned.scope(),unsigned.request(),unsigned.context(),signature.create(unsigned),unsigned.at());
    }
    @Test void validLinkProofRetainsEveryBusinessCapabilityBoundary() {
        signature.verify(row);assertThat(row.receipt().path("associationRecorded").asBoolean()).isTrue();
        assertThat(row.receipt().path("pendingReconciliation").asBoolean()).isTrue();
        for(String field:new String[]{"newCollectionAuthorized","stockAvailable","businessProcessed","releaseAllowed","localDeletionAllowed"})
            assertThat(row.receipt().path(field).asBoolean()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"targetOrderId","physicalCheckId","deviceId","taskId","archiveSha256","contextSha256","signedProof","signingKeyId","associationRecorded"})
    void changedReceiptDoesNotInheritSignature(String field) {
        ((ObjectNode)row.receipt()).put(field,"changed");assertThatThrownBy(()->signature.verify(row)).isInstanceOf(BusinessException.class);
    }
    @Test void changingRequestOrTargetFactsBreaksTheProof() {
        ((ObjectNode)row.request()).put("reason","altered");assertThatThrownBy(()->signature.verify(row)).isInstanceOf(BusinessException.class);
    }
    @Test void changedRelationalReferenceIsDetectedByTheOriginalSignature() {
        var old=row.scope();var changed=new RelayAssociationStore.Scope(old.warehouse(),old.relay(),old.device(),old.task(),java.util.UUID.randomUUID(),old.actor(),old.physical());
        var altered=new RelayAssociationStore.Row(row.id(),changed,row.request(),row.context(),row.receipt(),row.at());
        assertThatThrownBy(()->signature.verify(altered)).isInstanceOf(BusinessException.class);
    }
}
