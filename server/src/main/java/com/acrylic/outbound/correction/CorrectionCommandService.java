package com.acrylic.outbound.correction;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/**
 * Outer commit boundary for the correction chain. Replay is bound to the actor that recorded the original receipt;
 * a same-warehouse principal cannot use a leaked original key to read another actor's response.
 */
@Service
public class CorrectionCommandService {
    private final CorrectionCommand.Identity identities; private final CorrectionCommand.Locks locks;
    private final CorrectionStore store; private final CorrectionReceiptCodec codec; private final CorrectionReadReceipt verifier;
    private final AuditWriter audit; private final ServerState state; private final List<CorrectionCommand.Operation> operations;
    private final TransactionTemplate read; private final TransactionTemplate write;

    public CorrectionCommandService(CorrectionCommand.Identity identities, CorrectionCommand.Locks locks,
        CorrectionStore store, CorrectionReceiptCodec codec, CorrectionReadReceipt verifier, AuditWriter audit,
        ServerState state, ObjectProvider<CorrectionCommand.Operation> operations, PlatformTransactionManager manager) {
        this.identities=identities; this.locks=locks; this.store=store; this.codec=codec; this.verifier=verifier;
        this.audit=audit; this.state=state; this.operations=operations.orderedStream().toList();
        this.read=template(manager,true); this.write=template(manager,false);
    }

