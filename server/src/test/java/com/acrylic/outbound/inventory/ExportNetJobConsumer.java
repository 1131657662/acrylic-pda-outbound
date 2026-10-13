package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.reports.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** HTTP task/file consumer; setup and synchronous worker scheduling are explicit test controls. */
final class ExportNetJobConsumer {
    final WebApplicationContext context;
    final CorrectionExportHttpClient wire;
    final Path evidence;
    ExportNetJobConsumer(WebApplicationContext context, CorrectionExportHttpClient wire, Path evidence) throws Exception {
        this.context = context; this.wire = wire; this.evidence = evidence; Files.createDirectories(evidence);
    }
    UUID create(String path, JsonNode body, UUID key) throws Exception {
        var response = wire.post(path, body, key); var view = data(response, 200);
        Files.write(evidence.resolve(key + "-create-response.json"), response.body());
        Files.writeString(evidence.resolve(key + "-request.json"), SubmissionJson.canonical(body));
        return UUID.fromString(view.path("id").asText());
    }
    ExportModels.Payload payload(UUID id) {
        return context.getBean(ExportJobQueries.class).payload(context.getBean(BackgroundJobStore.class).find(id).orElseThrow());
    }
    ReportSnapshot snapshot(UUID id) throws Exception {
        return SnapshotJson.read(context.getBean(ExportFiles.class).snapshot(payload(id).snapshotHash()));
    }
    byte[] download(UUID id, String name, String prefix) throws Exception {
        complete(id); var response = wire.get("/exports/" + id + "/file");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Disposition").orElseThrow()).contains(prefix);
        var payload = payload(id); var files = context.getBean(ExportFiles.class);
        assertThat(response.body()).containsExactly(Files.readAllBytes(files.artifact(id, payload.artifact().token(), payload.format())));
        Files.write(evidence.resolve(name), response.body());
        SnapshotJson.write(snapshot(id), evidence.resolve(name + ".snapshot.json"));
        Files.writeString(evidence.resolve(name + ".headers.json"),
            context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class).writeValueAsString(response.headers().map()));
        return response.body();
    }
    private void complete(UUID id) throws Exception {
        for (int index = 0; index < 30; index++) {
            var view = data(wire.get("/exports/" + id), 200);
            assertThat(view.path("status").asText()).as("export: %s", view).isNotIn("FAILED", "EXPIRED", "INVALIDATED");
            if (view.path("status").asText().equals("SUCCEEDED")) return;
            assertThat(context.getBean(BackgroundJobWorker.class).runOnce("e05-http-consumer")).isPresent();
        }
        throw new AssertionError("Export did not finish");
    }
}
