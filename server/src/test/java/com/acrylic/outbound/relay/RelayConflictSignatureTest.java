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

class RelayConflictSignatureTest {
    @TempDir Path temporary;
    private RelayConflictSignature signature;private RelayConflictStore.Row row;
    @BeforeEach void signedEvidence() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var keys=generator.generateKeyPair();
        Files.write(temporary.resolve("private.der"),keys.getPrivate().getEncoded());Files.write(temporary.resolve("public.der"),keys.getPublic().getEncoded());
        signature=new RelayConflictSignature(new OfflineSigner(temporary.resolve("private.der").toString(),temporary.resolve("public.der").toString(),new JsonText(new ObjectMapper())));
        var unsigned=new RelayConflictFixture().row();row=new RelayConflictStore.Row(unsigned.id(),unsigned.scope(),unsigned.request(),unsigned.context(),signature.create(unsigned),unsigned.at());
    }
    @Test void validDecisionConfersNoAssociationStockOrBusinessCapability() {
        signature.verify(row);assertThat(row.receipt().path("conflictDecisionRecorded").asBoolean()).isTrue();
        assertThat(row.receipt().path("decision").asText()).isEqualTo("ASSIGN_TO_SELECTED_TASK");
        for(String field:new String[]{"associationAuthorized","newCollectionAuthorized","stockAvailable","businessProcessed","releaseAllowed","localDeletionAllowed"})
            assertThat(row.receipt().path(field).asBoolean()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"decisionId","deviceId","taskId","decision","archiveSha256","contextSha256","signedProof","signingKeyId","conflictDecisionRecorded"})
    void changedReceiptDoesNotInheritSignature(String field) {
        ((ObjectNode)row.receipt()).put(field,"changed");assertThatThrownBy(()->signature.verify(row)).isInstanceOf(BusinessException.class);
    }
    @Test void changedPeerContextOrPhysicalReferenceBreaksTheProof() {
        ((ObjectNode)row.context().path("peers").get(0)).put("hiddenChange",true);
        assertThatThrownBy(()->signature.verify(row)).isInstanceOf(BusinessException.class);
    }
    @Test void changedRelationalOwnerIsDetectedByOriginalSignature() {
        var old=row.scope();var changed=new RelayConflictStore.Scope(old.warehouse(),old.relay(),java.util.UUID.randomUUID(),old.task(),old.actor());
        var altered=new RelayConflictStore.Row(row.id(),changed,row.request(),row.context(),row.receipt(),row.at());
        assertThatThrownBy(()->signature.verify(altered)).isInstanceOf(BusinessException.class);
    }
    @Test void extensionBindsTheCompleteOriginalPredecessorReceipt() {
        var request=(ObjectNode)row.request().deepCopy();var context=(ObjectNode)row.context().deepCopy();
        request.put("predecessorDecisionId",row.id().toString());context.set("predecessorReceipt",row.receipt().deepCopy());
        var unsigned=new RelayConflictStore.Row(java.util.UUID.randomUUID(),row.scope(),request,context,null,row.at());
        var next=new RelayConflictStore.Row(unsigned.id(),unsigned.scope(),request,context,signature.create(unsigned),row.at());
        signature.verify(next);assertThat(next.receipt().path("predecessorDecisionId").asText()).isEqualTo(row.id().toString());
        ((ObjectNode)context.path("predecessorReceipt")).put("localDeletionAllowed",true);
        assertThatThrownBy(()->signature.verify(next)).isInstanceOf(BusinessException.class);
    }
}
