package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Re-proves the activation's signed decision against its relational consumption row. */
@Component
final class TemporaryHandoverActivationBinding {
    private final JdbcTemplate jdbc;private final HandoverPdaEvidenceStore commands;private final HandoverReadReceipt receipts;
    TemporaryHandoverActivationBinding(JdbcTemplate jdbc,HandoverPdaEvidenceStore commands,HandoverReadReceipt receipts) {
        this.jdbc=jdbc;this.commands=commands;this.receipts=receipts;
    }
    JsonNode read(HandoverTemporaryActivationTicketStore.Ticket ticket,JsonNode facts) {
        var rows=jdbc.queryForList("SELECT receipt_text FROM temporary_handover_activation WHERE ticket_id=?",String.class,Ids.bytes(ticket.id()));
        if(rows.size()!=1)throw TemporaryHandoverIntakeFacts.changed();var decision=parse(rows.getFirst());
        var signed=receipts.verify(commands.command(ticket.caseId(),"ACTIVATE"));
        equal(signed.receipt().path("businessDecision"),decision,"交接激活原签名决定不符");
        equal(uuid(signed.command(),"caseId"),ticket.caseId(),"交接激活原命令案件不符");
        columns(ticket,decision);anchor(facts,signed.receipt(),decision);
        return decision;
    }
    private void columns(HandoverTemporaryActivationTicketStore.Ticket ticket,JsonNode decision) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('ticketId',BIN_TO_UUID(ticket_id),'proofId',BIN_TO_UUID(proof_id),
              'newAuthorizationId',BIN_TO_UUID(new_authorization_id),'newClientOrderId',BIN_TO_UUID(new_client_order_id),
              'targetDeviceId',BIN_TO_UUID(target_device_id),'targetUserId',BIN_TO_UUID(target_user_id),
              'targetSessionId',BIN_TO_UUID(target_session_id),'fromWorkRound',CAST(from_work_round AS CHAR),
              'toWorkRound',CAST(to_work_round AS CHAR),'startOrderScanSeq',CAST(start_order_scan_seq AS CHAR),
              'retainedCount',CAST(retained_count AS CHAR)) FROM temporary_handover_activation WHERE ticket_id=?
            """,String.class,Ids.bytes(ticket.id()));
        if(rows.size()!=1)throw TemporaryHandoverIntakeFacts.changed();
        var expected=parse(rows.getFirst());
        expected.fields().forEachRemaining(field->equal(field.getValue(),decision.get(field.getKey()),"交接激活消费列与原回执不符"));
    }
    private void anchor(JsonNode facts,JsonNode receipt,JsonNode decision) {
        var evidence=(ObjectNode)decision.deepCopy();evidence.remove(List.of("status","retainedCount"));
        evidence.put("activatedAt",text(receipt,"recordedAt"));
        equal(sha(evidence),text(facts,"activationSha256"),"交接激活案件锚点不符");
    }
}
