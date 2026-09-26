package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DraftEvidenceServicesTest {
    @TempDir Path directory;
    private DraftEvidenceFixture f;
    private DraftEvidenceReceipts receipts;
    private DraftEvidenceOpenService opens;
    private SourceEvidenceIntakeObjectFiles files;
    @BeforeEach void setup() throws Exception {
        f=new DraftEvidenceFixture();receipts=mock(DraftEvidenceReceipts.class);
        opens=new DraftEvidenceOpenService(f.access,f.preparation,f.store,receipts);files=new SourceEvidenceIntakeObjectFiles(directory.toString());
    }
    @Test void validOriginalSignatureOpensOnlyOnceAndRejectsSecondEvidenceForSource() {
        when(receipts.insert(any(),any(),eq("OPEN"),any(),any())).thenReturn(new byte[]{1});
        assertThat(opens.open(f.header.evidence(),f.key,"1",f.open)).containsExactly(1);
        verify(f.store).insert(eq(f.expected),any(),eq(f.key),eq(f.open));
        when(f.store.sourceExists(f.actor.deviceId(),f.header.client())).thenReturn(true);
        assertThatThrownBy(()->opens.open(f.header.evidence(),UUID.randomUUID(),"1",f.open)).isInstanceOf(BusinessException.class);
    }
    @Test void alteredSignedMetadataNeverWrites() {
        var bad=SubmissionJson.parse(f.open);((com.fasterxml.jackson.databind.node.ObjectNode)bad.path("metadata")).put("stopEventId",UUID.randomUUID().toString());
        assertThatThrownBy(()->opens.open(f.header.evidence(),f.key,"1",DraftEvidenceWire.bytes(bad))).isInstanceOf(BusinessException.class);
        verify(f.store,never()).insert(any(),any(),any(),any());
    }
    @Test void openReplayNeedsNoNewWriteEpoch() {
        when(receipts.existing(f.actor.userId(),f.key,f.header.evidence(),"OPEN",f.open)).thenReturn(new byte[]{1});
        assertThat(opens.open(f.header.evidence(),f.key,"stale",f.open)).containsExactly(1);verify(f.access,never()).lock(anyString());
    }
    @Test void itemWritesDurableRawBeforeReceipt() throws Exception {
        var request=f.request(0);when(f.store.totals(f.header.evidence())).thenReturn(new long[]{0,0,0});
        new DraftEvidenceItemService(f.access,f.store,opens,receipts,files).item(request,"1");
        assertThat(files.read(f.item(request).stored())).isEqualTo(request.raw());
        var order=inOrder(f.store,receipts);order.verify(f.store).insertItem(eq(f.header.evidence()),any());
        order.verify(receipts).insert(eq(f.header),eq(request.key()),eq("ITEM"),any(),any());
    }
    @Test void outOfOrderAndCompletedUploadCannotAppend() {
        var service=new DraftEvidenceItemService(f.access,f.store,opens,receipts,files);
        when(f.store.totals(f.header.evidence())).thenReturn(new long[]{0,0,0});
        assertThatThrownBy(()->service.item(f.request(1),"1")).isInstanceOf(BusinessException.class);
        when(f.store.complete(f.header.evidence())).thenReturn(true);
        assertThatThrownBy(()->service.item(f.request(0),"1")).isInstanceOf(BusinessException.class);
        verify(f.store,never()).insertItem(any(),any());
    }
    @Test void originalItemReplayRefusesMissingFile() {
        var request=f.request(0);when(f.store.item(f.header.evidence(),1)).thenReturn(f.item(request));
        when(receipts.existing(eq(f.actor.userId()),eq(request.key()),eq(f.header.evidence()),eq("ITEM"),any())).thenReturn(new byte[]{1});
        assertThatThrownBy(()->new DraftEvidenceItemService(f.access,f.store,opens,receipts,files).item(request,"stale")).isInstanceOf(BusinessException.class);
    }
    @Test void actualOriginalSetVerifiesAndMissingOrTamperedFileFails() throws Exception {
        var stored=new ArrayList<DraftEvidenceStore.Item>();
        for(int i=0;i<f.vector.path("items").size();i++) {
            var request=f.request(i);files.store(new java.io.ByteArrayInputStream(request.raw()),request.length(),request.sha());stored.add(f.item(request));
        }
        when(f.store.page(eq(f.header.evidence()),anyLong())).thenAnswer(call->call.<Long>getArgument(1)==0?stored:List.of());
        var integrity=new DraftEvidenceIntegrity(f.store,files,mock(DraftEvidenceArchives.class));var original=opens.checked(f.header,f.actor);
        assertThat(integrity.verify(f.header,original).ownCaptureCount()).isEqualTo(1);
        Files.write(directory.resolve(stored.getFirst().stored().key()),new byte[]{0});
        assertThatThrownBy(()->integrity.verify(f.header,original)).isInstanceOf(BusinessException.class);
    }
    @Test void verifyFailureNeverMarksComplete() {
        when(f.store.page(any(),anyLong())).thenReturn(List.of());
        var service=new DraftEvidenceVerifyService(f.access,f.store,opens,receipts,new DraftEvidenceIntegrity(f.store,files,mock(DraftEvidenceArchives.class)));
        byte[] request=DraftEvidenceWire.bytes(SubmissionJson.object().put("evidenceId",f.header.evidence().toString()).put("rootSha256",f.header.root()));
        assertThatThrownBy(()->service.verify(f.header.evidence(),UUID.randomUUID(),"1",request)).isInstanceOf(BusinessException.class);
        verify(f.store,never()).complete(any(),any(),any());verify(receipts,never()).insert(any(),any(),any(),any(),any());
    }
}
