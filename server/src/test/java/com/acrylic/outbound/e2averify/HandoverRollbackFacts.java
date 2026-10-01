package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exact typed database snapshots, independent of the command protocol's JSON value restrictions. */
final class HandoverRollbackFacts {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> TABLES = List.of("outbound_order", "order_assignment", "offline_authorization",
        "submission", "submission_scope", "order_submission_slot", "handover_case", "handover_evidence",
        "handover_checklist", "handover_command_receipt", "api_idempotency_request", "operation_event",
        "order_sync_outbox", "bale_commitment", "dispatch_record", "inventory_ledger");
    private final JdbcTemplate jdbc;
    private final HandoverRollbackFixture fixture;

    HandoverRollbackFacts(HandoverRollbackFixture fixture, JdbcTemplate jdbc) { this.fixture = fixture; this.jdbc = jdbc; }

    ObjectNode snapshot() {
        var result = MAPPER.createObjectNode();
        for (String table : TABLES) {
            var rows = jdbc.queryForList("SELECT * FROM " + table).stream().map(HandoverRollbackFacts::encodeRow).sorted().toList();
            var array = result.putArray(table); rows.forEach(array::add);
        }
        return result;
    }

    static String encodeRow(Map<String, Object> row) {
        var encoded = MAPPER.createObjectNode();
        new TreeMap<>(row).forEach((column, value) -> {
            if (value == null) encoded.putNull(column);
            else encoded.putObject(column).put("type", value.getClass().getName()).put("value", scalar(value));
        });
        return encoded.toString();
    }

    private static String scalar(Object value) {
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof java.sql.Time time) return time.toLocalTime().toString();
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof TemporalAccessor)
            return value.toString();
        throw new IllegalArgumentException("Unsupported JDBC snapshot type: " + value.getClass().getName());
    }

    ObjectNode summary() {
        var result = SubmissionJson.object();
        result.put("caseStatus", jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?", String.class, Ids.bytes(fixture.caseId)));
        result.put("activeAssignments", count("SELECT COUNT(*) FROM order_assignment WHERE order_id=? AND status='ACTIVE'", fixture.order));
        result.put("oldRevoked", count("SELECT COUNT(*) FROM order_assignment WHERE id=? AND status='REVOKED'", fixture.assignment));
        result.put("scopeRound", count("SELECT work_round FROM submission_scope WHERE id=?", fixture.scope));
        result.put("slotRound", count("SELECT work_round FROM order_submission_slot WHERE order_id=?", fixture.order));
        result.put("outbox", count("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=? AND event_type='HANDOVER_ACTIVATED'", fixture.order));
        result.put("pendingOutbox", count("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=? AND event_type='HANDOVER_ACTIVATED' AND published_seq IS NULL", fixture.order));
        result.put("activationReceipts", count("SELECT COUNT(*) FROM handover_command_receipt WHERE case_id=? AND command_type='ACTIVATE'", fixture.caseId));
        result.put("activationAudits", jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE event_type='HANDOVER_ACTIVATE'", Long.class));
        result.put("retained", count("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=?", fixture.submission));
        return result;
    }

    private long count(String sql, UUID id) { return jdbc.queryForObject(sql, Long.class, Ids.bytes(id)); }
}
