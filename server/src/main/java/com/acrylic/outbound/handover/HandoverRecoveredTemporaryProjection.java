package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Read-only proof of the single existing FAULT_FENCE outbox version effect; no version is repaired or inferred. */
@Component
final class HandoverRecoveredTemporaryProjection {
    private final JdbcTemplate jdbc;
    private final HandoverReadReceipt receipts;
    private final HandoverFaultReplay faults;
    HandoverRecoveredTemporaryProjection(JdbcTemplate jdbc,HandoverReadReceipt receipts,HandoverFaultReplay faults) {
        this.jdbc=jdbc; this.receipts=receipts; this.faults=faults;
    }

    void require(HandoverCommand.Input command,JsonNode original,JsonNode current) {
        if(!command.kind().equals("FAULT_FENCE")) {
            require(original.path("orderVersion").equals(current.path("orderVersion"))); return;
        }
        var rows=jdbc.queryForList("SELECT * FROM handover_command_receipt WHERE original_key=? AND command_type='FAULT_FENCE'",
            Ids.bytes(command.uuid("commandId")));
        if(rows.isEmpty()) { require(original.path("orderVersion").equals(current.path("orderVersion"))); return; }
        require(number(current,"orderVersion")==Math.addExact(number(original,"orderVersion"),1));
        require(rows.size()==1 && Arrays.equals((byte[])rows.getFirst().get("canonical_command_bytes"),command.bytes()));
        var recorded=receipts.verify(rows.getFirst()); faults.verify(recorded); var context=recorded.receipt().path("caseContext");
        require(uuid(context,"caseId").equals(command.uuid("caseId")) && uuid(context,"scopeId").equals(uuid(original,"scopeId"))
            && uuid(context,"submissionId").equals(uuid(original,"currentSubmissionId"))
            && uuid(context,"warehouseId").equals(uuid(original,"warehouseId"))
            && uuid(context,"canonicalOrderId").equals(uuid(original,"canonicalOrderId")));
        outbox(command,recorded.receipt(),original,current);
    }

    private void outbox(HandoverCommand.Input command,JsonNode receipt,JsonNode original,JsonNode current) {
        var rows=jdbc.query("SELECT payload,created_at FROM order_sync_outbox WHERE order_id=? AND warehouse_id=? "
            +"AND recovery_epoch=? AND entity_version=? AND event_type='HANDOVER_FAULT_FENCED' AND published_seq IS NULL",
            (rs,n)->new Outbox(parse(rs.getString("payload")),rs.getTimestamp("created_at").toInstant()),
            Ids.bytes(uuid(original,"canonicalOrderId")),Ids.bytes(uuid(original,"warehouseId")),command.number("transportRecoveryEpoch"),number(current,"orderVersion"));
        require(rows.size()==1);
        var expected=object().put("caseId",command.text("caseId")).put("orderId",text(original,"canonicalOrderId"))
            .put("scopeId",text(original,"scopeId")).put("status",text(original,"orderStatus")).put("stopKind","FAULT_FENCE")
            .put("sourceDeviceId",text(original,"deviceId")).put("oldDeviceAcknowledged",false);
        require(rows.getFirst().payload().equals(expected)
            && rows.getFirst().recordedAt().equals(java.time.Instant.parse(text(receipt,"recordedAt"))));
    }

    private record Outbox(JsonNode payload,java.time.Instant recordedAt) { }

    private static void require(boolean valid) {
        if(!valid) throw new BusinessException(409,"HANDOVER_RECOVERED_PROJECTION_CHANGED","原订单版本变化须有本命令的完整签名故障围栏及唯一outbox凭据");
    }
}
