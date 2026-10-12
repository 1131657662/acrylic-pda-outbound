package com.acrylic.outbound.inventory;

import com.acrylic.outbound.correction.CorrectionHeaderOrigin;
import com.acrylic.outbound.exports.CorrectionExportSource;
import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.reports.SnapshotValidator;
import com.acrylic.outbound.reports.Adjustment;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual historical signatures and rows are consumed as-is; no replacement CREATE/APPROVE is issued. */
public final class HeaderCompensationHistoryChecks {
    private HeaderCompensationHistoryChecks() { }

    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        var evidence = attempt.resolve("historical-checks"); Files.createDirectories(evidence);
        var jdbc = context.getBean(JdbcTemplate.class);
        var originals = attempt.resolve("historical-originals/header-withdraw");
        byte[] createBytes = Files.readAllBytes(originals.resolve("6bae4351-1b52-4a99-925d-db6a1b0c1960.json"));
        byte[] approveBytes = Files.readAllBytes(originals.resolve("f9101c19-9ba4-4f0b-b65c-abd23a09bab3.json"));
        var key = attempt.resolve("private/server-public.der");
        var create = SupervisorCorrectionReceipt.verify(createBytes, key);
        var approved = SupervisorCorrectionReceipt.verify(approveBytes, key);
        assertThat(approved.at("/businessDecision/headerProof").isMissingNode()).isTrue();
        String order = create.path("orderId").asText(); String document = create.path("documentId").asText();
        var maker = new CorrectionReadHttp(username(jdbc, create), evidence, "historical-maker");
        var checker = new CorrectionReadHttp(username(jdbc, approved), evidence, "historical-checker");
        var before = immutableFacts(jdbc, order);
        String warehouse = create.at("/actor/warehouseId").asText();
        var pending = command(checker, document, warehouse, "WITHDRAW");
        SupervisorCorrectionReceipt.verify(pending, key);
        byte[] confirmed = command(maker, document, warehouse, "WITHDRAW_CONFIRM");
        var result = SupervisorCorrectionReceipt.verify(confirmed, key);
        assertThat(result.at("/businessDecision/resultVersion").asLong()).isEqualTo(3);
        assertThat(result.at("/businessDecision/headerRestoration/restoredFields")).containsExactly(SubmissionJson.parse("\"orderNo\""));
        assertThat(immutableFacts(jdbc, order)).isEqualTo(before);
        Files.write(evidence.resolve("compensated.json"), confirmed); Files.writeString(evidence.resolve("immutable-facts.json"), before.toPrettyString());
        assertThat(stored(jdbc, create.path("commandId").asText())).containsExactly(createBytes);
        assertThat(stored(jdbc, approved.path("commandId").asText())).containsExactly(approveBytes);
        exports(context, create, evidence);
    }

    private static byte[] command(CorrectionReadHttp wire, String document, String warehouse, String kind) throws Exception {
        var context = SubmissionJson.parse(wire.get("/corrections/" + document + "/context?warehouseId=" + warehouse + "&commandType=" + kind, 200));
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", kind)
            .put("commandId", UUID.randomUUID().toString()).put("documentId", document).put("warehouseId", warehouse)
            .put("transportRecoveryEpoch", "1").put("reason", "P06 historical original compensation");
        body.set("expected", context.path("expected")); byte[] bytes = wire.command(body);
        assertThat(wire.command(body)).containsExactly(bytes); return bytes;
    }

    private static void exports(WebApplicationContext context, JsonNode original, Path evidence) throws Exception {
        var scope = new ExportModels.Scope(UUID.fromString(original.at("/actor/principalId").asText()),
            UUID.fromString(original.at("/actor/warehouseId").asText()), UUID.fromString(original.path("orderId").asText()));
        var cutoff = Instant.now();
        for (long version : new long[]{2, 3}) {
            var export = context.getBean(CorrectionExportSource.class).read(scope, version, cutoff);
            SnapshotValidator.validate(export);
            var order = export.orders().getFirst();
            var shipmentDay = order.confirmedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            assertThat(shipmentDay).isBefore(cutoff.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate());
            assertThat(export.metadata().fromDate()).isEqualTo(shipmentDay);
            assertThat(export.metadata().throughDate()).isEqualTo(shipmentDay);
            assertThat(export.metadata().cutoff()).isEqualTo(cutoff);
            assertThat(order.adjustments()).hasSize(Math.toIntExact(version - 1));
            if (version == 3) assertThat(order.adjustments().getLast().kind()).isEqualTo(Adjustment.Kind.CORRECTION_WITHDRAWAL);
            Files.writeString(evidence.resolve("snapshot-v" + version + ".json"),
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(export));
        }
    }

    private static String username(JdbcTemplate jdbc, JsonNode receipt) {
        return jdbc.queryForObject("SELECT username FROM app_user WHERE id=UUID_TO_BIN(?)", String.class, receipt.at("/actor/principalId").asText());
    }

    private static byte[] stored(JdbcTemplate jdbc, String key) {
        return jdbc.queryForObject("SELECT response_bytes FROM correction_command_receipt WHERE original_key=UUID_TO_BIN(?)", byte[].class, key);
    }

    private static JsonNode immutableFacts(JdbcTemplate jdbc, String order) {
        var result = SubmissionJson.object();
        result.put("originalVersions", jdbc.queryForObject("SELECT CAST(JSON_ARRAYAGG(JSON_OBJECT('version',result_version,'header',header_snapshot,"
            + "'count',actual_count,'weight',weight_g)) AS CHAR) FROM order_result_version WHERE order_id=UUID_TO_BIN(?) AND result_version<=2", String.class, order));
        for (String table : new String[]{"inventory_ledger", "correction_ledger", "correction_effect", "bale_commitment", "cycle_reopening"})
            result.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        return result;
    }
}
