package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.audit.PdaAuditOrigins;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeAuditPrefix;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Synthetic facts for the same-device first-round legacy schema test only. */
final class LegacyAuditPrefixFixture {
    static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    final JdbcTemplate jdbc;
    final Map<String, String> seed;
    final UUID device, warehouse, operator, authorization, submission;
    final ObjectNode formal, temporary;
    final SourceEvidenceIntakeAuditPrefix prefix;

    LegacyAuditPrefixFixture(JdbcTemplate jdbc, Path storage) throws Exception {
        this.jdbc = jdbc;
        seed = E2aV34Seed.seed(jdbc, storage);
        device = id("deviceId"); warehouse = id("warehouseId"); operator = id("operatorId");
        authorization = id("authorizationId"); submission = id("mapping0SubmissionId");
        formal = event(1).put("assignmentId", seed.get("assignmentId"));
        temporary = event(2).put("detail", submission.toString())
            .put("authorizationId", authorization.toString());
        temporary.set("source", SubmissionJson.object().put("kind", "TEMP")
            .put("creatorDeviceId", device.toString()).put("clientOrderId", seed.get("mapping0ClientOrderId")));
        prefix = new SourceEvidenceIntakeAuditPrefix(jdbc, new PdaAuditOrigins(jdbc));
    }

    UUID id(String key) { return UUID.fromString(seed.get(key)); }

    private ObjectNode event(long sequence) {
        return SubmissionJson.object().put("id", UUID.randomUUID().toString()).putNull("captureId")
            .put("originDeviceId", device.toString()).put("operatorId", operator.toString())
            .put("localSeq", Long.toString(sequence)).put("type", "STOP").put("detail", "synthetic")
            .put("wallMs", "1").put("elapsedMs", "1").put("boot", 1);
    }

    void insertEvents() {
        insert(formal, seed.get("assignmentId"), null, null);
        insert(temporary, null, authorization.toString(), seed.get("mapping0ClientOrderId"));
    }

    private void insert(ObjectNode event, String assignment, String authorization, String client) {
        jdbc.update("""
            INSERT INTO pda_audit_event(id,origin_device_id,local_seq,warehouse_id,assignment_id,operator_id,
              source_recovery_epoch,record_sha256,payload,uploaded_by,received_at,temporary_authorization_id,client_order_id)
            VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3),?,?)
            """, Ids.bytes(SubmissionJson.uuid(event, "id")), Ids.bytes(device),
            SubmissionJson.number(event, "localSeq"), Ids.bytes(warehouse), bytes(assignment), Ids.bytes(operator),
            1L, SubmissionJson.sha(event), SubmissionJson.canonical(event), Ids.bytes(operator),
            bytes(authorization), bytes(client));
    }

    void requireValid() {
        prefix.require(device, warehouse, 3, 2);
        prefix.requireStopEvent(device, warehouse, operator, authorization, 3, 2,
            temporary.path("id").asText(), submission);
    }

    void updateScope(String column, Object value) {
        if (!java.util.Set.of("work_round", "client_order_id").contains(column))
            throw new IllegalArgumentException("Test column is not allowlisted");
        jdbc.update("UPDATE submission_scope SET " + column + "=? WHERE id=?",
            value, Ids.bytes(id("mapping0ScopeId")));
    }

    Map<String, String> snapshot() {
        var result = new LinkedHashMap<String, String>();
        for (String table : jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.tables "
            + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY TABLE_NAME", String.class)) {
            if (!table.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Unsafe table name");
            var rows = jdbc.queryForList("SELECT * FROM " + table).stream().map(this::canonicalRow).sorted().toList();
            result.put(table, SubmissionJson.sha(JSON.valueToTree(rows)));
        }
        return result;
    }

    private String canonicalRow(Map<String, Object> row) {
        var sorted = new TreeMap<String, String>();
        row.forEach((key, value) -> sorted.put(key, value == null ? null
            : value instanceof byte[] bytes ? "binary:" + Base64.getEncoder().encodeToString(bytes)
            : value.getClass().getName() + ":" + value));
        return SubmissionJson.canonical(JSON.valueToTree(sorted));
    }

    private static byte[] bytes(String value) { return value == null ? null : Ids.bytes(UUID.fromString(value)); }
}
