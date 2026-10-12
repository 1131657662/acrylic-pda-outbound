package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** Actual worker render and authenticated file download, alongside snapshot consumers. */
final class HeaderCompensationExportChecks {
    private final CorrectionAccountingHttpFixture fixture;
    private final CorrectionExportHttpClient wire;
    private HeaderCompensationExportChecks(CorrectionAccountingHttpFixture f) throws Exception {
        fixture = f; wire = CorrectionExportHttpClient.login(f.context,
            new ExportModels.Scope(f.makerIdentity.user().id(), UUID.fromString(f.warehouse), UUID.fromString(f.order)));
    }

    static void verify(CorrectionAccountingHttpFixture f) throws Exception {
        var checks = new HeaderCompensationExportChecks(f);
        checks.export("XLSX", 2); checks.export("XLSX", 5); checks.export("PDF", 5);
    }

    private void export(String format, long version) throws Exception {
        var f = fixture; var body = SubmissionJson.object().put("format", format).put("basis", "CORRECTED").put("resultVersion", version);
        var response = wire.post("/orders/" + f.order + "/exports", body, UUID.randomUUID());
        Files.write(f.evidence.resolve("export-v" + version + "-" + format + "-response.json"), response.body());
        UUID id = UUID.fromString(data(response, 200).path("id").asText()); var job = completed(id);
        var payload = f.context.getBean(ExportJobQueries.class).payload(job); var files = f.context.getBean(ExportFiles.class);
        var snapshot = SubmissionJson.parse(Files.readString(files.snapshot(payload.snapshotHash())));
        assertThat(snapshot.at("/orders/0/resultVersion").asLong()).isEqualTo(version);
        assertThat(snapshot.at("/orders/0/adjustments")).hasSize(version == 2 ? 1 : 4);
        String name = "export-v" + version + "." + payload.format().extension;
        Files.writeString(f.evidence.resolve(name + ".snapshot.json"), snapshot.toPrettyString());
        var download = wire.get("/exports/" + id + "/file"); assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).containsExactly(Files.readAllBytes(files.artifact(id, payload.artifact().token(), payload.format())));
        Files.write(f.evidence.resolve(name), download.body());
    }

    private BackgroundJob completed(UUID id) throws Exception {
        var f = fixture;
        for (int index = 0; index < 30; index++) {
            var status = data(wire.get("/exports/" + id), 200);
            assertThat(status.path("status").asText()).as("export: %s", status).isNotIn("FAILED", "EXPIRED", "INVALIDATED");
            if (status.path("status").asText().equals("SUCCEEDED")) return f.context.getBean(BackgroundJobStore.class).find(id).orElseThrow();
            assertThat(f.context.getBean(BackgroundJobWorker.class).runOnce("header-compensation-http")).isPresent();
        }
        throw new AssertionError("Export did not complete");
    }
}
