package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Corruption and impossible-state probes run separately from the trusted positive flow. */
final class TemporaryDispositionReceiptChecks {
    private final JdbcTemplate jdbc;
    private final TemporaryDispositionHttp http;
    private final TemporaryDispositionSqlChecks sql;

    TemporaryDispositionReceiptChecks(JdbcTemplate jdbc, TemporaryDispositionHttp http, TemporaryDispositionSqlChecks sql) {
        this.jdbc = jdbc;
        this.http = http;
        this.sql = sql;
    }

    void rejectedSourceCannotReopen(ObjectNode disposition) throws Exception {
        var command = disposition.deepCopy().put("commandType", "REOPEN_WORK")
            .put("commandId", UUID.randomUUID().toString());
        command.remove("dispositionManifest");
        var retained = retainedManifest(command);
        command.set("retainedManifest", retained);
        command.put("nextOrderScanSeq", nextOrderScanSeq(command));
        ((ObjectNode) command.path("expected")).putNull("dispositionManifestSha256")
            .putNull("dispositionManifestVersion").put("retainedManifestSha256", SubmissionJson.sha(retained))
            .put("retainedManifestVersion", "1");
        var before = sql.snapshot("reopen-before");
        assertThat(SubmissionJson.parse(http.command(command, 409)).path("code").asText())
            .isEqualTo("CONTINUATION_ABORT_REQUIRED");
        assertThat(sql.snapshot("reopen-after")).isEqualTo(before);
    }

    private ObjectNode retainedManifest(ObjectNode command) {
        var expected = command.path("expected");
        var rows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(bale_id) baleId,CAST(original_cycle AS CHAR) expectedDispatchCycle,record_sha256 recordSha256
            FROM source_evidence_item
            WHERE set_id=UUID_TO_BIN(?) AND submission_id=UUID_TO_BIN(?) AND work_round=?
              AND item_kind='SCAN' AND record_status='ACTIVE'
            ORDER BY bale_id
            """, expected.path("evidenceSetId").asText(), command.path("submissionId").asText(),
            Long.parseLong(command.path("workRound").asText()));
        var manifest = SubmissionJson.object().put("version", 1);
        var items = manifest.putArray("items");
        rows.forEach(row -> items.add(SubmissionJson.object().put("baleId", row.get("baleId").toString())
            .put("expectedDispatchCycle", row.get("expectedDispatchCycle").toString())
            .put("recordSha256", row.get("recordSha256").toString())));
        return manifest;
    }

    private String nextOrderScanSeq(ObjectNode command) {
        return jdbc.queryForObject("""
            SELECT CAST(order_scan_high_watermark+1 AS CHAR) FROM source_evidence_set
            WHERE id=UUID_TO_BIN(?) AND submission_id=UUID_TO_BIN(?) AND work_round=?
            """, String.class, command.path("expected").path("evidenceSetId").asText(),
            command.path("submissionId").asText(), Long.parseLong(command.path("workRound").asText()));
    }

    void immutableCycleCannotBeChanged(ObjectNode rejection, byte[] original) throws Exception {
        String submission = rejection.path("submissionId").asText();
        var item = jdbc.queryForMap("SELECT BIN_TO_UUID(i.disposition_id) disposition,BIN_TO_UUID(i.bale_id) bale,i.dispatch_cycle "
            + "FROM temporary_physical_disposition_item i JOIN temporary_physical_disposition d ON d.id=i.disposition_id "
            + "WHERE d.submission_id=UUID_TO_BIN(?) ORDER BY i.bale_id LIMIT 1", submission);
        String update = "UPDATE temporary_physical_disposition_item SET dispatch_cycle=? "
            + "WHERE disposition_id=UUID_TO_BIN(?) AND bale_id=UUID_TO_BIN(?)";
        long cycle = ((Number) item.get("dispatch_cycle")).longValue();
        var before = sql.snapshot("receipt-before-mutation");
        assertThatThrownBy(() -> jdbc.update(update, cycle + 1, item.get("disposition"), item.get("bale")))
            .isInstanceOf(org.springframework.jdbc.UncategorizedSQLException.class)
            .hasMessageContaining("Physical disposition items are immutable");
        assertThat(sql.snapshot("receipt-after-mutation-refused")).isEqualTo(before);
        assertThat(http.command(rejection, 200)).isEqualTo(original);
        assertThat(http.get(originalPath(rejection))).isEqualTo(original);
    }

    private static String originalPath(ObjectNode command) {
        return "/temporary-command-receipts/" + command.path("commandId").asText()
            + "/original?warehouseId=" + command.path("warehouseId").asText();
    }

}
