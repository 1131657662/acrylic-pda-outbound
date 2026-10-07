package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Opt-in new-round intake, separate from ordinary initial TEMP submission and abort. */
@Service
@ConditionalOnProperty(prefix="outbound.temporary.continuation-intake",name="enabled",havingValue="true")
public class TemporaryRoundIntake {
    private final TemporaryRoundAccess access;private final TemporaryRoundRegistration registration;private final SubmissionStore submissions;
    private final TemporaryRoundFacts facts;private final TemporaryRoundPayload payload;private final TemporaryRoundAcceptance acceptance;
    private final SubmissionReceipts receipts;
    public TemporaryRoundIntake(TemporaryRoundAccess access,TemporaryRoundRegistration registration,SubmissionStore submissions,
        TemporaryRoundFacts facts,TemporaryRoundPayload payload,TemporaryRoundAcceptance acceptance,SubmissionReceipts receipts) {
        this.access=access;this.registration=registration;this.submissions=submissions;this.facts=facts;
        this.payload=payload;this.acceptance=acceptance;this.receipts=receipts;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode submit(JsonNode body,String epoch) {
        var request=TemporaryRoundControl.read(body);var principal=access.verify(request,epoch,true);
        registration.register(request,principal);var head=submissions.required(request.control().id());
        equal(head.control(),request.control(),"续作注册身份与冻结原文不符");
        String hash=sha(body),previous=submissions.payloadHash(head.control().id());
        submissions.requireUploadHash(head.control().id(),hash);
        if(previous!=null && !previous.equals(hash))throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","同一续作提交ID正文不同");
        if(previous!=null)return response(head);
        if(!java.util.Set.of("REGISTERED","RECEIVING").contains(head.status()))throw TemporaryRoundFacts.conflict("TEMP_CONTINUATION_INTAKE_STATE_CONFLICT");
        var source=facts.read(request,new SubmissionAccess.Actor(principal.userId(),principal.warehouseId(),principal.userAuthVersion()));
        if(!"ACTIVATED".equals(source.status()))throw TemporaryRoundFacts.conflict("CONTINUATION_NOT_ACTIVATED");
        var input=new TemporaryRoundPayload.Input(request,source,head,facts.retained(request,source));
        var value=payload.validate(input,body);return acceptance.accept(input,body,value);
    }
    @Transactional(readOnly=true)
    public JsonNode read(UUID id) {
        var reference=facts.reference(id);var head=submissions.required(id);var request=new TemporaryRoundControl(head.control(),reference);
        var principal=access.verify(request,head.control().epoch(),false);
        facts.read(request,new SubmissionAccess.Actor(principal.userId(),principal.warehouseId(),principal.userAuthVersion()));
        return response(head);
    }
    private JsonNode response(SubmissionStore.Head head) {
        if(head.receipt()!=null)return parse(head.receipt());
        var result=receipts.base(head,head.status(),submissions.payloadHash(head.control().id())).put("localDeletionAllowed",false);
        if(head.issues()!=null)result.set("issues",parse(head.issues()));return result;
    }
}
