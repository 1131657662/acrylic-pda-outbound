package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundStopProof;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Technical closure of the receiving TEMP round; its handover credential and owners stay intact. */
@Service
@ConditionalOnProperty(prefix="outbound.temporary.handover-intake",name="enabled",havingValue="true")
public class TemporaryHandoverStop {
    private final TemporaryHandoverIntakeAccess access;private final TemporaryHandoverRegistration registration;
    private final TemporaryHandoverIntakeFacts facts;private final SubmissionStore submissions;
    private final TemporaryRoundStopProof proof;private final SubmissionReceipts receipts;
    private final SubmissionEvents events;private final SubmissionArchiveProofs signatures;
    TemporaryHandoverStop(TemporaryHandoverIntakeAccess access,TemporaryHandoverRegistration registration,
        TemporaryHandoverIntakeFacts facts,SubmissionStore submissions,TemporaryRoundStopProof proof,
        SubmissionReceipts receipts,SubmissionEvents events,SubmissionArchiveProofs signatures) {
        this.access=access;this.registration=registration;this.facts=facts;this.submissions=submissions;
        this.proof=proof;this.receipts=receipts;this.events=events;this.signatures=signatures;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode stop(UUID id,JsonNode body,String epoch) {
        validate(id,body);var head=submissions.required(id);
        var request=new TemporaryHandoverControl(head.control(),TemporaryHandoverControl.Reference.read(body.path("temporaryHandover")));
        equal(request.predecessor(),facts.reference(id),"交接停止原票据引用不符");
        var actor=access.require(request,epoch);registration.register(request,actor);head=submissions.required(id);
        if("ABORTED".equals(head.status()))return replay(head,body);
        if(!"REVIEW_REQUIRED".equals(head.status()) || submissions.payloadHash(id)==null)
            throw new BusinessException(409,"TEMP_HANDOVER_STOP_STATE_CONFLICT","交接新轮尚未受理或已有终态，请保留原件");
        submissions.current(head);proof.require(head,body);
        var receipt=receipts.continuationStop(head,submissions.payloadHash(id),body);
        submissions.state(id,"ABORTED",null,canonical(receipt));events.aborted(head,receipt);return receipt;
    }
    private void validate(UUID id,JsonNode body) {
        var fields=new java.util.HashSet<String>();body.fieldNames().forEachRemaining(fields::add);
        require(body.isObject() && fields.equals(Set.of("submissionId","reason","sourceEvidenceSetId","verificationId","setSha256","temporaryHandover")),"交接停止字段不符");
        equal(id,uuid(body,"submissionId"),"交接停止路径不符");uuid(body,"sourceEvidenceSetId");uuid(body,"verificationId");
        String reason=text(body,"reason");require(!reason.isBlank() && reason.length()<=500,"停止须填写1至500字原因");
        require(text(body,"setSha256").matches("[a-f0-9]{64}"),"停止证据摘要无效");
    }
    private JsonNode replay(SubmissionStore.Head head,JsonNode body) {
        if(head.receipt()==null)throw TemporaryHandoverIntakeFacts.changed();
        var receipt=parse(head.receipt());
        signatures.verify(receipt,"signedReceipt","acrylic-pda-receipt","receipt");
        if(!sha(body).equals(receipt.path("stopRequestSha256").asText()))
            throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","停止原请求不可改写");
        return receipt;
    }
}
