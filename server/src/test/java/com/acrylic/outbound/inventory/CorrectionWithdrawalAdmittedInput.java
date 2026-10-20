package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic actors and a raw accounting consumer for the existing P-R07-08 withdrawal policy. */
public record CorrectionWithdrawalAdmittedInput(CorrectionApprovalAdmittedInput source,
        CorrectionApprovalAdmittedInput.Actor otherMaker, CorrectionApprovalAdmittedInput.Actor otherChecker) {
    public static CorrectionWithdrawalAdmittedInput prepare(WebApplicationContext app) throws Exception {
        var source = CorrectionApprovalAdmittedInput.prepare(app);
        return new CorrectionWithdrawalAdmittedInput(source, outsider(app, source, "WAREHOUSE_ADMIN"),
            outsider(app, source, "SUPERVISOR"));
    }

    private static CorrectionApprovalAdmittedInput.Actor outsider(WebApplicationContext app,
            CorrectionApprovalAdmittedInput source, String role) {
        var raw = app.getBean(JdbcTemplate.class);
        var actor = IdentityFixture.create(raw, app.getBean(PasswordEncoder.class), role);
        raw.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(actor.user().id()), source.warehouse());
        return new CorrectionApprovalAdmittedInput.Actor(actor.user().id().toString(), actor.user().username());
    }

    public ObjectNode command(JsonNode created, String kind, String status) {
        var body = source.approve(created).put("commandType", kind);
        ((ObjectNode) body.path("expected")).put("status", status); return body;
    }

    public ObjectNode accounting(JdbcTemplate raw, String document) {
        var result = SubmissionJson.object();
        result.set("result", SubmissionJson.parse(raw.queryForObject(
            "SELECT JSON_OBJECT('version',CAST(v.result_version AS CHAR),'count',CAST(v.actual_count AS CHAR),"
                + "'weightG',CAST(v.weight_g AS CHAR)) FROM order_result_version v JOIN outbound_order o ON o.id=v.order_id"
                + " AND o.current_result_version=v.result_version WHERE o.id=UUID_TO_BIN(?)", String.class, source.order())));
        var entries = result.putArray("ledger");
        raw.queryForList("SELECT JSON_OBJECT('id',BIN_TO_UUID(id),'line',BIN_TO_UUID(correction_line_id),'kind',entry_kind,"
            + "'quantity',CAST(quantity_delta AS CHAR),'weightG',CAST(weight_delta_g AS CHAR),"
            + "'restores',BIN_TO_UUID(restores_entry_id),'key',business_key) FROM correction_ledger ORDER BY id",
            String.class).forEach(row -> entries.add(SubmissionJson.parse(row)));
        result.put("effects", raw.queryForObject("SELECT COUNT(*) FROM correction_effect WHERE order_id=UUID_TO_BIN(?)",
            Integer.class, source.order()));
        result.put("receipts", raw.queryForObject("SELECT COUNT(*) FROM correction_command_receipt WHERE document_id=UUID_TO_BIN(?)",
            Integer.class, document));
        return result;
    }

    public void assertRestored(JsonNode before, JsonNode after) {
        assertThat(after.at("/result/version").asText()).isEqualTo("3");
        assertThat(after.at("/result/count").asLong()).isEqualTo(source.originalCount());
        assertThat(after.at("/result/weightG").asLong()).isEqualTo(source.originalWeight());
        assertThat(after.path("effects").asInt()).isOne(); assertThat(after.path("receipts").asInt()).isEqualTo(4);
        assertThat(before.path("ledger")).hasSize(1); assertThat(after.path("ledger")).hasSize(2);
        JsonNode forward = before.path("ledger").get(0), restore = null;
        for (JsonNode row : after.path("ledger")) {
            if (row.path("restores").isNull()) assertThat(row).isEqualTo(forward); else restore = row;
        }
        assertThat(restore).isNotNull(); assertThat(restore.path("kind").asText()).isEqualTo("WITHDRAWAL_RESTORE");
        assertThat(restore.path("restores")).isEqualTo(forward.path("id"));
        assertThat(restore.path("line")).isEqualTo(forward.path("line"));
        assertThat(restore.path("quantity").asLong()).isEqualTo(-forward.path("quantity").asLong());
        assertThat(restore.path("weightG").asLong()).isEqualTo(-forward.path("weightG").asLong());
        assertThat(restore.path("key").asText()).isEqualTo("WD:" + forward.path("line").asText() + ":" + forward.path("id").asText());
    }
}
