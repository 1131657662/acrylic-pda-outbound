package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persists the immutable TEMP handover ticket and its retained owner set. */
@Component
final class HandoverTemporaryActivationTicketStore {
    private final JdbcTemplate jdbc;
    HandoverTemporaryActivationTicketStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void issue(Preparation prepared) {
        Ticket ticket = build(prepared);
        insert(ticket, prepared.now());
        for (Map<String, Object> row : prepared.checklist()) {
            if (!"RETAINED".equals(row.get("state"))) continue;
            jdbc.update("INSERT INTO temporary_handover_activation_owner(ticket_id,bale_id,dispatch_cycle,record_sha256) VALUES(?,?,?,?)",
                Ids.bytes(ticket.id()), Ids.bytes(UUID.fromString(row.get("bale_id").toString())), ((Number) row.get("dispatch_cycle")).longValue(),
                SubmissionJson.sha(line(row)));
        }
    }

    private Ticket build(Preparation prepared) {
        var facts = prepared.facts(); var source = prepared.source(); var command = prepared.command();
        var qualification = prepared.ticket().path("temporaryQualification");
        UUID caseId = SubmissionJson.uuid(facts, "caseId");
        UUID scope = SubmissionJson.uuid(facts, "scopeId");
        UUID submission = SubmissionJson.uuid(facts, "submissionId");
        String checklistSha = SubmissionJson.sha(checklistJson(prepared.checklist()));
        byte[] ticketBytes = HandoverReceiptCodec.bytes(prepared.ticket());
        UUID canonical = SubmissionJson.uuid(facts, "canonicalOrderId");
        long from = SubmissionJson.number(facts, "fromWorkRound"), high = SubmissionJson.number(facts, "inputHighWatermark");
        String payloadSha = qualification.path("payloadSha256").asText();
        return new Ticket(Ids.next(), caseId, scope, submission, canonical, SubmissionJson.uuid(source, "authorizationId"),
            SubmissionJson.uuid(source.path("source"), "clientOrderId"), SubmissionJson.uuid(prepared.evidence(), "sourceEvidenceSetId"),
            SubmissionJson.number(source, "sourceRecoveryEpoch"), from, SubmissionJson.number(facts, "toWorkRound"), high,
            HandoverCommand.sha(ticketBytes), payloadSha, checklistSha, SubmissionJson.sha(prefix(facts, payloadSha, checklistSha)),
            SubmissionJson.uuid(qualification, "authorizationId"), SubmissionJson.uuid(command, "targetDeviceId"),
            SubmissionJson.uuid(command, "targetUserId"), SubmissionJson.number(qualification, "userAuthVersion"),
            SubmissionJson.number(qualification, "deviceVersion"), ticketBytes);
    }

