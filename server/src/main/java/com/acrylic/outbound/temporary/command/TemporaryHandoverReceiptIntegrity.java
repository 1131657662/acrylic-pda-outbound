package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.TemporaryHandoverControl;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Historical handover verification uses immutable activation and receiving bindings. */
final class TemporaryHandoverReceiptIntegrity {
    private TemporaryHandoverReceiptIntegrity() { }
    static void original(JdbcTemplate jdbc,JsonNode context,JsonNode body) {
        var request=TemporaryHandoverControl.read(body);var ref=request.predecessor();
        CommandErrors.integrity(context.path("clientOrderId").equals(body.path("source").path("clientOrderId")));
        int count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM temporary_handover_submission r
            JOIN temporary_handover_activation n ON n.ticket_id=r.ticket_id
            JOIN temporary_handover_activation_ticket t ON t.id=n.ticket_id AND t.scope_id=r.scope_id
            JOIN submission p ON p.id=t.submission_id AND p.scope_id=t.scope_id AND p.work_round=t.from_work_round
              AND p.temporary_authorization_id=t.source_authorization_id
            JOIN submission s ON s.id=r.submission_id AND s.scope_id=r.scope_id AND s.work_round=r.to_work_round
              AND s.temporary_authorization_id=n.new_authorization_id AND s.freeze_device_id=n.target_device_id AND s.operator_id=n.target_user_id
            JOIN temporary_work_origin o ON o.creator_device_id=n.target_device_id AND o.client_order_id=n.new_client_order_id
              AND o.authorization_id=n.new_authorization_id
            WHERE r.submission_id=? AND r.scope_id=? AND r.ticket_id=? AND t.case_id=? AND t.submission_id=?
              AND r.to_work_round=? AND r.from_work_round=r.to_work_round-1 AND t.to_work_round=r.to_work_round
              AND t.payload_sha256=? AND r.predecessor_payload_sha256=t.payload_sha256 AND t.source_prefix_sha256=?
              AND n.new_authorization_id=? AND n.new_client_order_id=? AND n.target_device_id=? AND n.target_user_id=?
            """,Integer.class,Ids.bytes(request.control().id()),Ids.bytes(uuid(context,"scopeId")),Ids.bytes(ref.ticket()),Ids.bytes(ref.caseId()),
            Ids.bytes(ref.submission()),request.control().round(),ref.payloadSha256(),ref.prefixSha256(),Ids.bytes(uuid(context,"authorizationId")),
            Ids.bytes(uuid(context,"clientOrderId")),Ids.bytes(uuid(context,"freezeDeviceId")),Ids.bytes(uuid(context,"originalOperatorId")));
        CommandErrors.integrity(count==1);
    }
    static void completed(JdbcTemplate jdbc,JsonNode receipt,JsonNode body) {
        var rows=jdbc.queryForList("SELECT receipt_text FROM temporary_handover_completion WHERE ticket_id=? AND submission_id=? AND closure_receipt_id=?",
            String.class,Ids.bytes(uuid(body.path("temporaryHandover"),"ticketId")),Ids.bytes(uuid(body,"submissionId")),Ids.bytes(uuid(receipt,"receiptId")));
        CommandErrors.integrity(rows.size()==1 && parse(rows.getFirst()).equals(receipt.at("/businessDecision/submissionReceipt")));
    }
}
