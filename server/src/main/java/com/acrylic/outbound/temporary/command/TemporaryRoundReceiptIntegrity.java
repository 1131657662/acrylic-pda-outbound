package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundControl;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Immutable binding and closure proofs; no current slot, permission, expiry or live K checks. */
final class TemporaryRoundReceiptIntegrity {
    private TemporaryRoundReceiptIntegrity() { }
    static void original(JdbcTemplate jdbc,JsonNode context,JsonNode body) {
        var request=TemporaryRoundControl.read(body);var reference=request.predecessor();
        int valid=jdbc.queryForObject("""
            SELECT COUNT(*) FROM temporary_source_continuation_submission r
            JOIN temporary_source_continuation_activation n ON n.ticket_id=r.ticket_id AND n.scope_id=r.scope_id AND n.from_work_round=r.from_work_round
            JOIN temporary_source_continuation q ON q.id=n.ticket_id AND q.scope_id=r.scope_id AND q.from_work_round=r.from_work_round
            JOIN submission_scope c ON c.id=r.scope_id
            JOIN temporary_work_origin o ON o.creator_device_id=c.creator_device_id AND o.client_order_id=c.client_order_id AND o.authorization_id=q.previous_authorization_id
            WHERE r.submission_id=? AND r.scope_id=? AND r.ticket_id=? AND r.from_work_round=1 AND r.to_work_round=?
              AND c.creator_device_id=? AND c.client_order_id=? AND n.new_client_order_id=? AND n.new_authorization_id=?
              AND r.predecessor_payload_sha256=? AND q.previous_submission_id=? AND q.retained_manifest_sha256=?
            """,Integer.class,Ids.bytes(request.control().id()),Ids.bytes(uuid(context,"scopeId")),Ids.bytes(reference.ticket()),request.control().round(),
            Ids.bytes(uuid(context,"creatorDeviceId")),Ids.bytes(uuid(context,"clientOrderId")),Ids.bytes(request.control().temporary().client()),
            Ids.bytes(request.control().temporary().authorization()),reference.payloadSha256(),Ids.bytes(reference.submission()),reference.retainedSha256());
        CommandErrors.integrity(valid==1,"continuation receipt origin");
    }
    static void completed(JdbcTemplate jdbc,JsonNode receipt) {
        var context=receipt.path("originalContext");var success=receipt.path("businessDecision").path("submissionReceipt");
        int valid=jdbc.queryForObject("""
            SELECT COUNT(*) FROM temporary_source_continuation_closure c
            JOIN temporary_source_continuation_submission r ON r.ticket_id=c.ticket_id AND r.scope_id=c.scope_id
              AND r.from_work_round=c.from_work_round AND r.submission_id=c.target_submission_id
            WHERE c.scope_id=? AND c.target_submission_id=? AND c.new_authorization_id=?
              AND c.status='COMPLETED' AND c.closure_receipt_id=? AND c.receipt_text=?
            """,Integer.class,Ids.bytes(uuid(context,"scopeId")),Ids.bytes(uuid(context,"submissionId")),Ids.bytes(uuid(context,"authorizationId")),
            Ids.bytes(uuid(receipt,"receiptId")),canonical(success));
        CommandErrors.integrity(valid==1,"continuation receipt completion");
    }
}
