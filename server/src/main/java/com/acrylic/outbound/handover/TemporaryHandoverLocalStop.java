package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Atomically selects and reserves a verified local-only round, without storing an accepted payload. */
@Component
@Transactional(propagation=Propagation.MANDATORY)
class TemporaryHandoverLocalStop {
    private final TemporaryHandoverLocalOriginal originals;private final TemporaryHandoverFrozenSource frozen;
    private final TemporaryHandoverSubmissionOwners owners;private final JdbcTemplate jdbc;
    private final SubmissionReceipts receipts;
    TemporaryHandoverLocalStop(TemporaryHandoverLocalOriginal originals,TemporaryHandoverFrozenSource frozen,
        TemporaryHandoverSubmissionOwners owners,JdbcTemplate jdbc,SubmissionReceipts receipts) {
        this.originals=originals;this.frozen=frozen;this.owners=owners;this.jdbc=jdbc;this.receipts=receipts;
    }

    JsonNode close(SubmissionStore.Head head,JsonNode request) {
        var body=originals.read(head,request);var checked=frozen.checked(head,body);var source=frozen.source(head,body);
        select(source,head);owners.transfer(source,head,checked.snapshot());
        return receipts.localFrozenHandoverStop(head,sha(body),request);
    }

    private void select(TemporaryHandoverIntakeFacts.Facts facts,SubmissionStore.Head head) {
        var t=facts.ticket();
        int changed=jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=? AND work_round=? AND current_submission_id IS NULL AND canonical_order_id=?",
            Ids.bytes(head.control().id()),Ids.bytes(t.scopeId()),t.toRound(),Ids.bytes(t.canonicalOrderId()));
        if(changed!=1)throw TemporaryHandoverFrozenSource.changed();
        changed=jdbc.update("""
            UPDATE order_submission_slot SET current_submission_id=?,phase='FROZEN',slot_version=slot_version+1
            WHERE order_id=? AND scope_id=? AND work_round=? AND current_submission_id IS NULL AND phase='CONTINUING'
            """,Ids.bytes(head.control().id()),Ids.bytes(t.canonicalOrderId()),Ids.bytes(t.scopeId()),t.toRound());
        if(changed!=1 || jdbc.update("UPDATE outbound_order SET status='REVIEW_REQUIRED' WHERE id=? AND status='CONTINUING' AND current_result_version IS NULL",
            Ids.bytes(t.canonicalOrderId()))!=1)throw TemporaryHandoverFrozenSource.changed();
    }
}
