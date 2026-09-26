package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import com.acrylic.outbound.temporary.command.CommandLocks;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeRawObjectServiceTest {
    private static final UUID WAREHOUSE=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID SUBMISSION=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID OPERATOR=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID DEVICE=UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final UUID OBJECT=UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    private static final UUID OPEN=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID AUTHORIZATION=UUID.fromString("22222222-2222-4222-8222-222222222222");
    @TempDir Path directory;
    private final SourceEvidenceIntakeIdentity identity=mock(SourceEvidenceIntakeIdentity.class);
    private final SourceEvidenceIntakeTransactions transactions=mock(SourceEvidenceIntakeTransactions.class);
    private final SourceEvidenceIntakeFacts facts=mock(SourceEvidenceIntakeFacts.class);
    private final SourceEvidenceIntakeReceipts opens=mock(SourceEvidenceIntakeReceipts.class);
    private final CommandLocks locks=mock(CommandLocks.class);
    private final SourceEvidenceIntakeEvidenceLocks evidenceLocks=mock(SourceEvidenceIntakeEvidenceLocks.class);
    private final SourceEvidenceIntakeRawObjects objects=mock(SourceEvidenceIntakeRawObjects.class);
    private final ServerState state=mock(ServerState.class);
    private SourceEvidenceIntakeObjectFiles files;
    private SourceEvidenceIntakeRawObjectService service;
    private CommandIdentity.Actor actor;

    @BeforeEach void setUp() {
        var principal=new PdaSessions.Principal(UUID.randomUUID(),OPERATOR,"operator",DEVICE,WAREHOUSE,"7","2");
        actor=new CommandIdentity.Actor(OPERATOR,WAREHOUSE,"7",principal,List.of("pda:recover"),List.of(WAREHOUSE.toString()));
        when(identity.current(WAREHOUSE)).thenReturn(actor);
        when(facts.read(SUBMISSION)).thenReturn(context(OPERATOR));
        var open=SourceEvidenceIntakeOpen.parse("""
            {"operation":"OPEN","manifestId":"manifest","manifestVersion":1,"snapshotId":"snapshot",
             "source":"TEMP","sourceId":"%s","submissionId":"%s","workRound":3}
            """.formatted(AUTHORIZATION,SUBMISSION).getBytes(StandardCharsets.UTF_8),SUBMISSION,3);
        when(opens.openBound(any())).thenReturn(open);
        files=spy(new SourceEvidenceIntakeObjectFiles(directory.toString()));
        service=new SourceEvidenceIntakeRawObjectService(identity,transactions,facts,opens,locks,evidenceLocks,files,objects,state);
        doAnswer(invocation -> ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null))
            .when(transactions).write(any());
    }

    @Test void durableFilePrecedesLockedShortDatabaseInsert() throws Exception {
        when(objects.insert(any(),any(),any())).thenAnswer(invocation -> {
            SourceEvidenceIntakeRawObjects.Binding binding=invocation.getArgument(0);
            SourceEvidenceIntakeObjectFiles.Stored stored=invocation.getArgument(1);
            return new SourceEvidenceIntakeRawObjects.RawObject(UUID.randomUUID(),binding.originalObjectId(),stored);
        });

        var result=service.store(request("original"));

        assertThat(result.originalObjectId()).isEqualTo(OBJECT);
        var order=inOrder(files,transactions,locks,evidenceLocks,objects);
        order.verify(files).store(any(),any(Integer.class),any());
        order.verify(transactions).write(any());
        order.verify(locks).identity(any(),any());
        order.verify(locks).source(any());
        order.verify(evidenceLocks).source(any());
        order.verify(objects).insert(any(),any(),any());
    }

    @Test void identicalOriginalObjectIsIdempotentAndVerifiesDurableFile() throws Exception {
        byte[] bytes="original".getBytes(StandardCharsets.UTF_8);
        var stored=files.store(new ByteArrayInputStream(bytes),bytes.length,ImportFiles.sha256(bytes));
        var existing=new SourceEvidenceIntakeRawObjects.RawObject(UUID.randomUUID(),OBJECT,stored);
        when(objects.findAfterSourceLock(any())).thenReturn(existing);

        assertThat(service.store(request("original"))).isEqualTo(existing);

        verify(files).verify(stored);
        verify(objects,never()).insert(any(),any(),any());
    }

    @Test void conflictingOriginalObjectNeverOverwritesOrInserts() {
        var stored=new SourceEvidenceIntakeObjectFiles.Stored(
            "source-evidence-intake/objects/"+"0".repeat(64)+".bin","0".repeat(64),8);
        when(objects.findAfterSourceLock(any())).thenReturn(new SourceEvidenceIntakeRawObjects.RawObject(UUID.randomUUID(),OBJECT,stored));

        assertThatThrownBy(() -> service.store(request("original"))).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_RAW_OBJECT_CONFLICT"));
        verify(objects,never()).insert(any(),any(),any());
    }

    @Test void changedSourceOwnerAfterFileWriteIsRejectedBeforeInsert() {
        when(facts.read(SUBMISSION)).thenReturn(context(OPERATOR),context(UUID.randomUUID()));

        assertThatThrownBy(() -> service.store(request("original"))).isInstanceOf(BusinessException.class);
        verify(objects,never()).insert(any(),any(),any());
    }

    @Test void mismatchedBytesNeverReachTheDatabase() {
        var original=request("original");
        var forged=new SourceEvidenceIntakeRawObjectService.RawRequest(original.warehouseId(),
            original.submissionId(),original.workRound(),original.openOriginalKey(),original.originalObjectId(),original.kind(),
            original.transportRecoveryEpoch(),original.length(),"0".repeat(64),original.input());

        assertThatThrownBy(() -> service.store(forged)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_RAW_OBJECT_FILE_UNAVAILABLE"));
        verify(transactions,never()).write(any());
    }

    @Test void missingOpenCannotStoreAnyRawBytes() throws Exception {
        when(opens.openBound(any())).thenThrow(new BusinessException(404,"E2A_OPEN_NOT_FOUND","未找到OPEN"));

        assertThatThrownBy(() -> service.store(request("original"))).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_OPEN_NOT_FOUND"));
        verify(files,never()).store(any(),any(Integer.class),any());
        verify(transactions,never()).write(any());
    }

    @Test void changedOpenAfterFileWriteCannotInsertObject() {
        var different=SourceEvidenceIntakeOpen.parse("""
            {"operation":"OPEN","manifestId":"different","manifestVersion":1,"snapshotId":"snapshot",
             "source":"TEMP","sourceId":"%s","submissionId":"%s","workRound":3}
            """.formatted(AUTHORIZATION,SUBMISSION).getBytes(StandardCharsets.UTF_8),SUBMISSION,3);
        var initial=opens.openBound(new SourceEvidenceIntakeReceipts.BoundKey(OPERATOR,OPEN,WAREHOUSE,SUBMISSION,
            3,DEVICE,AUTHORIZATION));
        when(opens.openBound(any())).thenReturn(initial,different);

        assertThatThrownBy(() -> service.store(request("original"))).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_OPEN_CHANGED"));
        verify(objects,never()).insert(any(),any(),any());
    }

    private SourceEvidenceIntakeRawObjectService.RawRequest request(String text) {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        return new SourceEvidenceIntakeRawObjectService.RawRequest(WAREHOUSE,SUBMISSION,3,OPEN,OBJECT,"CAPTURE_RECORDS",
            "4",bytes.length,ImportFiles.sha256(bytes),new ByteArrayInputStream(bytes));
    }

    private CommandContext context(UUID operator) {
        return new CommandContext(SubmissionJson.parse("""
            {"scopeId":"ffffffff-ffff-4fff-8fff-ffffffffffff","warehouseId":"%s",
             "submissionId":"%s","currentSubmissionId":"%s","workRound":"3","scopeRound":"3",
             "originalOperatorId":"%s","creatorDeviceId":"%s","freezeDeviceId":"%s",
             "authorizationId":"%s"}
            """.formatted(WAREHOUSE,SUBMISSION,SUBMISSION,operator,DEVICE,DEVICE,AUTHORIZATION)));
    }
}
