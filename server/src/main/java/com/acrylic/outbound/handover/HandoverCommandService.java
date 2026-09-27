package com.acrylic.outbound.handover;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/**
 * Outer commit boundary for the handover chain, mirroring the R06 command service: an original-key replay happens
 * before any work, the write path is a single transaction, and a duplicate-key collision is resolved by reading
 * the stored original bytes instead of executing twice. The command surface is assembled only when
 * {@code outbound.handover.commands.enabled=true}.
 */
@Service
public class HandoverCommandService {
    private final HandoverCommand.Identity identities;private final HandoverCommand.Locks locks;
    private final HandoverStore store;private final HandoverReceiptCodec codec;private final AuditWriter audit;
    private final ServerState state;private final List<HandoverCommand.Operation> operations;
    private final TransactionTemplate read;private final TransactionTemplate write;

    public HandoverCommandService(HandoverCommand.Identity identities,HandoverCommand.Locks locks,HandoverStore store,
        HandoverReceiptCodec codec,AuditWriter audit,ServerState state,
        org.springframework.beans.factory.ObjectProvider<HandoverCommand.Operation> operations,
        PlatformTransactionManager manager) {
        this.identities=identities;this.locks=locks;this.store=store;this.codec=codec;this.audit=audit;this.state=state;
        this.operations=operations.orderedStream().toList();
        this.read=template(manager,true);this.write=template(manager,false);
    }

    public byte[] execute(byte[] utf8,String requestId) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Handover command execution must own the outer transaction boundary");
        if (requestId==null || !requestId.matches("[A-Za-z0-9._:-]{1,64}"))
            throw new BusinessException(400,"INVALID_REQUEST_ID","请求追踪标识无效");
        var input=HandoverCommand.Input.parse(utf8);
        var actor=identities.current(input.uuid("warehouseId"));
        byte[] committed=read.execute(s->replay(input));if (committed!=null) return committed;
        var operation=operations.stream().filter(o->o.commandType().equals(input.kind())).findFirst()
            .orElseThrow(HandoverCommand::unsupported);
        try { return Objects.requireNonNull(write.execute(s->executeOnce(input,actor,operation,requestId))); }
        catch (DuplicateKeyException collision) {
            byte[] original=read.execute(s->replay(input));
            if (original!=null) return original;
            throw new BusinessException(409,"HANDOVER_RESULT_UNKNOWN","原键提交结果不可确认，请保留原意图并查询");
        }
    }

    /** Same key + same canonical bytes replays the stored response; different bytes is a conflict, never a redo. */
    private byte[] replay(HandoverCommand.Input input) {
        var row=store.receipt(input.key());
        if (row==null) return null;
        byte[] stored=(byte[])row.get("canonical_command_bytes");
        if (!Arrays.equals(stored,input.bytes())) throw HandoverCommand.conflict();
        return (byte[])row.get("response_bytes");
    }

    private byte[] executeOnce(HandoverCommand.Input input,HandoverCommand.Actor actor,HandoverCommand.Operation operation,String requestId) {
        state.requireWritable(input.text("transportRecoveryEpoch"));
        JsonNode anchor;UUID caseId;
        if (input.kind().equals("REQUEST")) {
            UUID scope=input.uuid("scopeId");locks.scope(scope);anchor=store.scopeFacts(scope);caseId=Ids.next();
        } else {
            caseId=input.uuid("caseId");locks.kase(caseId);anchor=store.caseFacts(caseId);
        }
        if (!anchor.path("warehouseId").asText().equals(actor.warehouse().toString())) throw HandoverCommand.missing();
        var current=input.kind().equals("REQUEST")
            ? HandoverCommand.Expected.forRequest(anchor,Long.parseLong(actor.authVersion()))
            : HandoverCommand.Expected.forCase(anchor,Long.parseLong(actor.authVersion()));
        HandoverCommand.Expected.validate(input.expected(),current);
        if (operation.requiresApproval() && !actor.mayApprove()) throw HandoverCommand.denied();
        var write=new HandoverCommand.Write(Ids.next(),Ids.next(),Ids.next(),caseId,
            Instant.now().truncatedTo(ChronoUnit.MILLIS),input,anchor,snapshot(actor));
        store.reserve(write.requestId(),actor.id(),input.key(),input.digest(),write.receiptId(),write.recordedAt());
        JsonNode decision=operation.execute(write);
        var receipt=codec.sign(write,decision);
        store.receipt(write,SubmissionJson.canonical(input.expected()),
            new String[]{new String(receipt.original(),StandardCharsets.UTF_8),receipt.originalSha256()});
        var details=SubmissionJson.object().put("commandSha256",input.digest()).put("originalResponseSha256",receipt.originalSha256())
            .put("caseId",caseId.toString());
        audit.append(new AuditWriter.Event(actor.id(),"HANDOVER_"+input.kind(),"HANDOVER_COMMAND",write.receiptId(),details.toString()),requestId);
        return receipt.original();
    }

    private static JsonNode snapshot(HandoverCommand.Actor actor) {
        var node=SubmissionJson.object().put("principalId",actor.id().toString()).put("warehouseId",actor.warehouse().toString())
            .put("userAuthVersion",actor.authVersion()).put("deviceId",actor.pda()==null?null:actor.pda().deviceId().toString())
            .put("sessionId",actor.pda()==null?null:actor.pda().sessionId().toString());
        var permissions=node.putObject("permissionSnapshot");
        actor.permissions().stream().sorted().forEach(permissions.putArray("effectivePermissions")::add);
        actor.warehouses().forEach(permissions.putArray("warehouseIds")::add);
        return node;
    }

    private TransactionTemplate template(PlatformTransactionManager manager,boolean readonly) {
        var tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setReadOnly(readonly);return tx;
    }
}
