package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.temporary.continuation.TemporaryRoundFacts.conflict;

/** Stops an accepted continuation only after complete E2A evidence. Owners and tickets stay reserved. */
@Service
@ConditionalOnProperty(prefix="outbound.temporary.continuation-intake",name="enabled",havingValue="true")
public class TemporaryRoundStop {
    private final TemporaryRoundAccess access;private final TemporaryRoundRegistration registration;
    private final TemporaryRoundFacts facts;private final SubmissionStore submissions;
    private final TemporaryRoundStopProof proof;private final SubmissionReceipts receipts;private final SubmissionEvents events;
    private final SubmissionArchiveProofs signatures;
    public TemporaryRoundStop(TemporaryRoundAccess access,TemporaryRoundRegistration registration,TemporaryRoundFacts facts,
        SubmissionStore submissions,TemporaryRoundStopProof proof,SubmissionReceipts receipts,SubmissionEvents events,SubmissionArchiveProofs signatures) {
        this.access=access;this.registration=registration;this.facts=facts;this.submissions=submissions;
        this.proof=proof;this.receipts=receipts;this.events=events;this.signatures=signatures;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode stop(UUID id,JsonNode body,String epoch) {
        validate(id,body);var head=submissions.required(id);
        if(head.control().temporary()==null || head.control().round()<2)throw conflict("TEMP_CONTINUATION_STOP_ONLY");
        var request=new TemporaryRoundControl(head.control(),facts.reference(id));
        var actor=access.verify(request,epoch,true);registration.register(request,actor);head=submissions.required(id);
        if("ABORTED".equals(head.status()))return replay(head,body);
        if(!"REVIEW_REQUIRED".equals(head.status()) || submissions.payloadHash(id)==null)
            throw conflict("TEMP_CONTINUATION_STOP_STATE_CONFLICT");
        submissions.current(head);
        proof.require(head,body);
        var receipt=receipts.continuationStop(head,submissions.payloadHash(id),body);
        submissions.state(id,"ABORTED",null,canonical(receipt));events.aborted(head,receipt);
        return receipt;
    }
    private void validate(UUID id,JsonNode body) {
        var fields=new java.util.HashSet<String>();body.fieldNames().forEachRemaining(fields::add);
        require(body.isObject() && fields.equals(Set.of("submissionId","reason","sourceEvidenceSetId","verificationId","setSha256")),"续作停止请求字段不符");
        equal(id,uuid(body,"submissionId"),"续作停止路径不符");uuid(body,"sourceEvidenceSetId");uuid(body,"verificationId");
        String reason=text(body,"reason");require(!reason.isBlank() && reason.length()<=500,"停止须填写1至500字原因");
        require(text(body,"setSha256").matches("[0-9a-f]{64}"),"停止证据摘要错误");
    }
    private JsonNode replay(SubmissionStore.Head head,JsonNode body) {
        if(head.receipt()==null)throw conflict("TEMP_CONTINUATION_STOP_RECEIPT_UNAVAILABLE");
        var receipt=parse(head.receipt());
        signatures.verify(receipt,"signedReceipt","acrylic-pda-receipt","receipt");
        if(!sha(body).equals(receipt.path("stopRequestSha256").asText()))throw conflict("IDEMPOTENCY_CONTENT_MISMATCH");
        return receipt;
    }
}
