package com.acrylic.outbound.submissions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class SubmissionArchiveService {
    private final SubmissionAccess access;private final SubmissionStore submissions;private final SubmissionArchiveStore archives;
    private final SubmissionArchiveEvidence evidence;private final SubmissionArchiveProofs proofs;
    private final com.acrylic.outbound.temporary.TemporaryArchiveService temporary;
    public SubmissionArchiveService(SubmissionAccess access,SubmissionStore submissions,SubmissionArchiveStore archives,
        SubmissionArchiveEvidence evidence,SubmissionArchiveProofs proofs,
        org.springframework.transaction.PlatformTransactionManager manager,
        com.acrylic.outbound.temporary.TemporaryArchiveEvidence temporaryEvidence) {
        this.access=access;this.submissions=submissions;this.archives=archives;this.evidence=evidence;this.proofs=proofs;
        // TEMP archiving owns its own outer commit boundary, so it must be invoked outside any active transaction.
        this.temporary=new com.acrylic.outbound.temporary.TemporaryArchiveService(
            new com.acrylic.outbound.temporary.TemporaryArchiveService.Dependencies(
                access,submissions,archives,temporaryEvidence,proofs),manager);
    }
    /**
     * Dispatch entry point. It is deliberately <b>not</b> transactional: a TEMP confirmation delegates to
     * {@link com.acrylic.outbound.temporary.TemporaryArchiveService#confirm}, which requires that no transaction is
     * active because it commits the archive chain itself. FORMAL confirmations keep the original transactional path.
     */
    public JsonNode confirm(UUID id,String epoch,JsonNode request) {
        var head=submissions.required(id);
        if(head.control().temporary()!=null) return temporary.confirm(id,epoch,request);
        return confirmFormal(id,epoch,request);
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