    public byte[] execute(byte[] utf8,String requestId) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Correction command execution must own the outer transaction boundary");
        if (requestId==null || !requestId.matches("[A-Za-z0-9._:-]{1,64}"))
            throw new BusinessException(400,"INVALID_REQUEST_ID","请求追踪标识无效");
        var input=CorrectionCommand.Input.parse(utf8);
        if (ReconciliationContext.isBusiness(input.kind())) ReconciliationInput.validate(input.body());
        var actor=identities.current(input.uuid("warehouseId"),CorrectionCommand.requiresApproval(input.kind()));
        byte[] committed=read.execute(s->replay(input,actor));
        if (committed!=null) return committed;
        var operation=operations.stream().filter(o->o.commandType().equals(input.kind())).findFirst()
            .orElseThrow(CorrectionCommand::notOpen);
        try { return Objects.requireNonNull(write.execute(s->executeOnce(input,actor,operation,requestId))); }
        catch (DuplicateKeyException collision) {
            byte[] original=read.execute(s->replay(input,actor));
            if (original!=null) return original;
            throw new BusinessException(409,"CORRECTION_RESULT_UNKNOWN","原键提交结果不可确认，请保留原意图并查询");
        }
    }

    /** Same actor, same key and same canonical bytes replay one verified immutable response. */
    private byte[] replay(CorrectionCommand.Input input,CorrectionCommand.Actor actor) {
        var row=store.receipt(input.key());
        if (row==null) return null;
        Object owner=row.get("actor_id");
        if (!(owner instanceof byte[] bytes) || !Arrays.equals(bytes,Ids.bytes(actor.id()))) throw CorrectionCommand.missing();
        byte[] stored=(byte[])row.get("canonical_command_bytes");
        if (!Arrays.equals(stored,input.bytes())) throw CorrectionCommand.conflict();
        return verifier.verify(row).bytes();
    }

    private byte[] executeOnce(CorrectionCommand.Input input,CorrectionCommand.Actor actor,
        CorrectionCommand.Operation operation,String requestId) {
        state.requireWritable(input.text("transportRecoveryEpoch"));
        operation.beforeLocks(input);
        if (ReconciliationContext.isBusiness(input.kind()))
            actor = identities.current(input.uuid("warehouseId"), CorrectionCommand.requiresApproval(input.kind()));
        JsonNode anchor; UUID documentId=null; UUID orderId=null; UUID caseId=null;
        if (input.kind().equals("CREATE")) {
            orderId=input.uuid("orderId"); locks.order(orderId); anchor=store.orderFacts(orderId);
        } else if (input.kind().startsWith("RECONCILE_")) {
            if (input.kind().equals("RECONCILE_OPEN") || input.kind().endsWith("_DISCOVER")) anchor=SubmissionJson.object().put("warehouseId",input.text("warehouseId"));
            else { caseId=input.uuid("caseId"); locks.caseRow(caseId); anchor=store.reconciliationFacts(caseId); }
        } else {
            documentId=input.uuid("documentId"); locks.document(documentId); anchor=store.documentFacts(documentId);
            orderId=UUID.fromString(anchor.path("orderId").asText()); locks.order(orderId);
        }
        anchor = operation.lockContext(input, anchor);
        if (ReconciliationContext.isBusiness(input.kind())) orderId = UUID.fromString(anchor.path("orderId").asText());
        byte[] committed = replay(input, actor);
        if (committed != null) return committed;
        if (!anchor.path("warehouseId").asText().equals(actor.warehouse().toString())) throw CorrectionCommand.missing();
        var current=ReconciliationContext.isBusiness(input.kind())?ReconciliationContext.expected(anchor,actor.authVersion())
            :input.kind().equals("CREATE")?CorrectionCommand.Expected.forCreate(anchor,Long.parseLong(actor.authVersion()))
            :input.kind().equals("RECONCILE_OPEN")?CorrectionCommand.Expected.forCaseOpen(anchor,Long.parseLong(actor.authVersion()))
            :input.kind().equals("RECONCILE_ADVANCE")?CorrectionCommand.Expected.forCase(anchor,Long.parseLong(actor.authVersion()))
            :CorrectionCommand.Expected.forDocument(anchor,Long.parseLong(actor.authVersion()));
        CorrectionCommand.Expected.validate(input.expected(),current);
        if (operation.requiresApproval() && !actor.mayApprove()) throw CorrectionCommand.denied();
        var write=new CorrectionCommand.Write(Ids.next(),Ids.next(),Ids.next(),documentId,orderId,
            Instant.now().truncatedTo(ChronoUnit.MILLIS),input,anchor,snapshot(actor));
        store.reserve(write.requestId(),actor.id(),input.key(),input.digest(),write.receiptId(),write.recordedAt());
        JsonNode decision=operation.execute(write);
        UUID effectiveDocument=write.documentId()!=null?write.documentId()
            :(decision!=null&&decision.hasNonNull("documentId")?UUID.fromString(decision.path("documentId").asText()):null);
        var receiptWrite=new CorrectionCommand.Write(write.requestId(),write.receiptId(),write.eventId(),
            effectiveDocument,orderId,write.recordedAt(),input,anchor,snapshot(actor));
        var receipt=codec.sign(receiptWrite,decision);
        store.receipt(receiptWrite,SubmissionJson.canonical(input.expected()),
            new String[]{new String(receipt.original(),StandardCharsets.UTF_8),receipt.originalSha256()});
        operation.afterReceipt(receiptWrite, decision);
        var details=SubmissionJson.object().put("commandSha256",input.digest()).put("originalResponseSha256",receipt.originalSha256())
            .put("orderId",orderId==null?"null":orderId.toString())
            .put("documentId",decision==null||decision.path("documentId").isMissingNode()?"null":decision.path("documentId").asText())
            .put("caseId",decision!=null&&decision.hasNonNull("caseId")?decision.path("caseId").asText():(caseId==null?"null":caseId.toString()));
        audit.append(new AuditWriter.Event(actor.id(),"CORRECTION_"+input.kind(),"CORRECTION_COMMAND",write.receiptId(),details.toString()),requestId);
        return receipt.original();
    }

    private static JsonNode snapshot(CorrectionCommand.Actor actor) {
        var node=SubmissionJson.object().put("principalId",actor.id().toString()).put("warehouseId",actor.warehouse().toString())
            .put("userAuthVersion",actor.authVersion()).put("deviceId",actor.pda()==null?null:actor.pda().deviceId().toString())
            .put("sessionId",actor.pda()==null?null:actor.pda().sessionId().toString());
        var permissions=node.putObject("permissionSnapshot");
        actor.permissions().stream().sorted().forEach(permissions.putArray("effectivePermissions")::add);
        actor.warehouses().forEach(permissions.putArray("warehouseIds")::add);
        return node;
    }

    private TransactionTemplate template(PlatformTransactionManager manager,boolean readonly) {
        var tx=new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); tx.setReadOnly(readonly); return tx;
    }
}
