package com.acrylic.outbound.submissions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class SubmissionArchiveService {
    private final SubmissionAccess access;private final SubmissionStore submissions;private final SubmissionArchiveStore archives;
    private final SubmissionArchiveEvidence evidence;private final SubmissionArchiveProofs proofs;
    private final com.acrylic.outbound.temporary.TemporaryArchiveService temporary;
    private final TransactionTemplate closedTemporary;
    private final TransactionTemplate formalConfirmation;
    private final boolean temporaryArchiveEnabled;
    public SubmissionArchiveService(SubmissionAccess access,SubmissionStore submissions,SubmissionArchiveStore archives,
        SubmissionArchiveEvidence evidence,SubmissionArchiveProofs proofs,
        com.acrylic.outbound.temporary.TemporaryArchiveService temporary,PlatformTransactionManager manager,
        @org.springframework.beans.factory.annotation.Value("${outbound.temporary.archive.enabled:false}") boolean temporaryArchiveEnabled) {
        this.access=access;this.submissions=submissions;this.archives=archives;this.evidence=evidence;this.proofs=proofs;
        this.temporary=temporary;this.temporaryArchiveEnabled=temporaryArchiveEnabled;
        closedTemporary=new TransactionTemplate(manager);
        closedTemporary.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // The formal branch keeps its @Transactional, but the dispatch below calls it on `this`, which bypasses the
        // proxy: without this template no transaction exists and IdentityWriteGuard (MANDATORY) answers 500.
        formalConfirmation=new TransactionTemplate(manager);
        formalConfirmation.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    /**
     * Dispatch entry point. It is deliberately <b>not</b> transactional: a TEMP confirmation delegates to the
     * injected {@link com.acrylic.outbound.temporary.TemporaryArchiveService}, which owns the outer commit boundary
     * and therefore must be called while no transaction is active. FORMAL confirmations keep the original path.
     *
     * <p>TEMP confirmation stays closed unless the operator sets {@code outbound.temporary.archive.enabled=true}.
     * With the default (false) the production face is the former hard rejection: the request shape, the PDA/epoch
     * admission ({@link com.acrylic.outbound.common.IdentityWriteGuard} is {@code MANDATORY}, so this branch owns its
     * own transaction) and the row lock are still enforced, and only then {@code 409
     * TEMPORARY_CONFIRMATION_REQUIRED} is returned — nothing is archived and no new information is exposed.
     * Registering the assembly and this dispatch does not by itself open the capability, mirroring
     * {@code outbound.temporary.commands.enabled} and {@code outbound.handover.commands.enabled}.
     */
    public JsonNode confirm(UUID id,String epoch,JsonNode request) {
        var head=submissions.required(id);
        if(head.control().temporary()==null) return formalConfirmation.execute(status -> confirmFormal(id,epoch,request));
        if(temporaryArchiveEnabled) return temporary.confirm(id,epoch,request);
        return closedTemporary.execute(status->rejectTemporaryClosed(head,epoch,request));
    }
    /** Closed-state TEMP path: identical to the pre-dispatch implementation, ending in the former hard rejection. */
    private JsonNode rejectTemporaryClosed(SubmissionStore.Head head,String epoch,JsonNode request) {
        require(request.isObject() && request.size()==2 && text(request,"payloadSha256").matches("[a-f0-9]{64}") &&
            text(request,"receiptSha256").matches("[a-f0-9]{64}"),"归档确认须提供原正文与完整回执的规范摘要");
        access.pda(head.control(),epoch,true);submissions.lockExisting(head);
        throw new com.acrylic.outbound.common.BusinessException(409,"TEMPORARY_CONFIRMATION_REQUIRED","临时单独立确认及归档尚未开放，保留原记录");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode confirmFormal(UUID id,String epoch,JsonNode request) {
        require(request.isObject() && request.size()==2 && text(request,"payloadSha256").matches("[a-f0-9]{64}") &&
            text(request,"receiptSha256").matches("[a-f0-9]{64}"),"归档确认须提供原正文与完整回执的规范摘要");
        var head=submissions.required(id);access.pda(head.control(),epoch,true);head=submissions.lockExisting(head);
        var value=evidence.read(head,request);var previous=archives.find(id,epoch);
        evidence.persistReceipt(value,previous!=null);
        if(previous!=null) { verifyPrevious(head,epoch,value,previous);return previous; }
        var now=Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if(now.isBefore(Instant.parse(value.completedAt()))) throw SubmissionArchiveProofs.unavailable();
        var proof=proofs.sign(manifest(head,epoch,value).put("archivedAt",now.toString())
            .put("retainUntil",now.atOffset(ZoneOffset.UTC).plusYears(5).toInstant().toString()));
        archives.insert(id,epoch,proof);return proof;
    }
    private ObjectNode manifest(SubmissionStore.Head head,String epoch,SubmissionArchiveEvidence.Evidence value) {
        var c=head.control();return object().put("archiveVersion",1).put("submissionId",c.id().toString())
            .put("orderId",c.order().toString()).put("assignmentId",c.assignment().toString()).put("warehouseId",head.warehouse().toString())
            .put("operatorId",head.actor().toString()).put("freezeDeviceId",c.device().toString()).put("workRound",Long.toString(c.round()))
            .put("sourceRecoveryEpoch",c.epoch()).put("recoveryEpoch",epoch).put("payloadSha256",value.payloadHash())
            .put("receiptSha256",value.receiptHash()).put("payloadBytes",Integer.toString(value.payloadBytes()))
            .put("receiptBytes",Integer.toString(value.receiptBytes().length)).put("completedAt",value.completedAt())
            .put("storagePolicy","SERVER_DURABLE_FILES_V1").put("localDeletionAllowed",false);
    }
    private void verifyPrevious(SubmissionStore.Head head,String epoch,SubmissionArchiveEvidence.Evidence value,JsonNode previous) {
        proofs.verify(previous,"signedArchive","acrylic-pda-archive","archive");
        var expected=manifest(head,epoch,value);
        expected.fields().forEachRemaining(field->{
            if(!field.getValue().equals(previous.get(field.getKey()))) throw SubmissionArchiveProofs.unavailable();
        });
    }
}
