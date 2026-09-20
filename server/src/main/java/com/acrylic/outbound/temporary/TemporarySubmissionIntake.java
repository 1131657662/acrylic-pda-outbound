package com.acrylic.outbound.temporary;

import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class TemporarySubmissionIntake {
    private final TemporarySubmissionPlan plans;private final TemporarySubmissionEvidence evidence;private final TemporarySubmissionSnapshot snapshots;
    private final TemporaryFormalization formalization;private final SubmissionStore store;private final SubmissionFiles files;
    private final SubmissionEvents events;private final SubmissionStock stock;private final SubmissionReceipts receipts;
    private final TemporarySubmissionIssues reviewIssues;
    public TemporarySubmissionIntake(TemporarySubmissionPlan plans,TemporarySubmissionEvidence evidence,TemporarySubmissionSnapshot snapshots,
        TemporaryFormalization formalization,SubmissionStore store,SubmissionFiles files,SubmissionEvents events,SubmissionStock stock,SubmissionReceipts receipts,TemporarySubmissionIssues reviewIssues) {
        this.reviewIssues=reviewIssues;
        this.plans=plans;this.evidence=evidence;this.snapshots=snapshots;this.formalization=formalization;this.store=store;this.files=files;this.events=events;this.stock=stock;this.receipts=receipts;
    }
    public JsonNode accept(SubmissionStore.Head head,JsonNode body) {
        var plan=plans.validate(head,body);evidence.validate(head,plan,body);var value=snapshots.validate(plan,body);
        byte[] bytes=canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=sha(body);
        equal(files.store(bytes),hash,"临时冻结正文摘要不符");files.confirm(hash);
        formalization.create(head,plan);head=store.required(head.control().id());store.current(head);
        store.payload(head,body,hash,bytes.length);events.evidence(head,body);
        var issues=stock.reserve(head,value);reviewIssues.append(plan.authorization(),issues);
        if(value.shortShipment())issues.add(object().put("code","SHORT_SHIPMENT_APPROVAL_REQUIRED").put("reason",text(body,"shortShipmentReason")));
        store.state(head.control().id(),"REVIEW_REQUIRED",issues,null);events.transition(head,"REVIEW_REQUIRED",head.actor());
        var response=receipts.base(head,"REVIEW_REQUIRED",hash);response.set("issues",issues);return response;
    }
}
