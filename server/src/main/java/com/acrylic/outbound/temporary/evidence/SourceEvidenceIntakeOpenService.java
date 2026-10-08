package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import com.acrylic.outbound.temporary.command.CommandLocks;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SourceEvidenceIntakeOpenService {
    private final SourceEvidenceIntakeIdentity identity;
    private final SourceEvidenceIntakeTransactions transactions;
    private final SourceEvidenceIntakeReceipts receipts;
    private final SourceEvidenceIntakeRecovery recovery;
    private final SourceEvidenceIntakeReceiptCodec codec;
    private final SourceEvidenceIntakeFacts facts;
    private final CommandLocks locks;
    private final ServerState serverState;

    public SourceEvidenceIntakeOpenService(SourceEvidenceIntakeIdentity identity,SourceEvidenceIntakeTransactions transactions,
        SourceEvidenceIntakeReceipts receipts,SourceEvidenceIntakeRecovery recovery,SourceEvidenceIntakeReceiptCodec codec,
        SourceEvidenceIntakeFacts facts,CommandLocks locks,ServerState serverState) {
        this.identity=identity;this.transactions=transactions;this.receipts=receipts;this.recovery=recovery;this.codec=codec;
        this.facts=facts;this.locks=locks;this.serverState=serverState;
    }

    public byte[] open(OpenRequest request) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("INTAKE_MUST_OWN_TRANSACTION_BOUNDARY");
        var parsed=SourceEvidenceIntakeOpen.parse(request.requestBytes(),request.submissionId(),request.workRound());
        var actor=identity.current(request.warehouseId());
        var hint=facts.read(parsed.submissionId());
        var key=bound(request,actor,hint,parsed);
        var original=transactions.read(status->receipts.existingBound(key,parsed.canonicalBytes()));
        if(original.status()!=SourceEvidenceIntakeReplay.Status.NOT_FOUND)return result(original);
        try {
            return transactions.write(status->write(request,parsed,actor,hint,key));
        } catch(DuplicateKeyException collision) {
            if(!recovery.originalKeyCollision(collision))throw collision;
            return result(recovery.resolveBoundAfterRollback(collision,
                bound(request,identity.current(request.warehouseId()),facts.read(parsed.submissionId()),parsed),parsed.canonicalBytes()));
        }
    }

    private byte[] write(OpenRequest request,SourceEvidenceIntakeOpen.Parsed parsed,CommandIdentity.Actor initialActor,
                         CommandContext hint,SourceEvidenceIntakeReceipts.BoundKey key) {
        locks.identity(initialActor,hint);
        locks.source(hint);
        var lockedHint=facts.read(parsed.submissionId());
        locks.sameHint(hint,lockedHint);
        var actor=identity.current(request.warehouseId());
        require(actor.id().equals(initialActor.id()),"E2A_SOURCE_NOT_OWNER");
        var after=bound(request,actor,lockedHint,parsed);
        require(after.equals(key),"E2A_SOURCE_CONTEXT_CHANGED");
        var second=receipts.existingBoundLocked(key,parsed.canonicalBytes());
        if(second.status()!=SourceEvidenceIntakeReplay.Status.NOT_FOUND)return result(second);
        var context=facts.read(parsed.submissionId());
        locks.sameHint(hint,context);
        serverState.requireWritable(request.transportRecoveryEpoch());
        validate(request,parsed,actor,context);
        var insertion=insert(context,actor,parsed,request.originalKey());
        var receipt=codec.open(Ids.next(),request.originalKey(),parsed,insertion,Instant.now());
        receipts.insertOpen(insertion,receipt);
        return receipt.originalResponseBytes();
    }

    private void validate(OpenRequest request,SourceEvidenceIntakeOpen.Parsed parsed,CommandIdentity.Actor actor,CommandContext context) {
        identity.requireRecovery(actor);
        require(actor.id().equals(context.id("originalOperatorId")),"E2A_SOURCE_NOT_OWNER");
        require(actor.pda().deviceId().equals(context.id("freezeDeviceId")),"E2A_SOURCE_NOT_DEVICE");
        require(parsed.sourceId().equals(context.text("authorizationId")),"E2A_SOURCE_ID_CHANGED");
        require(context.id("submissionId").equals(parsed.submissionId()),"E2A_SOURCE_SUBMISSION_CHANGED");
        require(context.version("workRound")==parsed.workRound() && context.version("scopeRound")==parsed.workRound(),"E2A_SOURCE_ROUND_CHANGED");
        require(parsed.submissionId().equals(context.id("currentSubmissionId"))
            || facts.currentOrPendingHandover(context,parsed.submissionId()),"E2A_SOURCE_NOT_CURRENT");
        require(context.id("warehouseId").equals(request.warehouseId()),"E2A_SOURCE_WAREHOUSE_CHANGED");
    }

    private SourceEvidenceIntakeReceipts.BoundKey bound(OpenRequest request,CommandIdentity.Actor actor,
        CommandContext hint,SourceEvidenceIntakeOpen.Parsed parsed) {
        require(actor.id().equals(hint.id("originalOperatorId")) && actor.warehouse().equals(hint.id("warehouseId")),
            "E2A_SOURCE_NOT_OWNER");
        require(actor.pda().deviceId().equals(hint.id("creatorDeviceId")),"E2A_SOURCE_NOT_DEVICE");
        require(parsed.sourceId().equals(hint.text("authorizationId")),"E2A_SOURCE_ID_CHANGED");
        return new SourceEvidenceIntakeReceipts.BoundKey(actor.id(),request.originalKey(),request.warehouseId(),
            parsed.submissionId(),parsed.workRound(),hint.id("creatorDeviceId"),hint.id("authorizationId"));
    }

    private SourceEvidenceIntakeReceipts.OpenInsert insert(CommandContext context,CommandIdentity.Actor actor,
        SourceEvidenceIntakeOpen.Parsed parsed,UUID originalKey) {
        return new SourceEvidenceIntakeReceipts.OpenInsert(context.id("originalOperatorId"),originalKey,context.id("warehouseId"),
            context.id("scopeId"),parsed.submissionId(),parsed.workRound(),context.id("creatorDeviceId"),context.id("clientOrderId"),
            context.id("authorizationId"),actor.id(),Long.parseLong(context.text("sourceRecoveryEpoch")),
            actor.pda().sessionId(),actor.authVersion(),actor.pda().deviceVersion(),parsed.canonicalBytes(),parsed.requestSha256(),context.id("scopeClientOrderId"));
    }

    private byte[] result(SourceEvidenceIntakeReplay.Decision decision) {
        return switch(decision.status()) {
            case REPLAY -> Arrays.copyOf(decision.originalResponseBytes(),decision.originalResponseBytes().length);
            case CONFLICT -> throw new BusinessException(409,"E2A_INTAKE_CONFLICT","同一原键对应不同OPEN请求");
            case INTEGRITY_FAILURE -> throw new BusinessException(409,"E2A_INTAKE_INTEGRITY_FAILURE","原OPEN回执完整性失败");
            case UNKNOWN,NOT_FOUND -> throw new BusinessException(409,"E2A_INTAKE_RESULT_UNKNOWN","原OPEN结果不可确认，请保留原意图并查询");
        };
    }

    private static void require(boolean valid,String code) { if(!valid)throw new BusinessException(409,code,"原来源上下文已变化"); }

    public record OpenRequest(UUID warehouseId,UUID originalKey,UUID submissionId,long workRound,String transportRecoveryEpoch,byte[] requestBytes) {
        public OpenRequest {
            if(warehouseId==null || originalKey==null || submissionId==null || workRound<1 || transportRecoveryEpoch==null
                || !transportRecoveryEpoch.matches("0|[1-9][0-9]{0,18}") || requestBytes==null)throw new IllegalArgumentException("E2A_OPEN_REQUEST");
            requestBytes=requestBytes.clone();
        }
        @Override public byte[] requestBytes() { return requestBytes.clone(); }
    }
}
