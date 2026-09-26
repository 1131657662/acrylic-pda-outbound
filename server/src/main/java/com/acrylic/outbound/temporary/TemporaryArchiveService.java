package com.acrylic.outbound.temporary;

import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Explicit internal assembly only. The production archive controller continues to reject TEMP. */
public final class TemporaryArchiveService {
    public record Dependencies(SubmissionAccess access,SubmissionStore submissions,SubmissionArchiveStore archives,
        TemporaryArchiveEvidence evidence,SubmissionArchiveProofs proofs) { }
    private final Dependencies d;private final TransactionTemplate transaction;
    public TemporaryArchiveService(Dependencies dependencies,PlatformTransactionManager manager) {
        d=dependencies;transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public JsonNode confirm(UUID id,String epoch,JsonNode request) {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Archive confirmation must own the outer commit boundary");
        require(request.isObject() && request.size()==2 && text(request,"payloadSha256").matches("[a-f0-9]{64}")
            && text(request,"receiptSha256").matches("[a-f0-9]{64}"),"归档须提供原正文与成功回执摘要");
        return transaction.execute(status->confirmLocked(id,epoch,request));
    }
    private JsonNode confirmLocked(UUID id,String epoch,JsonNode request) {
        var head=d.submissions().required(id);d.access().pda(head.control(),epoch,true);head=d.submissions().lockExisting(head);
        var value=d.evidence().read(head,request);var previous=d.archives().find(id,epoch);
        d.evidence().persist(value,previous!=null);var expected=manifest(head,epoch,value);
        if(previous!=null) { verifyPrevious(expected,previous);return previous; }
        var now=Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if(now.isBefore(Instant.parse(value.receipt().path("completedAt").asText())))throw SubmissionArchiveProofs.unavailable();
        var proof=d.proofs().sign(expected.put("archivedAt",now.toString())
            .put("retainUntil",now.atOffset(ZoneOffset.UTC).plusYears(5).toInstant().toString()));
        d.archives().insert(id,epoch,proof);return proof;
    }
    private ObjectNode manifest(SubmissionStore.Head head,String epoch,TemporaryArchiveEvidence.Evidence value) {
        var c=head.control();var result=object().put("archiveVersion",2).put("submissionId",c.id().toString())
            .put("orderId",head.order().toString()).putNull("assignmentId").put("authorizationId",c.temporary().authorization().toString())
            .put("warehouseId",head.warehouse().toString()).put("operatorId",head.actor().toString()).put("freezeDeviceId",c.device().toString())
            .put("workRound",Long.toString(c.round())).put("sourceRecoveryEpoch",c.epoch()).put("recoveryEpoch",epoch)
            .put("payloadSha256",value.payloadHash()).put("payloadBytes",Integer.toString(value.payloadBytes()))
            .put("receiptSha256",value.receiptHash()).put("receiptBytes",Integer.toString(value.receiptBytes().length))
            .put("commandReceiptId",value.commandId().toString()).put("commandResponseSha256",value.commandHash())
            .put("commandBytes",Integer.toString(value.commandBytes().length)).put("mappingVersion","1")
            .put("planSha256",value.context().path("planSha256").asText()).put("completedAt",value.receipt().path("completedAt").asText())
            .put("storagePolicy","SERVER_DURABLE_FILES_V1").put("localDeletionAllowed",false);
        result.set("source",c.source());return result;
    }
    private void verifyPrevious(ObjectNode expected,JsonNode previous) {
        d.proofs().verify(previous,"signedArchive","acrylic-pda-archive","archive");
        try {
            require(previous.size()==expected.size()+3,"归档字段变化");
            expected.fields().forEachRemaining(field->equal(field.getValue(),previous.get(field.getKey()),"原归档上下文变化"));
            var archived=Instant.parse(text(previous,"archivedAt"));var retain=Instant.parse(text(previous,"retainUntil"));
            require(!archived.isBefore(Instant.parse(text(expected,"completedAt"))) &&
                !retain.isBefore(archived.atOffset(ZoneOffset.UTC).plusYears(5).toInstant()),"归档留存不足");
        } catch(RuntimeException error) { throw SubmissionArchiveProofs.unavailable(); }
    }
}
