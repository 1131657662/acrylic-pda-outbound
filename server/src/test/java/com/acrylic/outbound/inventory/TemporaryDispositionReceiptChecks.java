package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Corruption and unsupported-command probes run separately from the trusted positive flow. */
final class TemporaryDispositionReceiptChecks {
    private final JdbcTemplate jdbc;
    private final TemporaryDispositionHttp http;
    private final TemporaryDispositionSqlChecks sql;

    TemporaryDispositionReceiptChecks(JdbcTemplate jdbc, TemporaryDispositionHttp http, TemporaryDispositionSqlChecks sql) {
        this.jdbc = jdbc;
        this.http = http;
        this.sql = sql;
    }

    void unsupportedReopen(ObjectNode disposition) throws Exception {
        var command = disposition.deepCopy().put("commandType", "REOPEN_WORK")
            .put("commandId", UUID.randomUUID().toString());
        var original = (ObjectNode) command.remove("dispositionManifest");
        var retained = SubmissionJson.object().put("version", 1);
        var items = retained.putArray("items");
        original.path("items").forEach(item -> items.add(SubmissionJson.object()
            .put("baleId", item.path("baleId").asText())
            .put("expectedDispatchCycle", "1")
            .put("recordSha256", "0".repeat(64))));
        ((ObjectNode) command.path("expected")).putNull("dispositionManifestSha256")
            .putNull("dispositionManifestVersion").put("retainedManifestSha256", SubmissionJson.sha(retained))
            .put("retainedManifestVersion", "1");
        var before = sql.snapshot("reopen-before");
        assertThat(SubmissionJson.parse(http.command(command, 409)).path("code").asText())
            .isEqualTo("COMMAND_EXECUTION_NOT_IMPLEMENTED");
        assertThat(sql.snapshot("reopen-after")).isEqualTo(before);
    }

    void corruptCycle(ObjectNode rejection, byte[] original) throws Exception {
        String submission = rejection.path("submissionId").asText();
        var item = jdbc.queryForMap("SELECT BIN_TO_UUID(i.disposition_id) disposition,BIN_TO_UUID(i.bale_id) bale,i.dispatch_cycle "
            + "FROM temporary_physical_disposition_item i JOIN temporary_physical_disposition d ON d.id=i.disposition_id "
            + "WHERE d.submission_id=UUID_TO_BIN(?) ORDER BY i.bale_id LIMIT 1", submission);
        String update = "UPDATE temporary_physical_disposition_item SET dispatch_cycle=? "
            + "WHERE disposition_id=UUID_TO_BIN(?) AND bale_id=UUID_TO_BIN(?)";
        var before = sql.snapshot("receipt-before-corruption");
        long cycle = ((Number) item.get("dispatch_cycle")).longValue();
        assertThat(jdbc.update(update, cycle + 1, item.get("disposition"), item.get("bale"))).isEqualTo(1);
        try {
            var damaged = sql.snapshot("receipt-damaged-cycle");
            requireCorrupt(http.command(rejection, 409));
            requireCorrupt(http.get(originalPath(rejection), 409));
            assertThat(sql.snapshot("receipt-after-refusal")).isEqualTo(damaged);
        } finally {
            assertThat(jdbc.update(update, cycle, item.get("disposition"), item.get("bale"))).isEqualTo(1);
        }
        assertThat(sql.snapshot("receipt-restored")).isEqualTo(before);
        assertThat(http.command(rejection, 200)).isEqualTo(original);
        assertThat(http.get(originalPath(rejection))).isEqualTo(original);
    }

    private static String originalPath(ObjectNode command) {
        return "/temporary-command-receipts/" + command.path("commandId").asText()
            + "/original?warehouseId=" + command.path("warehouseId").asText();
    }

    private static void requireCorrupt(byte[] response) {
        assertThat(SubmissionJson.parse(response).path("code").asText()).isEqualTo("COMMAND_RECEIPT_INTEGRITY_FAILURE");
    }
}
