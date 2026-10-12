package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.CorrectionExportSource;
import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.reports.SnapshotValidator;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP signed original commands prove scalar compensation and preservation of unrelated successors. */
public final class HeaderCompensationHttpChecks {
    private HeaderCompensationHttpChecks() { }

    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        positive(context, attempt, true); positive(context, attempt, false);
        new HeaderCompensationConflictChecks(context, attempt).verify();
    }

    private static void positive(WebApplicationContext context, Path attempt, boolean deduct) throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, deduct, "header-positive-" + deduct);
        var original = f.original(); var header = header(f);
        String document = approve(f, SubmissionJson.object().put("vehicle", "P06-primary").set("remark", header.get("remark")));
        approve(f, SubmissionJson.object().put("remark", "P06-disjoint")); f.approve(1, f.originalDispatchWeight + 25);
        var before = preserved(f); var request = f.command(document, "WITHDRAW");
        var pendingBytes = f.checker.command(request, 200); assertThat(f.checker.command(request, 200)).containsExactly(pendingBytes);
        assertThat(f.maker.command(f.command(document, "WITHDRAW"), 403)).isNotEmpty();
        var confirm = f.command(document, "WITHDRAW_CONFIRM");
        assertThat(f.checker.command(confirm, 403)).isNotEmpty();
        byte[] bytes = f.maker.command(confirm, 200); var receipt = SupervisorCorrectionReceipt.verify(bytes, f.publicKey);
        assertThat(f.maker.command(confirm, 200)).containsExactly(bytes);
        assertThat(header(f).path("vehicle")).isEqualTo(header.path("vehicle"));
        assertThat(header(f).path("remark").asText()).isEqualTo("P06-disjoint");
        assertThat(preserved(f)).isEqualTo(before); assertThat(f.original()).isEqualTo(original);
        assertThat(f.result().path("version").asLong()).isEqualTo(5);
        assertThat(receipt.at("/businessDecision/headerRestoration/restoredFields")).containsExactly(SubmissionJson.parse("\"vehicle\""));
        Files.write(f.evidence.resolve("withdrawal.json"), bytes);
        Files.writeString(f.evidence.resolve("confirmed-command.json"), SubmissionJson.canonical(confirm));
        Files.writeString(f.evidence.resolve("restart-input.json"), SubmissionJson.object().put("orderId", f.order)
            .put("documentId", document).put("username", f.makerIdentity.user().username()).put("warehouseId", f.warehouse)
            .put("responseSha256", sha(bytes)).set("command", confirm).toPrettyString());
        exports(f, header); Files.writeString(f.evidence.resolve("completed.json"), f.result().toPrettyString());
    }

    static String approve(CorrectionAccountingHttpFixture f, JsonNode patch) throws Exception {
        var body = f.headerCreate(); body.set("headerPatch", patch);
        String document = f.signed(f.maker, body).path("documentId").asText();
        f.signed(f.checker, f.command(document, "APPROVE")); return document;
    }

    static JsonNode header(CorrectionAccountingHttpFixture f) {
        var snapshot = SubmissionJson.parse(f.jdbc.queryForObject("SELECT CAST(header_snapshot AS CHAR) FROM order_result_version"
            + " WHERE order_id=UUID_TO_BIN(?) ORDER BY result_version DESC LIMIT 1", String.class, f.order));
        return f.context.getBean(com.acrylic.outbound.correction.CorrectionHeaderOrigin.class).normalize(UUID.fromString(f.order), snapshot);
    }

    static JsonNode preserved(CorrectionAccountingHttpFixture f) {
        var result = SubmissionJson.object(); result.put("count", f.result().path("count").asText())
            .put("weight", f.result().path("weightG").asText()).put("postings", f.postings()).put("ledgerWeight", f.correctionWeight());
        for (String table : new String[]{"inventory_ledger", "correction_ledger", "correction_effect", "bale_commitment", "cycle_reopening"})
            result.put(table, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        String scans = f.jdbc.queryForObject("SELECT CAST(JSON_ARRAYAGG(JSON_OBJECT('dispatch',BIN_TO_UUID(dispatch_id),"
            + "'effective',is_effective,'weight',effective_weight_g,'line',BIN_TO_UUID(latest_correction_line_id))) AS CHAR)"
            + " FROM order_effective_scan WHERE order_id=UUID_TO_BIN(?) AND result_version=?", String.class,
            f.order, f.result().path("version").asLong());
        result.put("scans", scans); return result;
    }

    private static void exports(CorrectionAccountingHttpFixture f, JsonNode originalHeader) throws Exception {
        var scope = new ExportModels.Scope(f.makerIdentity.user().id(), UUID.fromString(f.warehouse), UUID.fromString(f.order));
        var source = f.context.getBean(CorrectionExportSource.class);
        var approved = source.read(scope, 2, Instant.now()); SnapshotValidator.validate(approved);
        assertThat(approved.orders().getFirst().effectiveHeader().vehicle()).isEqualTo("P06-primary");
        var compensated = source.read(scope, 5, Instant.now()); SnapshotValidator.validate(compensated);
        var order = compensated.orders().getFirst(); assertThat(order.effectiveHeader().vehicle()).isEqualTo(originalHeader.path("vehicle").asText());
        assertThat(order.effectiveHeader().remark()).isEqualTo("P06-disjoint"); assertThat(order.adjustments()).hasSize(4);
        Files.writeString(f.evidence.resolve("export-approved.snapshot.json"), new com.fasterxml.jackson.databind.ObjectMapper()
            .findAndRegisterModules().writeValueAsString(approved));
        Files.writeString(f.evidence.resolve("export-compensated.snapshot.json"), new com.fasterxml.jackson.databind.ObjectMapper()
            .findAndRegisterModules().writeValueAsString(compensated));
        HeaderCompensationExportChecks.verify(f);
    }

    private static String sha(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
