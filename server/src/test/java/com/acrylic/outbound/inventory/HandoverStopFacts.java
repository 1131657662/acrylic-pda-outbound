package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Full typed table comparison includes recall, evidence, qualifications, audit and stock effects. */
final class HandoverStopFacts {
    private static final List<String> TABLES = List.of("outbound_order", "order_assignment", "offline_authorization",
        "submission", "submission_scope", "order_submission_slot", "handover_case", "handover_evidence", "handover_checklist",
        "handover_command_receipt", "api_idempotency_request", "operation_event", "order_sync_outbox", "bale_commitment",
        "dispatch_record", "inventory_ledger", "order_change_request", "recovery_package", "temporary_work_origin");
    private final HandoverStopFixture f;
    private final JdbcTemplate jdbc;
    private int comparisons;
    HandoverStopFacts(HandoverStopFixture f, JdbcTemplate jdbc) { this.f = f; this.jdbc = jdbc; }

    ObjectNode snapshot() {
        var result = SubmissionJson.object();
        for (String table : TABLES) {
            var rows = jdbc.queryForList("SELECT * FROM " + table).stream().map(this::encode).sorted().toList();
            var array = result.putArray(table); rows.forEach(array::add);
        }
        return result;
    }

    void unchanged(ObjectNode before) throws Exception {
        var after = snapshot(); String label = "state-" + ++comparisons + "-" + UUID.randomUUID();
        Files.writeString(f.directory.resolve(label + "-before.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(f.directory.resolve(label + "-after.json"), after.toPrettyString(), StandardOpenOption.CREATE_NEW);
        assertThat(after).as("business tables unchanged for " + label).isEqualTo(before);
    }

    void stopped() throws Exception {
        assertThat(value("SELECT status FROM order_assignment WHERE id=UUID_TO_BIN(?)", f.r.f.assignment.path("id").asText())).isEqualTo("REVOKED");
        assertThat(count("SELECT COUNT(*) FROM offline_authorization WHERE assignment_id=UUID_TO_BIN(?) AND revoked_at IS NULL", f.r.f.assignment.path("id").asText())).isZero();
        assertThat(value("SELECT status FROM order_change_request WHERE id=UUID_TO_BIN(?)", f.r.recall.path("changeRequestId").asText())).isEqualTo("STOPPED");
        var state = jdbc.queryForMap("SELECT status,stop_state,integrity_state,physical_state,row_version,input_high_watermark FROM handover_case WHERE id=UUID_TO_BIN(?)", f.kase.toString());
        assertThat(state).containsEntry("status", "EVIDENCE_PENDING").containsEntry("stop_state", "DEVICE_ACKED")
            .containsEntry("integrity_state", "COMPLETE").containsEntry("physical_state", "UNCHECKED");
        assertThat(((Number) state.get("row_version")).longValue()).isEqualTo(2);
        assertThat(((Number) state.get("input_high_watermark")).longValue()).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM handover_evidence WHERE case_id=UUID_TO_BIN(?)", f.kase.toString())).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM operation_event WHERE event_type='HANDOVER_STOPPED' AND JSON_UNQUOTE(JSON_EXTRACT(details,'$.caseId'))=?", f.kase.toString())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) AND event_type='HANDOVER_STOPPED' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.status'))='RECALL_PENDING'", f.r.f.order)).isEqualTo(1);
        assertThat(f.r.f.count("bale_commitment")).isEqualTo(2); assertThat(f.r.f.count("inventory_ledger")).isZero();
        Files.writeString(f.directory.resolve("final-state.json"), snapshot().toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private String value(String sql, String argument) { return jdbc.queryForObject(sql, String.class, argument); }
    private long count(String sql, String argument) { return jdbc.queryForObject(sql, Long.class, argument); }
    private String encode(Map<String, Object> row) {
        var result = SubmissionJson.object();
        new TreeMap<>(row).forEach((key, value) -> {
            if (value == null) result.putNull(key);
            else result.putObject(key).put("type", value.getClass().getName()).put("value",
                value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes) : value.toString());
        });
        return result.toString();
    }
}