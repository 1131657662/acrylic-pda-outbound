package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic completed source prepared outside admission; commands are consumed over TCP. */
public record CorrectionApprovalAdmittedInput(String warehouse, String order, Actor maker, Actor checker,
        long originalCount, long originalWeight, ObjectNode create) {
    public record Actor(String id, String username) { }

    public static CorrectionApprovalAdmittedInput prepare(WebApplicationContext context) throws Exception {
        var f = new SupervisorCorrectionFixture(context);
        return new CorrectionApprovalAdmittedInput(f.warehouse, f.order,
            new Actor(f.source.operator().id().toString(), f.source.operator().username()),
            new Actor(f.checker.user().id().toString(), f.checker.user().username()),
            f.originalCount, f.originalWeight, f.create());
    }

    public ObjectNode approve(JsonNode created) {
        var facts = created.path("businessDecision"); var expected = SubmissionJson.object();
        for (String key : java.util.List.of("documentId", "documentSha256", "status", "expectedResultVersion"))
            expected.set(key, facts.required(key));
        expected.put("userAuthVersion", "1");
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", "APPROVE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", warehouse).put("documentId", facts.path("documentId").asText())
            .put("reason", "isolated admitted supervisor approval");
        body.set("expected", expected); return body;
    }

    public JsonNode verifyReceipt(byte[] response, Path publicKey) throws Exception {
        return SupervisorCorrectionReceipt.verify(response, publicKey);
    }

    /** Independent raw consumer, outside the guarded application datasource. */
    public ObjectNode accounting(JdbcTemplate raw, String document) {
        var result = (ObjectNode) SubmissionJson.parse(raw.queryForObject(
            "SELECT JSON_OBJECT('version',CAST(v.result_version AS CHAR),'count',CAST(v.actual_count AS CHAR),"
                + "'weightG',CAST(v.weight_g AS CHAR)) FROM order_result_version v JOIN outbound_order o ON o.id=v.order_id"
                + " AND o.current_result_version=v.result_version WHERE o.id=UUID_TO_BIN(?)", String.class, order));
        assertThat(result.path("version").asText()).isEqualTo("2");
        assertThat(result.path("count").asLong()).isEqualTo(originalCount);
        assertThat(result.path("weightG").asLong()).isEqualTo(originalWeight + 1);
        var entries = raw.queryForList("SELECT quantity_delta,weight_delta_g,entry_kind FROM correction_ledger");
        assertThat(entries).hasSize(1); var entry = entries.getFirst();
        assertThat(((Number) entry.get("quantity_delta")).longValue()).isZero();
        assertThat(((Number) entry.get("weight_delta_g")).longValue()).isEqualTo(-1);
        assertThat(entry.get("entry_kind")).isEqualTo("WEIGHT_DIFF");
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM correction_effect WHERE order_id=UUID_TO_BIN(?)",
            Integer.class, order)).isOne();
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM correction_command_receipt WHERE document_id=UUID_TO_BIN(?)",
            Integer.class, document)).isEqualTo(2);
        result.put("ledgerEntries", entries.size()).put("effectCount", 1); return result;
    }

    public ObjectNode evidence() {
        return SubmissionJson.object().put("warehouse", warehouse).put("order", order).put("maker", maker.id())
            .put("checker", checker.id()).put("originalCount", originalCount).put("originalWeightG", originalWeight);
    }
}
