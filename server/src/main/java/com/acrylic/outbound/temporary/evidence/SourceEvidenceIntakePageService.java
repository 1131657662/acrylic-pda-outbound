package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import com.acrylic.outbound.temporary.command.CommandLocks;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SourceEvidenceIntakePageService {
    private final SourceEvidenceIntakeIdentity identity;
    private final SourceEvidenceIntakeTransactions transactions;
    private final SourceEvidenceIntakeReceipts opens;
    private final SourceEvidenceIntakePageReceipts pages;
    private final SourceEvidenceIntakeRecovery recovery;
    private final SourceEvidenceIntakeReceiptCodec codec;
    private final SourceEvidenceIntakeFacts facts;
    private final CommandLocks locks;
    private final SourceEvidenceIntakeEvidenceLocks evidenceLocks;
    private final SourceEvidenceIntakePageObjects objects;
    private final SourceEvidenceIntakePageItemWriter itemWriter;
    private final SourceEvidenceIntakeFiles files;
    private final ServerState serverState;

    public SourceEvidenceIntakePageService(SourceEvidenceIntakeIdentity identity,SourceEvidenceIntakeTransactions transactions,
        SourceEvidenceIntakeReceipts opens,SourceEvidenceIntakePageReceipts pages,SourceEvidenceIntakeRecovery recovery,
        SourceEvidenceIntakeReceiptCodec codec,SourceEvidenceIntakeFacts facts,CommandLocks locks,
        SourceEvidenceIntakeEvidenceLocks evidenceLocks,SourceEvidenceIntakePageObjects objects,
        SourceEvidenceIntakePageItemWriter itemWriter,SourceEvidenceIntakeFiles files,ServerState serverState) {
        this.identity=identity;this.transactions=transactions;this.opens=opens;this.pages=pages;this.recovery=recovery;
        this.codec=codec;this.facts=facts;this.locks=locks;this.evidenceLocks=evidenceLocks;this.objects=objects;
        this.itemWriter=itemWriter;this.files=files;this.serverState=serverState;
    }

    public byte[] page(PageRequest request) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("INTAKE_MUST_OWN_TRANSACTION_BOUNDARY");
        var actor=identity.current(request.warehouseId());
        var hint=facts.read(request.submissionId());
        var key=bound(request.originalKey(),request,actor,hint);
        var first=transactions.read(status->pages.existing(key,request.requestBytes()));
        if(first.status()!=SourceEvidenceIntakeReplay.Status.NOT_FOUND)return result(first);
        var openKey=bound(request.openOriginalKey(),request,actor,hint);
        var open=transactions.read(status->opens.openBound(openKey));
        var parsed=SourceEvidenceIntakePage.parse(request.requestBytes(),request.openOriginalKey(),open);
        validate(request,actor,hint,open);
        identity.requireRecovery(actor);
        serverState.requireWritable(request.transportRecoveryEpoch());
        var binding=new SourceEvidenceIntakePageObjects.Binding(request.warehouseId(),hint.id("scopeId"),
            open.submissionId(),open.workRound(),hint.id("creatorDeviceId"),actor.id(),request.openOriginalKey());
        var prepared=itemWriter.preflight(binding,parsed);
        SourceEvidenceIntakeFiles.Stored stored;
        try { stored=files.storePage(parsed.pageCoreBytes()); }
        catch(IOException failure) { throw unavailable(); }
        try {
            return transactions.write(status->write(request,actor,hint,key,openKey,open,parsed,stored,prepared));
        } catch(DuplicateKeyException collision) {
            if(!recovery.originalKeyCollision(collision))throw collision;
            var current=bound(request.originalKey(),request,identity.current(request.warehouseId()),facts.read(request.submissionId()));
            return result(pages.recheckAfterRollback(current,parsed.canonicalBytes()));
        }
    }

    private byte[] write(PageRequest request,CommandIdentity.Actor initial,CommandContext hint,
        SourceEvidenceIntakeReceipts.BoundKey key,SourceEvidenceIntakeReceipts.BoundKey openKey,
        SourceEvidenceIntakeOpen.Parsed original,SourceEvidenceIntakePage.Parsed parsed,
        SourceEvidenceIntakeFiles.Stored stored,SourceEvidenceIntakePageItemWriter.Prepared prepared) {
        locks.identity(initial,hint);locks.source(hint);
        var locked=facts.read(request.submissionId());locks.sameHint(hint,locked);
        var actor=identity.current(request.warehouseId());
        require(actor.id().equals(initial.id()),"E2A_SOURCE_NOT_OWNER");
        require(bound(request.originalKey(),request,actor,locked).equals(key),"E2A_SOURCE_CONTEXT_CHANGED");
        evidenceLocks.source(locked);
        var second=pages.existing(key,parsed.canonicalBytes());
        if(second.status()!=SourceEvidenceIntakeReplay.Status.NOT_FOUND)return result(second);
        var currentOpen=opens.openBound(openKey);
        require(Arrays.equals(original.canonicalBytes(),currentOpen.canonicalBytes()),"E2A_OPEN_CHANGED");
        var context=facts.read(request.submissionId());locks.sameHint(hint,context);
        validate(request,actor,context,currentOpen);
        serverState.requireWritable(request.transportRecoveryEpoch());identity.requireRecovery(actor);
        var binding=new SourceEvidenceIntakePageObjects.Binding(request.warehouseId(),context.id("scopeId"),
            currentOpen.submissionId(),currentOpen.workRound(),context.id("creatorDeviceId"),actor.id(),request.openOriginalKey());
        previous(binding,parsed,currentOpen);
        var existing=objects.findAfterSourceLock(binding,parsed.pageNo());
        SourceEvidenceIntakePageObjects.PageObject object;
        if(existing==null) {
            object=objects.insert(binding,parsed.pageNo(),stored,Instant.now());
            itemWriter.stageNew(binding,parsed,object,prepared);
        } else {
            object=sameObject(existing,stored);
            itemWriter.verifyExisting(binding,parsed,object,prepared);
        }
        var input=new SourceEvidenceIntakePageReceipts.PageInsert(insert(context,actor,parsed,request.originalKey()),object.id());
        var receipt=codec.page(Ids.next(),request.originalKey(),currentOpen,parsed,input,object,Instant.now());
        pages.insert(input,receipt);
        return receipt.originalResponseBytes();
    }

    private void previous(SourceEvidenceIntakePageObjects.Binding binding,SourceEvidenceIntakePage.Parsed page,
        SourceEvidenceIntakeOpen.Parsed open) {
        if(page.pageNo()==1)return;
        var prior=objects.findAfterSourceLock(binding,page.pageNo()-1);
        require(prior!=null,"E2A_PAGE_ORDER");
        try {
            var node=SubmissionJson.parse(files.readPage(prior.stored()));
            if(!(node instanceof ObjectNode core))throw new IllegalArgumentException("PAGE_CORE");
            require(E2aProtocol.pageHash(core).equals(page.prevPageHash()),"E2A_PAGE_PREV_HASH");
            require(core.path("pageNo").longValue()==page.pageNo()-1
                && core.path("pageCount").longValue()==page.pageCount()
                && core.path("manifestId").asText().equals(open.manifestId())
                && core.path("manifestVersion").longValue()==open.manifestVersion()
                && core.path("snapshotId").asText().equals(open.snapshotId()),"E2A_PAGE_CONTEXT");
        } catch(IOException | IllegalArgumentException failure) { throw unavailable(); }
    }

    private SourceEvidenceIntakePageObjects.PageObject sameObject(SourceEvidenceIntakePageObjects.PageObject existing,
        SourceEvidenceIntakeFiles.Stored stored) {
        require(existing.stored().equals(stored),"E2A_PAGE_CONFLICT");
        try { files.verify(existing.stored()); }
        catch(IOException failure) { throw unavailable(); }
        return existing;
    }

    private void validate(PageRequest request,CommandIdentity.Actor actor,CommandContext context,
        SourceEvidenceIntakeOpen.Parsed open) {
        require(actor.id().equals(context.id("originalOperatorId")) && actor.warehouse().equals(context.id("warehouseId")),
            "E2A_SOURCE_NOT_OWNER");
        require(actor.pda().deviceId().equals(context.id("creatorDeviceId"))
            && actor.pda().deviceId().equals(context.id("freezeDeviceId")),"E2A_SOURCE_NOT_DEVICE");
        require(open.sourceId().equals(context.text("authorizationId"))
            && open.submissionId().equals(request.submissionId()) && open.workRound()==request.workRound()
            && context.version("workRound")==request.workRound() && context.version("scopeRound")==request.workRound()
            && (request.submissionId().equals(context.id("currentSubmissionId")) || facts.currentOrPendingHandover(context,request.submissionId())),"E2A_SOURCE_CONTEXT_CHANGED");
    }

    private SourceEvidenceIntakeReceipts.BoundKey bound(UUID originalKey,PageRequest request,CommandIdentity.Actor actor,
        CommandContext hint) {
        require(actor.id().equals(hint.id("originalOperatorId")) && actor.warehouse().equals(hint.id("warehouseId")),
            "E2A_SOURCE_NOT_OWNER");
        require(actor.pda().deviceId().equals(hint.id("creatorDeviceId")),"E2A_SOURCE_NOT_DEVICE");
        return new SourceEvidenceIntakeReceipts.BoundKey(actor.id(),originalKey,request.warehouseId(),request.submissionId(),
            request.workRound(),hint.id("creatorDeviceId"),hint.id("authorizationId"));
    }

    private SourceEvidenceIntakeReceipts.OpenInsert insert(CommandContext context,CommandIdentity.Actor actor,
        SourceEvidenceIntakePage.Parsed page,UUID key) {
        return new SourceEvidenceIntakeReceipts.OpenInsert(actor.id(),key,context.id("warehouseId"),context.id("scopeId"),
            context.id("submissionId"),context.version("workRound"),context.id("creatorDeviceId"),
            context.id("clientOrderId"),context.id("authorizationId"),context.id("originalOperatorId"),
            Long.parseLong(context.text("sourceRecoveryEpoch")),actor.pda().sessionId(),actor.authVersion(),
            actor.pda().deviceVersion(),page.canonicalBytes(),page.requestSha256(),context.id("scopeClientOrderId"));
    }

    private byte[] result(SourceEvidenceIntakeReplay.Decision decision) {
        return switch(decision.status()) {
            case REPLAY -> decision.originalResponseBytes();
            case CONFLICT -> throw new BusinessException(409,"E2A_INTAKE_CONFLICT","同一原键对应不同PAGE请求");
            case INTEGRITY_FAILURE -> throw new BusinessException(409,"E2A_INTAKE_INTEGRITY_FAILURE","原PAGE回执损坏");
            case UNKNOWN,NOT_FOUND -> throw new BusinessException(409,"E2A_INTAKE_RESULT_UNKNOWN","PAGE结果不可确认");
        };
    }

    private static void require(boolean condition,String code) {
        if(!condition)throw new BusinessException(409,code,"页与原来源上下文不一致");
    }
    private static BusinessException unavailable() {
        return new BusinessException(409,"E2A_PAGE_FILE_UNAVAILABLE","页文件缺失、损坏或不可持久保存");
    }

    public record PageRequest(UUID warehouseId,UUID originalKey,UUID openOriginalKey,UUID submissionId,long workRound,
                              String transportRecoveryEpoch,byte[] requestBytes) {
        public PageRequest {
            if(warehouseId==null || originalKey==null || openOriginalKey==null || originalKey.equals(openOriginalKey)
                || submissionId==null || workRound<1 || transportRecoveryEpoch==null
                || !transportRecoveryEpoch.matches("0|[1-9][0-9]{0,18}") || requestBytes==null)
                throw new IllegalArgumentException("E2A_PAGE_REQUEST");
            requestBytes=requestBytes.clone();
        }
        @Override public byte[] requestBytes() { return requestBytes.clone(); }
    }
}
