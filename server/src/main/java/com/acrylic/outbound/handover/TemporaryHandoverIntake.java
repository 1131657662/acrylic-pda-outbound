package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
@ConditionalOnProperty(prefix="outbound.temporary.handover-intake",name="enabled",havingValue="true")
public class TemporaryHandoverIntake {
    private final TemporaryHandoverIntakeAccess access;private final TemporaryHandoverRegistration registration;
    private final SubmissionStore submissions;private final TemporaryHandoverPayload payload;
    private final TemporaryHandoverAcceptance acceptance;private final TemporaryHandoverIntakeFacts facts;private final SubmissionReceipts receipts;
    TemporaryHandoverIntake(TemporaryHandoverIntakeAccess access,TemporaryHandoverRegistration registration,SubmissionStore submissions,
        TemporaryHandoverPayload payload,TemporaryHandoverAcceptance acceptance,TemporaryHandoverIntakeFacts facts,SubmissionReceipts receipts) {
        this.access=access;this.registration=registration;this.submissions=submissions;this.payload=payload;
        this.acceptance=acceptance;this.facts=facts;this.receipts=receipts;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode submit(JsonNode body,String epoch) {
        var request=TemporaryHandoverControl.read(body);var actor=access.require(request,epoch);
        var bound=registration.register(request,actor);var head=submissions.required(request.control().id());
        equal(head.control(),request.control(),"交接注册身份与原文不符");equal(head.actor(),actor.userId(),"交接提交人员不符");
        String hash=sha(body),previous=submissions.payloadHash(head.control().id());submissions.requireUploadHash(head.control().id(),hash);
        if(previous!=null && !hash.equals(previous))throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","同一交接提交ID正文不同");
        if(previous!=null)return response(head);
        if(!java.util.Set.of("REGISTERED","RECEIVING").contains(head.status()))throw TemporaryHandoverIntakeFacts.changed();
        return acceptance.accept(bound,head,body,payload.validate(bound,head,body));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode read(UUID id) {
        var head=submissions.required(id);var request=new TemporaryHandoverControl(head.control(),facts.reference(id));
        var actor=access.require(request,head.control().epoch());var bound=facts.read(request);facts.actor(bound,actor);
        return response(head);
    }
    private JsonNode response(SubmissionStore.Head head) {
        if(head.receipt()!=null)return parse(head.receipt());
        var result=receipts.base(head,head.status(),submissions.payloadHash(head.control().id())).put("localDeletionAllowed",false);
        if(head.issues()!=null)result.set("issues",parse(head.issues()));return result;
    }
}
