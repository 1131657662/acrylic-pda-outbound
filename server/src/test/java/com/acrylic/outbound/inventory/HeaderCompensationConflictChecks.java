package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.inventory.HeaderCompensationHttpChecks.*;
import static org.assertj.core.api.Assertions.assertThat;

final class HeaderCompensationConflictChecks {
    private final WebApplicationContext context;
    private final Path attempt;
    HeaderCompensationConflictChecks(WebApplicationContext context, Path attempt) { this.context = context; this.attempt = attempt; }

    void verify() throws Exception { aba(); afterPending(); corruptCreate(); corruptSnapshot(); finalSql(); }

    private void aba() throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, false, "header-aba");
        String document = approve(f, SubmissionJson.object().put("vehicle", "P06-A"));
        approve(f, SubmissionJson.object().put("vehicle", "P06-B")); approve(f, SubmissionJson.object().put("vehicle", "P06-A"));
        var before = state(f, document); refused(f, document, "WITHDRAW", "CORRECTION_LEDGER_CONFLICT");
        assertThat(state(f, document)).isEqualTo(before);
    }

    private void afterPending() throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, false, "header-after-pending");
        String document = approve(f, SubmissionJson.object().put("vehicle", "P06-A"));
        f.signed(f.checker, f.command(document, "WITHDRAW")); approve(f, SubmissionJson.object().put("vehicle", "P06-B"));
        var before = state(f, document); refused(f, document, "WITHDRAW_CONFIRM", "CORRECTION_LEDGER_CONFLICT");
        assertThat(state(f, document)).isEqualTo(before);
    }

    private void corruptCreate() throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, false, "header-corrupt-create");
        String document = approve(f, SubmissionJson.object().put("vehicle", "P06-A"));
        Runnable corrupt = () -> f.jdbc.update("UPDATE correction_command_receipt SET canonical_command_bytes=?"
            + " WHERE document_id=UUID_TO_BIN(?) AND command_type='CREATE'", "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), document);
        org.assertj.core.api.Assertions.assertThatThrownBy(corrupt::run).hasMessageContaining("Correction command receipt is immutable");
        f.jdbc.execute("DROP TRIGGER correction_receipt_no_update");
        try { corrupt.run(); }
        finally { f.jdbc.execute("CREATE TRIGGER correction_receipt_no_update BEFORE UPDATE ON correction_command_receipt"
            + " FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Correction command receipt is immutable'; END"); }
        var before = state(f, document); refused(f, document, "WITHDRAW", "CORRECTION_RECEIPT_INTEGRITY_FAILURE");
        assertThat(state(f, document)).isEqualTo(before);
    }

    private void corruptSnapshot() throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, false, "header-corrupt-snapshot");
        String document = approve(f, SubmissionJson.object().put("vehicle", "P06-A"));
        f.jdbc.update("UPDATE order_result_version SET header_snapshot=JSON_SET(header_snapshot,'$.remark','corrupted')"
            + " WHERE order_id=UUID_TO_BIN(?) AND result_version=1", f.order);
        var before = state(f, document); refused(f, document, "WITHDRAW", "CORRECTION_RECEIPT_INTEGRITY_FAILURE");
        assertThat(state(f, document)).isEqualTo(before);
    }

    private void finalSql() throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, true, "header-final-sql");
        String document = approve(f, SubmissionJson.object().put("vehicle", "P06-A"));
        f.signed(f.checker, f.command(document, "WITHDRAW")); var before = state(f, document);
        var body = f.command(document, "WITHDRAW_CONFIRM");
        f.jdbc.execute("CREATE TRIGGER p06_header_tail BEFORE INSERT ON operation_event FOR EACH ROW BEGIN"
            + " IF NEW.event_type='CORRECTION_WITHDRAW_CONFIRM' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P06 tail SQL'; END IF; END");
        try { Files.write(f.evidence.resolve("tail-failure.json"), f.maker.command(body, 500)); }
        finally { f.jdbc.execute("DROP TRIGGER p06_header_tail"); }
        assertThat(state(f, document)).isEqualTo(before);
        byte[] completed = f.maker.command(body, 200); SupervisorCorrectionReceipt.verify(completed, f.publicKey);
        assertThat(f.maker.command(body, 200)).containsExactly(completed);
        assertThat(f.result().path("version").asLong()).isEqualTo(3);
    }

    private void refused(CorrectionAccountingHttpFixture f, String document, String kind, String code) throws Exception {
        var body = f.command(document, kind); byte[] bytes = (kind.equals("WITHDRAW") ? f.checker : f.maker).command(body, 409);
        Files.write(f.evidence.resolve(kind + "-denied.json"), bytes); assertThat(SubmissionJson.parse(bytes).path("code").asText()).isEqualTo(code);
    }

    private JsonNode state(CorrectionAccountingHttpFixture f, String document) {
        var state = SubmissionJson.object().put("header", f.jdbc.queryForObject("SELECT CAST(header_snapshot AS CHAR)"
            + " FROM order_result_version WHERE order_id=UUID_TO_BIN(?) ORDER BY result_version DESC LIMIT 1", String.class, f.order));
        state.set("preserved", preserved(f)); state.set("result", f.result());
        state.put("document", f.jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)", String.class, document));
        for (String table : new String[]{"correction_command_receipt", "order_result_version", "order_sync_outbox", "operation_event", "api_idempotency_request"})
            state.put(table, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        return state;
    }
}
