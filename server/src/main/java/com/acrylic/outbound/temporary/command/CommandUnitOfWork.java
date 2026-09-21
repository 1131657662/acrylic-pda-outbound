package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@Transactional(propagation=Propagation.MANDATORY)
public class CommandUnitOfWork {
    private final CommandIdentity identities;private final CommandFacts facts;private final CommandLocks locks;private final CommandReplay replay;
    private final ServerState state;private final CommandStore store;private final CommandReceiptCodec codec;private final AuditWriter audit;
    private final List<CommandOperation> operations;
    private final CommandSyntheticEligibility eligibility;
    public CommandUnitOfWork(CommandIdentity identities,CommandFacts facts,CommandLocks locks,CommandReplay replay,
        ServerState state,CommandStore store,CommandReceiptCodec codec,AuditWriter audit,List<CommandOperation> operations,CommandSyntheticEligibility eligibility) {
        this.identities=identities;this.facts=facts;this.locks=locks;this.replay=replay;this.state=state;
        this.store=store;this.codec=codec;this.audit=audit;this.operations=List.copyOf(operations);this.eligibility=eligibility;
    }
    public byte[] execute(CommandInput input,String requestId) {
        var actor=identities.current(input.uuid("warehouseId"));var hint=facts.hint(input.uuid("submissionId"));
        if(!actor.warehouse().equals(hint.id("warehouseId")))throw CommandErrors.missing();
        locks.identity(actor,hint);actor=identities.current(input.uuid("warehouseId"));
        byte[] existing=replay.existing(actor,input);if(existing!=null)return existing;
        var operation=operation(input.kind()); // Never gate committed replay on today's implementation or permission.
        state.requireWritable(input.text("transportRecoveryEpoch"));CommandPermissions.requireWrite(actor.permissions(),input.kind());
        locks.source(hint);var context=facts.read(input.uuid("submissionId"));locks.sameHint(hint,context);
        CommandExpected.validate(input,context,actor);
        eligibility.require(context);
        var write=new CommandWrite(Ids.next(),Ids.next(),Ids.next(),Instant.now().truncatedTo(ChronoUnit.MILLIS),
            input,context,identities.snapshot(actor,input.kind()));
        store.reserve(write);store.advance(write);operation.apply(context);
        var receipt=codec.sign(write);store.receipt(write,receipt);store.event(write,receipt);audit(write,receipt,requestId);
        return replay.existing(actor,input);
    }
    private CommandOperation operation(String kind) {
        // Only synthetic test participants exist in this batch, and only these bounded expectations are supported.
        if(!Set.of("NORMAL_CONFIRM","SHORT_APPROVE").contains(kind))throw CommandErrors.unsupported();
        var found=operations.stream().filter(o->o.commandType().equals(kind)).toList();
        if(found.isEmpty())throw CommandErrors.unsupported();
        if(found.size()!=1)throw new IllegalStateException("Ambiguous command operation");return found.getFirst();
    }
    private void audit(CommandWrite write,CommandReceiptCodec.Receipt receipt,String requestId) {
        var details=SubmissionJson.object().put("commandSha256",write.command().digest()).put("originalResponseSha256",receipt.originalSha256());
        audit.append(new AuditWriter.Event(UUID.fromString(write.actor().path("principalId").asText()),
            "INTERNAL_TEST_ONLY","TEMPORARY_COMMAND",write.receiptId(),details.toString()),requestId);
    }
}
