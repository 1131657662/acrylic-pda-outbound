package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import com.acrylic.outbound.temporary.command.CommandLocks;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeOpenServiceTest {
    private static final UUID WAREHOUSE=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID OPERATOR=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID DEVICE=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID SESSION=UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final UUID SUBMISSION=UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    private static final UUID ORIGINAL_KEY=UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
    private static final UUID SCOPE=UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLIENT=UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID AUTHORIZATION=UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String REQUEST="""
        {"operation":"OPEN","manifestId":"manifest-1","manifestVersion":7,"snapshotId":"snapshot-1",
         "source":"TEMP","sourceId":"33333333-3333-4333-8333-333333333333","submissionId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","workRound":2}
        """;
    private final SourceEvidenceIntakeIdentity identity=mock(SourceEvidenceIntakeIdentity.class);
    private final SourceEvidenceIntakeTransactions transactions=mock(SourceEvidenceIntakeTransactions.class);
    private final SourceEvidenceIntakeReceipts receipts=mock(SourceEvidenceIntakeReceipts.class);
    private final SourceEvidenceIntakeRecovery recovery=mock(SourceEvidenceIntakeRecovery.class);
    private final SourceEvidenceIntakeReceiptCodec codec=mock(SourceEvidenceIntakeReceiptCodec.class);
    private final SourceEvidenceIntakeFacts facts=mock(SourceEvidenceIntakeFacts.class);
    private final CommandLocks locks=mock(CommandLocks.class);
    private final ServerState serverState=mock(ServerState.class);
    private SourceEvidenceIntakeOpenService service;
    private CommandIdentity.Actor actor;
    private CommandContext hint;
    private CommandContext context;
    private SourceEvidenceIntakeReceiptCodec.Receipt receipt;

    @BeforeEach void setUp() {
        var principal=new PdaSessions.Principal(SESSION,OPERATOR,"operator",DEVICE,WAREHOUSE,"7","1");
        actor=new CommandIdentity.Actor(OPERATOR,WAREHOUSE,"7",principal,List.of("pda:recover"),List.of(WAREHOUSE.toString()));
        hint=new CommandContext(SubmissionJson.parse("""
            {"scopeId":"11111111-1111-4111-8111-111111111111","warehouseId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
             "creatorDeviceId":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","clientOrderId":"22222222-2222-4222-8222-222222222222",
             "scopeClientOrderId":"22222222-2222-4222-8222-222222222222",
             "submissionId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","originalOperatorId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
             "authorizationId":"33333333-3333-4333-8333-333333333333","canonicalOrderId":null}
            """));
        context=new CommandContext(SubmissionJson.parse("""
            {"scopeId":"11111111-1111-4111-8111-111111111111","warehouseId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
             "creatorDeviceId":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","clientOrderId":"22222222-2222-4222-8222-222222222222",
             "scopeClientOrderId":"22222222-2222-4222-8222-222222222222",
             "submissionId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","workRound":"2","scopeRound":"2","status":"NORMAL",
             "authorizationId":"33333333-3333-4333-8333-333333333333","authorizationUserVersion":"7",
             "freezeDeviceId":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","originalOperatorId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
             "sourceRecoveryEpoch":"3","currentSubmissionId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"}
            """));
        when(identity.current(WAREHOUSE)).thenReturn(actor);
        when(facts.read(SUBMISSION)).thenReturn(hint,hint,context);
        when(receipts.existingBound(bound(),canonical())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.NOT_FOUND,null));
        when(receipts.existingBoundLocked(bound(),canonical())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.NOT_FOUND,null));
        var response="{\"operationKind\":\"OPEN\",\"status\":\"RECEIVED\"}".getBytes(StandardCharsets.UTF_8);
        receipt=new SourceEvidenceIntakeReceiptCodec.Receipt(UUID.randomUUID(),response,ImportFiles.sha256(response),"kid","token",Instant.EPOCH);
        when(codec.open(any(),eq(ORIGINAL_KEY),any(),any(),any())).thenReturn(receipt);
        service=new SourceEvidenceIntakeOpenService(identity,transactions,receipts,recovery,codec,facts,locks,serverState);
        runCallbacks();
    }

    @Test void newOpenQueriesBeforeLocksThenWritesOnlyAnEnvelope() {
        var result=service.open(request());

        assertThat(result).isEqualTo(codecResponse());
        InOrder order=inOrder(identity,transactions,receipts,facts,locks,serverState,codec);
        order.verify(identity).current(WAREHOUSE);
        order.verify(facts).read(SUBMISSION);
        order.verify(transactions).read(any());
        order.verify(receipts).existingBound(bound(),canonical());
        order.verify(transactions).write(any());
        order.verify(locks).identity(actor,hint);
        order.verify(locks).source(hint);
        order.verify(facts).read(SUBMISSION);
        order.verify(locks).sameHint(hint,hint);
        order.verify(identity).current(WAREHOUSE);
        order.verify(receipts).existingBoundLocked(bound(),canonical());
        order.verify(facts).read(SUBMISSION);
        order.verify(locks).sameHint(hint,context);
        order.verify(serverState).requireWritable("3");
        order.verify(identity).requireRecovery(actor);
        order.verify(codec).open(any(),eq(ORIGINAL_KEY),any(),
            org.mockito.ArgumentMatchers.argThat(value->value.currentSessionId().equals(SESSION)
                && value.userAuthVersion().equals("7") && value.deviceVersion().equals("1")),any());
        order.verify(receipts).insertOpen(any(),eq(receipt));
    }

    @Test void existingReplayStopsBeforeSourceLocks() {
        byte[] response="{\"original\":true}".getBytes(StandardCharsets.UTF_8);
        when(receipts.existingBound(bound(),canonical())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.REPLAY,response));

        assertThat(service.open(request())).isEqualTo(response);

        verify(transactions,never()).write(any());verify(locks,never()).identity(any(),any());
    }

    @Test void existingConflictDoesNotStartOpenWrite() {
        when(receipts.existingBound(bound(),canonical())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.CONFLICT,null));

        assertThatThrownBy(() -> service.open(request())).isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_INTAKE_CONFLICT"));

        verify(transactions,never()).write(any());
    }

    @Test void duplicateNaturalKeyUsesRecoveryOnlyAfterWriteReturns() {
        var collision=new DuplicateKeyException("duplicate");
        when(recovery.originalKeyCollision(collision)).thenReturn(true);
        doAnswer(invocation -> { throw collision; }).when(transactions).write(any());
        when(recovery.resolveBoundAfterRollback(any(),any(),any())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.REPLAY,codecResponse()));

        assertThat(service.open(request())).isEqualTo(codecResponse());
        verify(recovery).resolveBoundAfterRollback(eq(collision),eq(bound()),eq(canonical()));
        verify(facts,org.mockito.Mockito.times(2)).read(SUBMISSION);
    }

    @Test void supersededSubmissionFailsBeforeReceiptWrite() {
        context=new CommandContext(SubmissionJson.parse(context.json().toString().replace(
            "\"currentSubmissionId\":\"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee\"",
            "\"currentSubmissionId\":\"44444444-4444-4444-8444-444444444444\"")));
        when(facts.read(SUBMISSION)).thenReturn(hint,hint,context);

        assertThatThrownBy(() -> service.open(request())).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        verify(codec,never()).open(any(),any(),any(),any(),any());verify(receipts,never()).insertOpen(any(),any());
    }

    @Test void replayAppearingAfterLocksStopsBeforeNewWritePermission() {
        when(receipts.existingBoundLocked(bound(),canonical())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.REPLAY,codecResponse()));

        assertThat(service.open(request())).isEqualTo(codecResponse());

        verify(serverState,never()).requireWritable(anyString());
        verify(identity,never()).requireRecovery(any());
        verify(receipts,never()).insertOpen(any(),any());
    }

    @Test void outsideTransactionIsRequiredForRollbackRecovery() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.open(request())).isInstanceOf(IllegalStateException.class)
                .hasMessage("INTAKE_MUST_OWN_TRANSACTION_BOUNDARY");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(transactions,never()).write(any());
    }

    @Test void sourceIdMismatchDoesNotQueryReceipt() {
        var wrong=REQUEST.replace(AUTHORIZATION.toString(),UUID.randomUUID().toString());
        var request=new SourceEvidenceIntakeOpenService.OpenRequest(WAREHOUSE,ORIGINAL_KEY,SUBMISSION,2,"3",wrong.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.open(request)).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        verify(transactions,never()).read(any());
    }

    private void runCallbacks() {
        doAnswer(invocation -> ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null)).when(transactions).read(any());
        doAnswer(invocation -> ((TransactionCallback<?>)invocation.getArgument(0)).doInTransaction(null)).when(transactions).write(any());
    }

    private SourceEvidenceIntakeOpenService.OpenRequest request() {
        return new SourceEvidenceIntakeOpenService.OpenRequest(WAREHOUSE,ORIGINAL_KEY,SUBMISSION,2,"3",REQUEST.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] canonical() { return SourceEvidenceIntakeReplay.canonicalize(REQUEST.getBytes(StandardCharsets.UTF_8)).bytes(); }
    private SourceEvidenceIntakeReceipts.BoundKey bound() {
        return new SourceEvidenceIntakeReceipts.BoundKey(OPERATOR,ORIGINAL_KEY,WAREHOUSE,SUBMISSION,2,DEVICE,AUTHORIZATION);
    }
    private byte[] codecResponse() { return receipt.originalResponseBytes(); }
    private SourceEvidenceIntakeReplay.Decision decision(SourceEvidenceIntakeReplay.Status status,byte[] response) {
        return new SourceEvidenceIntakeReplay.Decision(status,response,SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
    }
}
