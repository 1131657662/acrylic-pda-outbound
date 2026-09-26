package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import com.acrylic.outbound.temporary.command.CommandLocks;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SourceEvidenceIntakeRawObjectService {
    private final SourceEvidenceIntakeIdentity identity;
    private final SourceEvidenceIntakeTransactions transactions;
    private final SourceEvidenceIntakeFacts facts;
    private final SourceEvidenceIntakeReceipts opens;
    private final CommandLocks locks;
    private final SourceEvidenceIntakeEvidenceLocks evidenceLocks;
    private final SourceEvidenceIntakeObjectFiles files;
    private final SourceEvidenceIntakeRawObjects objects;
    private final ServerState serverState;

    public SourceEvidenceIntakeRawObjectService(SourceEvidenceIntakeIdentity identity,
        SourceEvidenceIntakeTransactions transactions,SourceEvidenceIntakeFacts facts,SourceEvidenceIntakeReceipts opens,
        CommandLocks locks,
        SourceEvidenceIntakeEvidenceLocks evidenceLocks,SourceEvidenceIntakeObjectFiles files,
        SourceEvidenceIntakeRawObjects objects,ServerState serverState) {
        this.identity=identity;this.transactions=transactions;this.facts=facts;this.opens=opens;this.locks=locks;
        this.evidenceLocks=evidenceLocks;this.files=files;this.objects=objects;this.serverState=serverState;
    }

    public SourceEvidenceIntakeRawObjects.RawObject store(RawRequest request) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("INTAKE_MUST_OWN_TRANSACTION_BOUNDARY");
        var actor=identity.current(request.warehouseId());
        var hint=facts.read(request.submissionId());
        var binding=bound(request,actor,hint);
        var openKey=openKey(request,actor,hint);
        var original=opens.openBound(openKey);
        require(original.sourceId().equals(hint.text("authorizationId")),"E2A_SOURCE_CONTEXT_CHANGED");
        identity.requireRecovery(actor);
        serverState.requireWritable(request.transportRecoveryEpoch());
        SourceEvidenceIntakeObjectFiles.Stored stored;
        try { stored=files.store(request.input(),request.length(),request.sha256()); }
        catch(IOException failure) { throw unavailable(); }
        return transactions.write(status->write(request,actor,hint,binding,openKey,original,stored));
    }

    private SourceEvidenceIntakeRawObjects.RawObject write(RawRequest request,CommandIdentity.Actor initial,
        CommandContext hint,SourceEvidenceIntakeRawObjects.Binding binding,SourceEvidenceIntakeReceipts.BoundKey openKey,
        SourceEvidenceIntakeOpen.Parsed original,SourceEvidenceIntakeObjectFiles.Stored stored) {
        locks.identity(initial,hint);locks.source(hint);
        var locked=facts.read(request.submissionId());locks.sameHint(hint,locked);
        var actor=identity.current(request.warehouseId());
        require(actor.id().equals(initial.id()),"E2A_SOURCE_NOT_OWNER");
        require(bound(request,actor,locked).equals(binding),"E2A_SOURCE_CONTEXT_CHANGED");
        evidenceLocks.source(locked);
        var currentOpen=opens.openBound(openKey);
        require(Arrays.equals(original.canonicalBytes(),currentOpen.canonicalBytes())
            && currentOpen.sourceId().equals(locked.text("authorizationId")),"E2A_OPEN_CHANGED");
        serverState.requireWritable(request.transportRecoveryEpoch());identity.requireRecovery(actor);
        var existing=objects.findAfterSourceLock(binding);
        if(existing!=null) {
            require(existing.stored().equals(stored),"E2A_RAW_OBJECT_CONFLICT");
            try { files.verify(existing.stored()); }
            catch(IOException failure) { throw unavailable(); }
            return existing;
        }
        return objects.insert(binding,stored,Instant.now());
    }

    private SourceEvidenceIntakeRawObjects.Binding bound(RawRequest request,CommandIdentity.Actor actor,
        CommandContext context) {
        require(actor.id().equals(context.id("originalOperatorId"))
            && actor.warehouse().equals(context.id("warehouseId")),"E2A_SOURCE_NOT_OWNER");
        require(actor.pda().deviceId().equals(context.id("creatorDeviceId"))
            && actor.pda().deviceId().equals(context.id("freezeDeviceId")),"E2A_SOURCE_NOT_DEVICE");
        require(context.id("submissionId").equals(request.submissionId())
            && context.id("currentSubmissionId").equals(request.submissionId())
            && context.version("workRound")==request.workRound()
            && context.version("scopeRound")==request.workRound(),"E2A_SOURCE_CONTEXT_CHANGED");
        return new SourceEvidenceIntakeRawObjects.Binding(request.warehouseId(),context.id("scopeId"),
            request.submissionId(),request.workRound(),actor.pda().deviceId(),actor.id(),
            request.kind(),request.originalObjectId());
    }

    private SourceEvidenceIntakeReceipts.BoundKey openKey(RawRequest request,CommandIdentity.Actor actor,
        CommandContext context) {
        return new SourceEvidenceIntakeReceipts.BoundKey(actor.id(),request.openOriginalKey(),request.warehouseId(),
            request.submissionId(),request.workRound(),actor.pda().deviceId(),context.id("authorizationId"));
    }

    private static void require(boolean valid,String code) {
        if(!valid)throw new BusinessException(409,code,"原始材料来源上下文不一致");
    }

    private static BusinessException unavailable() {
        return new BusinessException(409,"E2A_RAW_OBJECT_FILE_UNAVAILABLE","原始材料缺失、损坏或不可持久保存");
    }

    public record RawRequest(UUID warehouseId,UUID submissionId,long workRound,UUID openOriginalKey,
                             UUID originalObjectId,String kind,
                             String transportRecoveryEpoch,int length,String sha256,InputStream input) {
        public RawRequest {
            if(warehouseId==null || submissionId==null || workRound<1 || openOriginalKey==null
                || originalObjectId==null || kind==null
                || transportRecoveryEpoch==null || !transportRecoveryEpoch.matches("0|[1-9][0-9]{0,18}")
                || length<1 || length>33554432 || sha256==null || !sha256.matches("[0-9a-f]{64}") || input==null)
                throw new IllegalArgumentException("E2A_RAW_OBJECT_REQUEST");
        }
    }
}
