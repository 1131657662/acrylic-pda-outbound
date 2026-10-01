package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** Real acceptance, worker rendering and authenticated downloads of corrected and restored results. */
final class CorrectionAccountingExportChecks {
    private final CorrectionAccountingHttpFixture f;
    private final CorrectionExportHttpClient wire;
    private CorrectionAccountingExportChecks(CorrectionAccountingHttpFixture f) throws Exception {
        this.f = f; wire = CorrectionExportHttpClient.login(f.context,
            new ExportModels.Scope(f.makerIdentity.user().id(), UUID.fromString(f.warehouse), UUID.fromString(f.order)));
    }

    static void verify(CorrectionAccountingHttpFixture f) throws Exception {
        var checks = new CorrectionAccountingExportChecks(f);
        checks.export("XLSX", 2); checks.export("XLSX", 9); checks.export("PDF", 9); checks.export("XLSX", 1);
    }

    private void export(String format, long version) throws Exception {
        var body = SubmissionJson.object().put("format", format).put("basis", version == 1 ? "ORIGINAL_CONFIRMED" : "CORRECTED");
        if (version > 1) body.put("resultVersion", version);
        var created = data(wire.post("/orders/" + f.order + "/exports", body, UUID.randomUUID()), 200);
        UUID id = UUID.fromString(created.path("id").asText()); var job = completed(id);
        var payload = f.context.getBean(ExportJobQueries.class).payload(job); var files = f.context.getBean(ExportFiles.class);
        var snapshot = SubmissionJson.parse(Files.readString(files.snapshot(payload.snapshotHash())));
        assertSnapshot(snapshot, version);
        String name = "export-v" + version + "." + payload.format().extension;
        Files.writeString(f.evidence.resolve(name + ".snapshot.json"), snapshot.toPrettyString());
        var download = wire.get("/exports/" + id + "/file"); assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).containsExactly(Files.readAllBytes(files.artifact(id, payload.artifact().token(), payload.format())));
        assertThat(download.body().length).isGreaterThan(100);
        Files.write(f.evidence.resolve(name), download.body());
    }

    private BackgroundJob completed(UUID id) throws Exception {
        for (int index = 0; index < 30; index++) {
            var status = data(wire.get("/exports/" + id), 200);
            assertThat(status.path("status").asText()).as("export: %s", status).isNotIn("FAILED", "EXPIRED", "INVALIDATED");
            if (status.path("status").asText().equals("SUCCEEDED")) return f.context.getBean(BackgroundJobStore.class).find(id).orElseThrow();
            assertThat(f.context.getBean(BackgroundJobWorker.class).runOnce("accounting-http")).isPresent();
        }
        throw new AssertionError("Export did not complete");
    }

    private void assertSnapshot(JsonNode snapshot, long version) {
        var order = snapshot.at("/orders/0"); assertThat(order.path("resultVersion").asLong()).isEqualTo(version);
        if (version == 1) { assertThat(order.path("adjustments")).isEmpty(); return; }
        assertThat(order.path("adjustments")).hasSize(version == 2 ? 1 : 8);
        long total = 0; for (var line : order.path("effectiveLines")) total += line.path("weightGrams").asLong();
        assertThat(total).isEqualTo(version == 2 ? f.originalWeight + 20 : f.originalWeight);
        for (var adjustment : order.path("adjustments")) {
            assertThat(adjustment.path("approver").asText()).isNotBlank();
            assertThat(adjustment.path("evidenceReference").asText()).contains("correction_line:");
        }
    }
}