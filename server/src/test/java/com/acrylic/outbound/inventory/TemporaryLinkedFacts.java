package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Raw independent table projections; no CommandFacts, business receipt DTOs or tokens exported. */
final class TemporaryLinkedFacts {
    private final TemporaryLinkedFixture fixture;
    private final JdbcTemplate jdbc;
    TemporaryLinkedFacts(TemporaryLinkedFixture fixture) { this.fixture = fixture; jdbc = fixture.source.jdbc; }

    ObjectNode before() {
        var f = fixture.source;
        var facts = new TemporaryDecisionFacts(jdbc).before(f, f.operator().id());
        var scope = facts.path("scope");
        facts.set("mapping", row("SELECT * FROM temporary_source_mapping WHERE scope_id=UUID_TO_BIN(?) AND mapping_version=?",
            scope.path("id").asText(), scope.path("mapping_version").asText()));
        facts.set("pdaSession", row("""
            SELECT s.id,s.device_id,s.user_id,s.user_auth_version,s.device_version,s.revoked_at,
              s.access_expires_at,s.expires_at,d.warehouse_id,d.status device_status,d.row_version
            FROM pda_session s JOIN device d ON d.id=s.device_id WHERE s.id=UUID_TO_BIN(?)
            """, fixture.sessionId.toString()));
        return facts;
    }

    ObjectNode state() {
        var result = SubmissionJson.object();
        String submission = fixture.source.source.id();
        var scope = row("SELECT * FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)", submission);
        result.set("scope", scope);
        result.set("mappings", rows("SELECT * FROM temporary_source_mapping WHERE scope_id=UUID_TO_BIN(?) ORDER BY mapping_version", scope.path("id").asText()));
        result.set("slot", row("SELECT * FROM order_submission_slot WHERE current_submission_id=UUID_TO_BIN(?)", submission));
        result.set("submission", row("SELECT id,status FROM submission WHERE id=UUID_TO_BIN(?)", submission));
        result.set("originalOrder", row("SELECT id,status,current_result_version FROM outbound_order WHERE id=UUID_TO_BIN(?)", fixture.originalOrder));
        result.set("targetOrder", row("SELECT id,status,current_result_version FROM outbound_order WHERE id=UUID_TO_BIN(?)", fixture.target));
        result.set("commitments", rows("SELECT bale_id,order_id,submission_id,state FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) ORDER BY bale_id", submission));
        result.set("dispatches", rows("SELECT id,bale_id,order_id,submission_id,weight_g FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?) ORDER BY bale_id", submission));
        result.set("results", rows("SELECT order_id,result_version,submission_id,actual_count,weight_g FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)", submission));
        result.set("ledger", rows("SELECT l.id,l.dispatch_id,l.bale_id,l.quantity_delta,l.weight_delta_g FROM inventory_ledger l "
            + "JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.submission_id=UUID_TO_BIN(?) ORDER BY l.bale_id", submission));
        result.set("decision", row("SELECT decision_version FROM temporary_decision_state WHERE scope_id=UUID_TO_BIN(?)", scope.path("id").asText()));
        result.put("receiptCount", jdbc.queryForObject("SELECT COUNT(*) FROM temporary_command_receipt WHERE submission_id=UUID_TO_BIN(?)", String.class, submission));
        return result;
    }

    ObjectNode record(String commandId) { return new TemporaryDecisionFacts(jdbc).record(commandId); }

    private ObjectNode row(String sql, Object... args) { return convert(jdbc.queryForMap(sql, args)); }

    private ArrayNode rows(String sql, String id) {
        var result = SubmissionJson.object().putArray("rows");
        jdbc.queryForList(sql, id).forEach(row -> result.add(convert(row)));
        return result;
    }

    private ObjectNode convert(Map<String, Object> values) {
        var result = SubmissionJson.object();
        values.forEach((name, value) -> {
            if (value == null) result.putNull(name);
            else if (value instanceof byte[] bytes) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                result.put(name, new UUID(buffer.getLong(), buffer.getLong()).toString());
            } else if (value instanceof Timestamp time) result.put(name, time.toInstant().toString());
            else if (value instanceof java.time.LocalDateTime time) result.put(name, time.toInstant(java.time.ZoneOffset.UTC).toString());
            else result.put(name, value.toString());
        });
        return result;
    }
}
