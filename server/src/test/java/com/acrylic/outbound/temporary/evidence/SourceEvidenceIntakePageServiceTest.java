package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import com.acrylic.outbound.temporary.command.CommandLocks;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionCallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakePageServiceTest {
    private static final UUID WAREHOUSE=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID OPERATOR=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID DEVICE=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID SESSION=UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final UUID SUBMISSION=UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    private static final UUID OPEN_KEY=UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
    private static final UUID PAGE_KEY=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID AUTHORIZATION=UUID.fromString("22222222-2222-4222-8222-222222222222");
    @TempDir Path directory;
    private final SourceEvidenceIntakeIdentity identity=mock(SourceEvidenceIntakeIdentity.class);
    private final SourceEvidenceIntakeTransactions transactions=mock(SourceEvidenceIntakeTransactions.class);
    private final SourceEvidenceIntakeReceipts opens=mock(SourceEvidenceIntakeReceipts.class);
    private final SourceEvidenceIntakePageReceipts pages=mock(SourceEvidenceIntakePageReceipts.class);
    private final SourceEvidenceIntakeRecovery recovery=mock(SourceEvidenceIntakeRecovery.class);
    private final SourceEvidenceIntakeReceiptCodec codec=mock(SourceEvidenceIntakeReceiptCodec.class);
    private final SourceEvidenceIntakeFacts facts=mock(SourceEvidenceIntakeFacts.class);
    private final CommandLocks locks=mock(CommandLocks.class);
    private final SourceEvidenceIntakeEvidenceLocks evidenceLocks=mock(SourceEvidenceIntakeEvidenceLocks.class);
    private final SourceEvidenceIntakePageObjects objects=mock(SourceEvidenceIntakePageObjects.class);
    private final SourceEvidenceIntakePageItemWriter itemWriter=mock(SourceEvidenceIntakePageItemWriter.class);
    private final ServerState serverState=mock(ServerState.class);
    private SourceEvidenceIntakeFiles files;
    private SourceEvidenceIntakePageService service;
    private CommandIdentity.Actor actor;
    private CommandContext context;
    private SourceEvidenceIntakeReceiptCodec.Receipt receipt;
    private SourceEvidenceIntakeOpen.Parsed open;

    @BeforeEach void setUp() {
        var principal=new PdaSessions.Principal(SESSION,OPERATOR,"operator",DEVICE,WAREHOUSE,"7","2");
        actor=new CommandIdentity.Actor(OPERATOR,WAREHOUSE,"7",principal,List.of("pda:recover"),List.of(WAREHOUSE.toString()));
        context=new CommandContext(SubmissionJson.parse("""
            {"scopeId":"33333333-3333-4333-8333-333333333333","warehouseId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
             "creatorDeviceId":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","clientOrderId":"44444444-4444-4444-8444-444444444444",
             "submissionId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","workRound":"3","scopeRound":"3",
             "originalOperatorId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","freezeDeviceId":"cccccccc-cccc-4ccc-8ccc-cccccccccccc",
             "authorizationId":"22222222-2222-4222-8222-222222222222","sourceRecoveryEpoch":"4",
             "currentSubmissionId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","canonicalOrderId":null}
            """));
        String openBody="""
            {"operation":"OPEN","manifestId":"manifest","manifestVersion":2,"snapshotId":"snapshot",
             "source":"TEMP","sourceId":"22222222-2222-4222-8222-222222222222",
             "submissionId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","workRound":3}
            """;
        open=SourceEvidenceIntakeOpen.parse(openBody.getBytes(StandardCharsets.UTF_8),SUBMISSION,3);
        files=spy(new SourceEvidenceIntakeFiles(directory.toString()));
        service=new SourceEvidenceIntakePageService(identity,transactions,opens,pages,recovery,codec,facts,locks,
            evidenceLocks,objects,itemWriter,files,serverState);
        when(itemWriter.preflight(any(),any())).thenReturn(new SourceEvidenceIntakePageItemWriter.Prepared(List.of()));
        when(identity.current(WAREHOUSE)).thenReturn(actor);
        when(facts.read(SUBMISSION)).thenReturn(context);
        when(opens.openBound(bound(OPEN_KEY))).thenReturn(open);
        when(pages.existing(any(),any())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.NOT_FOUND,null));
        byte[] response="{\"operationKind\":\"PAGE\",\"status\":\"PARTIAL\"}".getBytes(StandardCharsets.UTF_8);
        receipt=new SourceEvidenceIntakeReceiptCodec.Receipt(UUID.randomUUID(),response,ImportFiles.sha256(response),
            "kid","token",Instant.EPOCH);
        when(codec.page(any(),eq(PAGE_KEY),eq(open),any(),any(),any(),any())).thenReturn(receipt);
        when(objects.insert(any(),eq(1L),any(),any())).thenAnswer(invocation -> {
            SourceEvidenceIntakePageObjects.Binding binding=invocation.getArgument(0);
            SourceEvidenceIntakeFiles.Stored stored=invocation.getArgument(2);
            return new SourceEvidenceIntakePageObjects.PageObject(UUID.randomUUID(),
                SourceEvidenceIntakePageObjects.originalId(binding.openKey(),1),stored);
        });
        doAnswer(invocation -> ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null)).when(transactions).read(any());
        doAnswer(invocation -> ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null)).when(transactions).write(any());
    }

    @Test void newPagePersistsFileThenStagesItemsBeforePartialReceipt() throws Exception {
        var response=service.page(request(1,1,1));

        assertThat(response).containsExactly(receipt.originalResponseBytes());
        var order=inOrder(files,transactions,locks,evidenceLocks,objects,itemWriter,codec,pages);
        order.verify(pages).existing(eq(bound(PAGE_KEY)),any());
        order.verify(files).storePage(any());
        order.verify(transactions).write(any());
        order.verify(locks).identity(actor,context);
        order.verify(locks).source(context);
        order.verify(evidenceLocks).source(context);
        order.verify(objects).insert(any(),eq(1L),any(),any());
        order.verify(itemWriter).stageNew(any(),any(),any(),any());
        order.verify(codec).page(any(),eq(PAGE_KEY),eq(open),any(),any(),any(),any());
        order.verify(pages).insert(any(),eq(receipt));
    }

    @Test void originalKeyReplayNeverWritesAnotherPage() throws Exception {
        byte[] original="{\"original\":true}".getBytes(StandardCharsets.UTF_8);
        when(pages.existing(any(),any())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.REPLAY,original));

        assertThat(service.page(request(1,1,0))).containsExactly(original);

        verify(transactions,never()).write(any());
        verify(files,never()).storePage(any());
        verify(opens,never()).openBound(any());
    }

    @Test void missingRawItemFailsBeforeFileOrReceipt() throws Exception {
        var request=request(1,1,1);
        when(itemWriter.preflight(any(),any())).thenThrow(new BusinessException(409,"E2A_RAW_OBJECT_MISSING","缺原始对象"));

        assertThatThrownBy(() -> service.page(request)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_RAW_OBJECT_MISSING"));
        verify(files,never()).storePage(any());
        verify(transactions,never()).write(any());
    }

    @Test void missingPredecessorCannotInsertLaterPage() {
        var request=request(2,2,0);

        assertThatThrownBy(() -> service.page(request)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_PAGE_ORDER"));
        verify(objects,never()).insert(any(),anyLong(),any(),any());
        verify(pages,never()).insert(any(),any());
    }

    @Test void mismatchedPredecessorHashCannotInsertPage() throws Exception {
        var first=SourceEvidenceIntakePage.parse(request(1,2,0).requestBytes(),OPEN_KEY,open);
        var stored=files.storePage(first.pageCoreBytes());
        when(objects.findAfterSourceLock(any(),eq(1L))).thenReturn(new SourceEvidenceIntakePageObjects.PageObject(
            UUID.randomUUID(),SourceEvidenceIntakePageObjects.originalId(OPEN_KEY,1),stored));

        assertThatThrownBy(() -> service.page(request(2,2,0))).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_PAGE_PREV_HASH"));
        verify(pages,never()).insert(any(),any());
    }

    @Test void conflictingStoredPageCannotReceiveAnotherReceipt() throws Exception {
        var stored=files.storePage("different page".getBytes(StandardCharsets.UTF_8));
        when(objects.findAfterSourceLock(any(),eq(1L))).thenReturn(new SourceEvidenceIntakePageObjects.PageObject(
            UUID.randomUUID(),SourceEvidenceIntakePageObjects.originalId(OPEN_KEY,1),stored));

        assertThatThrownBy(() -> service.page(request(1,1,0))).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_PAGE_CONFLICT"));
        verify(pages,never()).insert(any(),any());
    }

    private SourceEvidenceIntakePageService.PageRequest request(int pageNo,int pageCount,int items) {
        var core=SubmissionJson.object().put("manifestId","manifest").put("manifestVersion",2)
            .put("snapshotId","snapshot").put("pageNo",pageNo).put("pageCount",pageCount)
            .put("isFinal",pageNo==pageCount).put("itemCount",items).put("byteCount",items);
        if(pageNo==1)core.putNull("prevPageHash");else core.put("prevPageHash","a".repeat(64));
        var entries=core.putArray("items");
        if(items>0)entries.add(SubmissionJson.object().put("itemKind","CAPTURE")
            .put("originalRecordId",UUID.randomUUID().toString()).put("objectKind","CAPTURE_RECORDS")
            .put("originalObjectId",UUID.randomUUID().toString()).put("itemBytesSha256","a".repeat(64))
            .put("byteCount",1).put("originalDeviceSeq",1).put("recordSha256","b".repeat(64))
            .put("recordStatus","CAPTURED").putNull("baleId").putNull("originalCycle").putNull("captureId"));
        var body=SubmissionJson.object().put("operation","PAGE").put("openOriginalKey",OPEN_KEY.toString());
        body.set("pageCore",core);body.put("pageHash",E2aProtocol.pageHash(core));
        return new SourceEvidenceIntakePageService.PageRequest(WAREHOUSE,PAGE_KEY,OPEN_KEY,SUBMISSION,3,"4",
            body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private SourceEvidenceIntakeReceipts.BoundKey bound(UUID originalKey) {
        return new SourceEvidenceIntakeReceipts.BoundKey(OPERATOR,originalKey,WAREHOUSE,SUBMISSION,3,DEVICE,AUTHORIZATION);
    }

    private SourceEvidenceIntakeReplay.Decision decision(SourceEvidenceIntakeReplay.Status status,byte[] response) {
        return new SourceEvidenceIntakeReplay.Decision(status,response,SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
    }
}
