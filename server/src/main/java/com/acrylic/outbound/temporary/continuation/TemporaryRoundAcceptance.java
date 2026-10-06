package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Full acceptance transaction; file bytes survive a database rollback as retained evidence. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
public class TemporaryRoundAcceptance {
    private final TemporaryRoundSelection selection;private final TemporaryFormalization formalization;
    private final SubmissionStore submissions;private final SubmissionFiles files;private final SubmissionEvents events;
    private final TemporaryRoundStock retained;private final SubmissionStock stock;private final TemporarySubmissionIssues review;
    private final SubmissionReceipts receipts;
    public TemporaryRoundAcceptance(TemporaryRoundSelection selection,TemporaryFormalization formalization,SubmissionStore submissions,
        SubmissionFiles files,SubmissionEvents events,TemporaryRoundStock retained,SubmissionStock stock,
        TemporarySubmissionIssues review,SubmissionReceipts receipts) {
        this.selection=selection;this.formalization=formalization;this.submissions=submissions;this.files=files;this.events=events;
        this.retained=retained;this.stock=stock;this.review=review;this.receipts=receipts;
    }
    public JsonNode accept(TemporaryRoundPayload.Input input,JsonNode body,TemporaryRoundPayload.Validated validated) {
        byte[] bytes=canonical(body).getBytes(StandardCharsets.UTF_8);String hash=sha(body);
        equal(files.store(bytes),hash,"续作冻结原文摘要不符");files.confirm(hash);
        var head=input.head();selection.select(head,input.source(),validated.plan());
        if(head.order()==null)formalization.create(head,validated.plan());
        head=submissions.required(head.control().id());submissions.current(head);
        submissions.payload(head,body,hash,bytes.length);events.evidence(head,body);
        retained.transfer(new TemporaryRoundPayload.Input(input.request(),input.source(),head,input.retained()),validated.snapshot());
        var issues=stock.reserve(head,validated.snapshot());review.append(validated.plan().authorization(),issues);
        if(validated.snapshot().shortShipment())issues.add(object().put("code","SHORT_SHIPMENT_APPROVAL_REQUIRED").put("reason",text(body,"shortShipmentReason")));
        submissions.state(head.control().id(),"REVIEW_REQUIRED",issues,null);events.transition(head,"REVIEW_REQUIRED",head.actor());
        var receipt=receipts.base(head,"REVIEW_REQUIRED",hash).put("localDeletionAllowed",false);receipt.set("issues",issues);return receipt;
    }
}
