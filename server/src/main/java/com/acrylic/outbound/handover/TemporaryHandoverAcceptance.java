package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.TemporarySubmissionIssues;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
@Transactional(propagation=Propagation.MANDATORY)
class TemporaryHandoverAcceptance {
    private final JdbcTemplate jdbc;private final SubmissionFiles files;private final SubmissionStore submissions;
    private final SubmissionEvents events;private final TemporaryHandoverSubmissionOwners owners;
    private final SubmissionStock stock;private final TemporarySubmissionIssues review;private final SubmissionReceipts receipts;
    private final TemporaryHandoverTarget target;
    TemporaryHandoverAcceptance(JdbcTemplate jdbc,SubmissionFiles files,SubmissionStore submissions,SubmissionEvents events,
        TemporaryHandoverSubmissionOwners owners,SubmissionStock stock,TemporarySubmissionIssues review,SubmissionReceipts receipts,
        TemporaryHandoverTarget target) {
        this.jdbc=jdbc;this.files=files;this.submissions=submissions;this.events=events;this.owners=owners;
        this.stock=stock;this.review=review;this.receipts=receipts;this.target=target;
    }
    JsonNode accept(TemporaryHandoverIntakeFacts.Facts facts,SubmissionStore.Head head,JsonNode body,TemporaryHandoverPayload.Checked checked) {
        target.require(head,checked.plan(),false);
        byte[] bytes=canonical(body).getBytes(StandardCharsets.UTF_8);String hash=sha(body);
        equal(files.store(bytes),hash,"交接冻结原文摘要不符");files.confirm(hash);
        select(facts,head);submissions.current(head);submissions.payload(head,body,hash,bytes.length);events.evidence(head,body);
        owners.transfer(facts,head,checked.snapshot());var issues=stock.reserve(head,checked.snapshot());
        review.append(checked.plan().authorization(),issues);
        if(checked.snapshot().shortShipment())issues.add(object().put("code","SHORT_SHIPMENT_APPROVAL_REQUIRED").put("reason",text(body,"shortShipmentReason")));
        submissions.state(head.control().id(),"REVIEW_REQUIRED",issues,null);events.transition(head,"REVIEW_REQUIRED",head.actor());
        var receipt=receipts.base(head,"REVIEW_REQUIRED",hash).put("localDeletionAllowed",false);receipt.set("issues",issues);return receipt;
    }
    private void select(TemporaryHandoverIntakeFacts.Facts facts,SubmissionStore.Head head) {
        var t=facts.ticket();
        int changed=jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=? AND work_round=? AND current_submission_id IS NULL AND canonical_order_id=?",
            Ids.bytes(head.control().id()),Ids.bytes(t.scopeId()),t.toRound(),Ids.bytes(t.canonicalOrderId()));
        if(changed!=1)throw TemporaryHandoverIntakeFacts.changed();
        changed=jdbc.update("""
            UPDATE order_submission_slot SET current_submission_id=?,phase='FROZEN',slot_version=slot_version+1
            WHERE order_id=? AND scope_id=? AND work_round=? AND current_submission_id IS NULL AND phase='CONTINUING'
            """,Ids.bytes(head.control().id()),Ids.bytes(t.canonicalOrderId()),Ids.bytes(t.scopeId()),t.toRound());
        if(changed!=1 || jdbc.update("UPDATE outbound_order SET status='REVIEW_REQUIRED' WHERE id=? AND status='CONTINUING' AND current_result_version IS NULL",
            Ids.bytes(t.canonicalOrderId()))!=1)throw TemporaryHandoverIntakeFacts.changed();
    }
}