    private void insert(Ticket ticket, Instant now) {
        jdbc.update("""
            INSERT INTO temporary_handover_activation_ticket(id,case_id,scope_id,submission_id,canonical_order_id,
              source_authorization_id,source_client_order_id,source_evidence_set_id,source_recovery_epoch,
              from_work_round,to_work_round,input_high_watermark,prepared_ticket_sha256,payload_sha256,checklist_sha256,
              source_prefix_sha256,target_authorization_id,target_device_id,target_user_id,target_user_auth_version,
              target_device_version,ticket_bytes,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, Ids.bytes(ticket.id()), Ids.bytes(ticket.caseId()), Ids.bytes(ticket.scopeId()), Ids.bytes(ticket.submissionId()),
            Ids.bytes(ticket.canonicalOrderId()), Ids.bytes(ticket.sourceAuthorizationId()), Ids.bytes(ticket.sourceClientOrderId()),
            Ids.bytes(ticket.sourceEvidenceSetId()), ticket.sourceRecoveryEpoch(), ticket.fromRound(), ticket.toRound(), ticket.inputHighWatermark(),
            ticket.preparedTicketSha(), ticket.payloadSha(), ticket.checklistSha(), ticket.sourcePrefixSha(), Ids.bytes(ticket.targetAuthorizationId()),
            Ids.bytes(ticket.targetDeviceId()), Ids.bytes(ticket.targetUserId()), ticket.targetUserAuthVersion(), ticket.targetDeviceVersion(),
            ticket.ticketBytes(), Timestamp.from(now));
    }

    Ticket lock(UUID caseId) {
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(id),BIN_TO_UUID(case_id),BIN_TO_UUID(scope_id),BIN_TO_UUID(submission_id),
              BIN_TO_UUID(canonical_order_id),BIN_TO_UUID(source_authorization_id),BIN_TO_UUID(source_client_order_id),
              BIN_TO_UUID(source_evidence_set_id),source_recovery_epoch,from_work_round,to_work_round,input_high_watermark,
              prepared_ticket_sha256,payload_sha256,checklist_sha256,source_prefix_sha256,BIN_TO_UUID(target_authorization_id),
              BIN_TO_UUID(target_device_id),BIN_TO_UUID(target_user_id),target_user_auth_version,target_device_version,ticket_bytes
            FROM temporary_handover_activation_ticket WHERE case_id=? FOR UPDATE
            """,
            (rs, n) -> new Ticket(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), UUID.fromString(rs.getString(3)),
                UUID.fromString(rs.getString(4)), UUID.fromString(rs.getString(5)), UUID.fromString(rs.getString(6)), UUID.fromString(rs.getString(7)),
                UUID.fromString(rs.getString(8)), rs.getLong(9), rs.getLong(10), rs.getLong(11), rs.getLong(12), rs.getString(13), rs.getString(14),
                rs.getString(15), rs.getString(16), UUID.fromString(rs.getString(17)), UUID.fromString(rs.getString(18)), UUID.fromString(rs.getString(19)),
                rs.getLong(20), rs.getLong(21), rs.getBytes(22)), Ids.bytes(caseId));
        if (rows.isEmpty()) throw new com.acrylic.outbound.common.BusinessException(409, "HANDOVER_TEMP_ACTIVATION_TICKET_MISSING", "临时交接激活票据缺失");
        return rows.getFirst();
    }

    List<Owner> owners(UUID ticket) {
        return jdbc.query("SELECT BIN_TO_UUID(bale_id),dispatch_cycle,record_sha256 FROM temporary_handover_activation_owner WHERE ticket_id=? ORDER BY bale_id",
            (rs, n) -> new Owner(UUID.fromString(rs.getString(1)), rs.getLong(2), rs.getString(3)), Ids.bytes(ticket));
    }

    private JsonNode checklistJson(List<Map<String, Object>> rows) {
        var items = SubmissionJson.object().putArray("items");
        for (var row : rows) items.add(line(row));
        return items;
    }

    static JsonNode line(Map<String, Object> row) {
        return SubmissionJson.object().put("baleId", row.get("bale_id").toString()).put("deliveryCode", row.get("delivery_code").toString())
            .put("state", row.get("state").toString()).put("dispatchCycle", Long.toString(((Number) row.get("dispatch_cycle")).longValue()));
    }

    static String prefixSha(Ticket ticket) {
        var facts = SubmissionJson.object().put("scopeId", ticket.scopeId().toString()).put("submissionId", ticket.submissionId().toString())
            .put("canonicalOrderId", ticket.canonicalOrderId().toString()).put("fromWorkRound", Long.toString(ticket.fromRound()))
            .put("inputHighWatermark", Long.toString(ticket.inputHighWatermark()));
        return SubmissionJson.sha(prefix(facts, ticket.payloadSha(), ticket.checklistSha()));
    }

    private static JsonNode prefix(JsonNode facts, String payloadSha, String checklistSha) {
        var prefix = SubmissionJson.object();
        for (String field : List.of("scopeId", "submissionId", "canonicalOrderId", "fromWorkRound", "inputHighWatermark"))
            prefix.set(field, facts.path(field).deepCopy());
        return prefix.put("payloadSha256", payloadSha).put("checklistSha256", checklistSha);
    }

    record Preparation(JsonNode facts, JsonNode command, JsonNode ticket, JsonNode source, JsonNode evidence,
        List<Map<String, Object>> checklist, Instant now) { }
    record Ticket(UUID id, UUID caseId, UUID scopeId, UUID submissionId, UUID canonicalOrderId, UUID sourceAuthorizationId,
        UUID sourceClientOrderId, UUID sourceEvidenceSetId, long sourceRecoveryEpoch, long fromRound, long toRound,
        long inputHighWatermark, String preparedTicketSha, String payloadSha, String checklistSha, String sourcePrefixSha,
        UUID targetAuthorizationId, UUID targetDeviceId, UUID targetUserId, long targetUserAuthVersion, long targetDeviceVersion,
        byte[] ticketBytes) {
        Ticket { ticketBytes = ticketBytes.clone(); }
        @Override public byte[] ticketBytes() { return ticketBytes.clone(); }
    }
    record Owner(UUID baleId, long dispatchCycle, String recordSha) { }
}
