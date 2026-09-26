package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftEvidenceReceiptsTest {
    @TempDir Path directory;
    private DraftEvidenceFixture f;
    private JdbcTemplate jdbc;
    private OfflineSigner signer;
    private DraftEvidenceReceipts receipts;
    private final Map<String,Object> row=new HashMap<>();
    @BeforeEach void setup() throws Exception {
        f=new DraftEvidenceFixture();jdbc=mock(JdbcTemplate.class);signer=DraftEvidenceFixture.signer(directory);
        receipts=new DraftEvidenceReceipts(jdbc,signer,f.store);
        when(jdbc.queryForList(anyString(),any(byte[].class),any(byte[].class))).thenReturn(List.of(row));
        retain(receipts.insert(f.header,f.key,"OPEN",f.open,SubmissionJson.object()),f.open,"OPEN");
    }
    @Test void exactOriginalOpenReplaysSignedBytes() {
        assertThat(receipts.existing(f.actor.userId(),f.key,f.header.evidence(),"OPEN",f.open)).isEqualTo(row.get("response_bytes"));
    }
    @Test void keyCannotChangeOperationEvidenceOrOriginalBytes() {
        assertThatThrownBy(()->receipts.existing(f.actor.userId(),f.key,f.header.evidence(),"VERIFY",f.open)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->receipts.existing(f.actor.userId(),f.key,UUID.randomUUID(),"OPEN",f.open)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->receipts.existing(f.actor.userId(),f.key,f.header.evidence(),"OPEN",new byte[]{1})).isInstanceOf(BusinessException.class);
    }
    @Test void recalculatedDatabaseHashDoesNotHideSignatureTampering() {
        var response=(ObjectNode)SubmissionJson.parse((byte[])row.get("response_bytes"));response.put("rootSha256","0".repeat(64));replace(response);
        assertUnavailable();
    }
    @Test void evenValidIssuerSignatureCannotChangeSourceOrCapabilities() {
        for(String field:List.of("clientOrderId","warehouseId","creatorDeviceId","rootSha256","status","businessFactsStatus")) {
            var response=(ObjectNode)SubmissionJson.parse(receipts.insert(f.header,f.key,"OPEN",f.open,SubmissionJson.object()));
            response.put(field,"wrong");resign(response);assertUnavailable();
        }
        for(String field:List.of("localDeletionAllowed","releaseAllowed","mayResumeScanning","writeConfirmationAvailable")) {
            var response=(ObjectNode)SubmissionJson.parse(receipts.insert(f.header,f.key,"OPEN",f.open,SubmissionJson.object()));
            response.put(field,true);resign(response);assertUnavailable();
        }
    }
    @Test void unknownHistoricalIssuerKeyFailsClosed() {
        var unavailable=mock(OfflineSigner.class);var other=new DraftEvidenceReceipts(jdbc,unavailable,f.store);
        assertThatThrownBy(()->other.existing(f.actor.userId(),f.key,f.header.evidence(),"OPEN",f.open)).isInstanceOf(BusinessException.class);
    }
    @Test void itemReceiptRequiresMatchingPersistentDescriptor() {
        var request=f.request(0);var stored=f.item(request);when(f.store.item(f.header.evidence(),1)).thenReturn(stored);
        var bytes=receipts.insert(f.header,f.key,"ITEM",request.descriptor(),SubmissionJson.object().put("ordinal",1).put("bytesSha256",request.sha()));
        retain(bytes,request.descriptor(),"ITEM");assertThat(receipts.existing(f.actor.userId(),f.key,f.header.evidence(),"ITEM",request.descriptor())).isEqualTo(bytes);
        when(f.store.item(f.header.evidence(),1)).thenReturn(new DraftEvidenceStore.Item(1,stored.kind(),UUID.randomUUID(),stored.sequence(),stored.status(),stored.stored()));
        assertThatThrownBy(()->receipts.existing(f.actor.userId(),f.key,f.header.evidence(),"ITEM",request.descriptor())).isInstanceOf(BusinessException.class);
    }
    @Test void verifyReceiptRequiresMatchingCompletionMarker() {
        byte[] request=DraftEvidenceWire.bytes(SubmissionJson.object().put("evidenceId",f.header.evidence().toString()).put("rootSha256",f.header.root()));
        var detail=SubmissionJson.object().put("recordCount",f.header.count()).put("byteCount",f.header.bytes()).put("ownCaptureCount",1).put("otherCaptureCount",0).put("archiveCount",0);
        byte[] bytes=receipts.insert(f.header,f.key,"VERIFY",request,detail);retain(bytes,request,"VERIFY");
        assertThatThrownBy(()->receipts.existing(f.actor.userId(),f.key,f.header.evidence(),"VERIFY",request)).isInstanceOf(BusinessException.class);
        when(f.store.completedBy(f.header.evidence(),f.actor.userId(),f.key)).thenReturn(true);
        assertThat(receipts.existing(f.actor.userId(),f.key,f.header.evidence(),"VERIFY",request)).isEqualTo(bytes);
    }
    private void assertUnavailable() {
        assertThatThrownBy(()->receipts.existing(f.actor.userId(),f.key,f.header.evidence(),"OPEN",f.open)).isInstanceOf(BusinessException.class);
    }
    private void resign(ObjectNode response) {
        response.remove("signedReceipt");var claims=SubmissionJson.object().put("iss","acrylic-outbound").put("aud",DraftEvidenceReceipts.AUDIENCE);
        claims.set("receipt",response.deepCopy());response.put("signedReceipt",signer.sign(claims).token());replace(response);
    }
    private void replace(ObjectNode response) {
        var bytes=DraftEvidenceWire.bytes(response);row.put("response_bytes",bytes);row.put("response_sha256",DraftEvidenceWire.sha(bytes));
    }
    private void retain(byte[] response,byte[] request,String operation) {
        row.put("evidence_id",Ids.bytes(f.header.evidence()));row.put("operation_kind",operation);row.put("request_bytes",request);
        row.put("request_sha256",DraftEvidenceWire.sha(request));row.put("response_bytes",response);row.put("response_sha256",DraftEvidenceWire.sha(response));
    }
}
