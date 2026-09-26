package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionCallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakePageItemWriterTest {
    private static final UUID OPEN=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID RECORD=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ORIGINAL=UUID.fromString("33333333-3333-4333-8333-333333333333");
    @TempDir Path directory;
    private final SourceEvidenceIntakeTransactions transactions=mock(SourceEvidenceIntakeTransactions.class);
    private final SourceEvidenceIntakeRawObjects rawObjects=mock(SourceEvidenceIntakeRawObjects.class);
    private final SourceEvidenceIntakePageItems staged=mock(SourceEvidenceIntakePageItems.class);
    private final SourceEvidenceIntakePageObjects.Binding binding=new SourceEvidenceIntakePageObjects.Binding(
        UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),3,UUID.randomUUID(),UUID.randomUUID(),OPEN);
    private final byte[] raw="raw!".getBytes(StandardCharsets.UTF_8);
    private SourceEvidenceIntakeObjectFiles files;
    private SourceEvidenceIntakePageItemWriter writer;
    private SourceEvidenceIntakeRawObjects.RawObject object;
    private SourceEvidenceIntakePage.Parsed page;
    private SourceEvidenceIntakePageObjects.PageObject pageObject;

    @BeforeEach void setUp() throws Exception {
        files=new SourceEvidenceIntakeObjectFiles(directory.toString());
        var stored=files.store(new ByteArrayInputStream(raw),raw.length,ImportFiles.sha256(raw));
        object=new SourceEvidenceIntakeRawObjects.RawObject(UUID.randomUUID(),ORIGINAL,stored);
        pageObject=new SourceEvidenceIntakePageObjects.PageObject(UUID.randomUUID(),UUID.randomUUID(),
            new SourceEvidenceIntakeFiles.Stored("source-evidence-intake/"+"a".repeat(64)+".json","a".repeat(64),10));
        page=page();
        writer=new SourceEvidenceIntakePageItemWriter(transactions,rawObjects,files,staged);
        when(rawObjects.findAfterSourceLock(any())).thenReturn(object);
        when(staged.existing(any())).thenReturn(List.of());
        when(staged.totals(binding)).thenReturn(new SourceEvidenceIntakePageItems.Totals(0,0));
        doAnswer(invocation -> ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null))
            .when(transactions).read(any());
    }

    @Test void preflightChecksOriginalBytesBeforeShortWriteThenIndexesReference() {
        var prepared=writer.preflight(binding,page);

        writer.stageNew(binding,page,pageObject,prepared);

        assertThat(prepared.raw()).containsExactly(object);
        var indexed=SourceEvidenceIntakePageItems.index(0,page.items().getFirst(),object);
        verify(staged).insert(new SourceEvidenceIntakePageItems.PageContext(binding,1,pageObject.id()),indexed);
    }

    @Test void missingOrCorruptedRawObjectCannotEnterThePageTransaction() throws Exception {
        when(rawObjects.findAfterSourceLock(any())).thenReturn(null);
        assertThatThrownBy(() -> writer.preflight(binding,page)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_RAW_OBJECT_MISSING"));
        when(rawObjects.findAfterSourceLock(any())).thenReturn(object);
        Files.writeString(directory.resolve(object.stored().key()),"BAD!");

        assertThatThrownBy(() -> writer.preflight(binding,page)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_RAW_OBJECT_FILE_UNAVAILABLE"));
        verify(staged,never()).insert(any(),any());
    }

    @Test void sourceIndexChangeAfterFileCheckFailsClosed() {
        var prepared=writer.preflight(binding,page);
        when(rawObjects.findAfterSourceLock(any())).thenReturn(new SourceEvidenceIntakeRawObjects.RawObject(
            UUID.randomUUID(),ORIGINAL,object.stored()));

        assertThatThrownBy(() -> writer.stageNew(binding,page,pageObject,prepared))
            .isInstanceOfSatisfying(BusinessException.class,
                error->assertThat(error.code()).isEqualTo("E2A_RAW_OBJECT_CHANGED"));
        verify(staged,never()).insert(any(),any());
    }

    @Test void setByteLimitRejectsLaterPageBeforeAnyIndexInsert() {
        var prepared=writer.preflight(binding,page);
        when(staged.totals(binding)).thenReturn(new SourceEvidenceIntakePageItems.Totals(134217728,1));

        assertThatThrownBy(() -> writer.stageNew(binding,page,pageObject,prepared))
            .isInstanceOfSatisfying(BusinessException.class,
                error->assertThat(error.code()).isEqualTo("E2A_PAGE_BYTES_LIMIT"));
        verify(staged,never()).insert(any(),any());
    }

    @Test void samePageMayGetNewReceiptOnlyWhenIndexedItemsMatch() {
        var prepared=writer.preflight(binding,page);
        var indexed=SourceEvidenceIntakePageItems.index(0,page.items().getFirst(),object);
        when(staged.existing(any())).thenReturn(List.of(indexed));

        writer.verifyExisting(binding,page,pageObject,prepared);

        verify(staged,never()).insert(any(),any());
        when(staged.existing(any())).thenReturn(List.of());
        assertThatThrownBy(() -> writer.verifyExisting(binding,page,pageObject,prepared))
            .isInstanceOfSatisfying(BusinessException.class,
                error->assertThat(error.code()).isEqualTo("E2A_PAGE_ITEM_INTEGRITY_FAILURE"));
    }

    private SourceEvidenceIntakePage.Parsed page() {
        var item=SubmissionJson.object().put("itemKind","CAPTURE").put("originalRecordId",RECORD.toString())
            .put("objectKind","CAPTURE_RECORDS").put("originalObjectId",ORIGINAL.toString())
            .put("itemBytesSha256",ImportFiles.sha256(raw)).put("byteCount",raw.length).put("originalDeviceSeq",1)
            .put("recordSha256",ImportFiles.sha256(raw)).put("recordStatus","CAPTURED")
            .putNull("baleId").putNull("originalCycle").putNull("captureId");
        var core=SubmissionJson.object().put("manifestId","m").put("manifestVersion",1).put("snapshotId","s")
            .put("pageNo",1).put("pageCount",1).put("isFinal",true).put("itemCount",1)
            .put("byteCount",raw.length).putNull("prevPageHash");
        core.putArray("items").add(item);
        var body=SubmissionJson.object().put("operation","PAGE").put("openOriginalKey",OPEN.toString());
        body.set("pageCore",core);body.put("pageHash",E2aProtocol.pageHash(core));
        var open=SourceEvidenceIntakeOpen.parse(("""
            {"operation":"OPEN","manifestId":"m","manifestVersion":1,"snapshotId":"s",
             "source":"TEMP","sourceId":"source","submissionId":"%s","workRound":3}
            """).formatted(binding.submissionId()).getBytes(StandardCharsets.UTF_8),binding.submissionId(),3);
        return SourceEvidenceIntakePage.parse(body.toString().getBytes(StandardCharsets.UTF_8),OPEN,open);
    }
}
