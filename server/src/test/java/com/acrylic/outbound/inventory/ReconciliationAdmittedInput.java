package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;

/** Synthetic successful dispatch and controlled original bytes prepared before admission. */
public record ReconciliationAdmittedInput(String warehouse, String order, String dispatch,
        String maker, String checker, JsonNode attachment, Path publicKey) {
    public static ReconciliationAdmittedInput prepare(WebApplicationContext app, Path attempt) throws Exception {
        var f = new ReconciliationMissingPostingFixture(app, attempt, "admitted");
        var checker = f.original.jdbc.queryForObject("SELECT username FROM app_user u JOIN user_role ur ON ur.user_id=u.id"
            + " JOIN role r ON r.id=ur.role_id JOIN user_warehouse uw ON uw.user_id=u.id"
            + " WHERE r.code='SUPERVISOR' AND uw.warehouse_id=UUID_TO_BIN(?) ORDER BY u.id DESC LIMIT 1",
            String.class, f.original.warehouse);
        return new ReconciliationAdmittedInput(f.original.warehouse, f.original.order, f.original.dispatch,
            f.original.makerIdentity.user().username(), checker, f.attachment, f.original.publicKey);
    }

    public ObjectNode command(String kind, String caseId, JsonNode context) {
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", kind)
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", warehouse)
            .put("transportRecoveryEpoch", "1").put("reason", "ISOLATED admitted missing posting");
        body.set("expected", context.path("expected").deepCopy());
        if (kind.endsWith("_DISCOVER")) body.put("dispatchId", dispatch); else body.put("caseId", caseId);
        if (kind.endsWith("_SUBMIT")) body.set("evidence", evidence());
        return body;
    }

    private ObjectNode evidence() {
        var evidence = SubmissionJson.object().put("observedAt", Instant.now().minusSeconds(10).toString())
            .put("place", "ISOLATED bay").put("observation", "DISPATCHED_CONFIRMED").put("reference", "ISOLATED receipt");
        evidence.putArray("attachments").add(attachment.deepCopy()); return evidence;
    }

    public String context(String kind, String caseId) {
        return "/corrections/reconciliation/context?warehouseId=" + warehouse + "&commandType=" + kind
            + (kind.endsWith("_DISCOVER") ? "&dispatchId=" + dispatch : "&caseId=" + caseId);
    }

    public ObjectNode accounting(JdbcTemplate raw) {
        var result = SubmissionJson.object();
        result.set("result", SubmissionJson.parse(raw.queryForObject("SELECT JSON_OBJECT('version',CAST(v.result_version AS CHAR),"
            + "'count',CAST(v.actual_count AS CHAR),'weightG',CAST(v.weight_g AS CHAR)) FROM order_result_version v"
            + " JOIN outbound_order o ON o.id=v.order_id AND o.current_result_version=v.result_version WHERE o.id=UUID_TO_BIN(?)",
            String.class, order)));
        result.set("stock", SubmissionJson.parse(raw.queryForObject("SELECT JSON_OBJECT('cycle',b.dispatch_cycle,'status',p.book_status,"
            + "'blocked',p.blocked_reason,'upstream',p.upstream_present,'version',p.row_version,'weightG',d.weight_g)"
            + " FROM dispatch_record d JOIN bale b ON b.id=d.bale_id JOIN inventory_position p ON p.bale_id=b.id WHERE d.id=UUID_TO_BIN(?)",
            String.class, dispatch)));
        result.set("ledger", SubmissionJson.parse(raw.queryForObject("SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('id',BIN_TO_UUID(id),"
            + "'kind',entry_kind,'quantity',quantity_delta,'weightG',weight_delta_g,'key',business_key)),JSON_ARRAY()) FROM correction_ledger"
            + " WHERE dispatch_id=UUID_TO_BIN(?)", String.class, dispatch)));
        return result;
    }

    public JsonNode verify(byte[] bytes) throws Exception { return SupervisorCorrectionReceipt.verify(bytes, publicKey); }
}
