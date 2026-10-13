package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.BackgroundJobStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** Restored source bytes and database rows are the oracle, not newly authored fixtures. */
public final class ExportNetHistoryChecks {
    private ExportNetHistoryChecks() { }
    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class);
        var before = businessRows(jdbc);
        var ids = jdbc.query("SELECT BIN_TO_UUID(id) FROM background_job WHERE job_type='REPORT_RENDER_V1' AND status='SUCCEEDED' ORDER BY id",
            (rs, n) -> UUID.fromString(rs.getString(1)));
        assertThat(ids).isNotEmpty();
        var scopes = new HashSet<ExportModels.Scope>();
        for (UUID id : ids) {
            var p = context.getBean(ExportJobQueries.class).payload(context.getBean(BackgroundJobStore.class).find(id).orElseThrow());
            var consumer = new ExportNetJobConsumer(context, CorrectionExportHttpClient.login(context, p.scope()),
                attempt.resolve("export-history/" + id));
            historicalFile(context, consumer, id);
            if (scopes.add(p.scope())) latestResult(jdbc, consumer, p.scope());
        }
        assertThat(businessRows(jdbc)).isEqualTo(before);
        Files.writeString(attempt.resolve("export-history/result.json"), SubmissionJson.object().put("oldJobs", ids.size())
            .put("scopes", scopes.size()).put("businessRowsUnchanged", true).toPrettyString());
    }

    private static void historicalFile(WebApplicationContext context, ExportNetJobConsumer c, UUID id) throws Exception {
        var old = c.payload(id); var files = context.getBean(ExportFiles.class);
        byte[] expected = Files.readAllBytes(files.artifact(id, old.artifact().token(), old.format()));
        byte[] frozen = Files.readAllBytes(files.snapshot(old.snapshotHash()));
        assertThat(ImportFiles.sha256(expected)).isEqualTo(old.artifact().sha256());
        String prefix = old.basis().equals("CORRECTED") ? "corrected-v" + old.version() : "original-v1-";
        assertThat(c.download(id, "historical." + old.format().extension, prefix)).containsExactly(expected);
        UUID retry = c.create("/exports/" + id + "/retry", SubmissionJson.object(), UUID.randomUUID());
        assertThat(c.payload(retry).snapshotHash()).isEqualTo(old.snapshotHash());
        assertThat(c.payload(retry).basis()).isEqualTo(old.basis());
        assertThat(c.payload(retry).version()).isEqualTo(old.version());
        c.download(retry, "regenerated." + old.format().extension, prefix);
        assertThat(Files.readAllBytes(files.snapshot(old.snapshotHash()))).containsExactly(frozen);
    }

    private static void latestResult(JdbcTemplate jdbc, ExportNetJobConsumer c, ExportModels.Scope scope) throws Exception {
        UUID job = c.create("/orders/" + scope.order() + "/exports",
            SubmissionJson.object().put("format", "XLSX").put("basis", "CURRENT_EFFECTIVE"), UUID.randomUUID());
        var old = c.snapshot(job);
        var expected = jdbc.queryForMap("SELECT result_version,actual_count,weight_g FROM order_result_version"
            + " WHERE order_id=? ORDER BY result_version DESC LIMIT 1", Ids.bytes(scope.order()));
        var order = old.orders().getFirst();
        assertThat(order.resultVersion()).isEqualTo(((Number) expected.get("result_version")).longValue());
        assertThat(order.effectiveLines().stream().mapToLong(line -> line.count()).sum())
            .isEqualTo(((Number) expected.get("actual_count")).longValue());
        assertThat(order.effectiveLines().stream().mapToLong(line -> Long.parseLong(line.weightGrams())).sum())
            .isEqualTo(((Number) expected.get("weight_g")).longValue());
        var day = order.confirmedAt().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate();
        var summary = data(c.wire.get("/exports/summary?fromDate=" + day + "&throughDate=" + day), 200);
        assertThat(summary.path("basis").asText()).isEqualTo("CURRENT_EFFECTIVE");
        assertThat(summary.path("orders")).isNotEmpty();
        c.download(job, "historical-latest.xlsx", "latest-net-");
    }

    private static Map<String, List<String>> businessRows(JdbcTemplate jdbc) throws Exception {
        var rows = new TreeMap<String, List<String>>();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        for (String table : List.of("outbound_order", "submission", "dispatch_record", "order_result_version", "order_effective_scan",
            "correction_document", "correction_line", "inventory_ledger", "inventory_position", "bale_commitment", "cycle_reopening")) {
            var encoded = new ArrayList<String>();
            for (var row : jdbc.queryForList("SELECT * FROM " + table)) encoded.add(json.writeValueAsString(row));
            Collections.sort(encoded); rows.put(table, encoded);
        }
        return rows;
    }
}
