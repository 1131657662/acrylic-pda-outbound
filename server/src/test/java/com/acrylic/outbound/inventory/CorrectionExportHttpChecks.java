package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** D slice 3: HTTP acceptance, persisted version, real worker, and HTTP download. */
public final class CorrectionExportHttpChecks {
    private final WebApplicationContext context;
    private final ExportModels.Scope scope;
    private final Path output;
    private CorrectionExportHttpClient wire;

    private CorrectionExportHttpChecks(WebApplicationContext context,ExportModels.Scope scope,Path attempt) {
        this.context=context;this.scope=scope;this.output=attempt.resolve("storage/corrected-http");
    }

    public static void verify(WebApplicationContext context,ExportModels.Scope scope,Path attempt) throws Exception {
        var checks=new CorrectionExportHttpChecks(context,scope,attempt);
        checks.wire=CorrectionExportHttpClient.login(context,scope);
        Files.createDirectories(checks.output);
        checks.rejectInvalidSelections();checks.defaultVersion();
        checks.corrected("XLSX");checks.corrected("PDF");
        Files.writeString(attempt.resolve("d-slice3-http-completed.marker"),
                "real Web HTTP + MySQL + worker + XLSX/PDF download; v1 default, v2 selection, replay, retry, refusal checks passed\n");
    }

    private String createPath() { return "/orders/"+scope.order()+"/exports"; }

    private ObjectNode command(String format,String basis,Long version) {
        var body=SubmissionJson.object().put("format",format).put("basis",basis);
        if(version!=null)body.put("resultVersion",version);
        return body;
    }

    private void rejectInvalidSelections() throws Exception {
        for(Long version:new Long[]{null,0L,1L})
            data(wire.post(createPath(),command("XLSX","CORRECTED",version),UUID.randomUUID()),400);
        data(wire.post(createPath(),command("XLSX","ORIGINAL_CONFIRMED",2L),UUID.randomUUID()),400);
        data(wire.post(createPath(),command("SCAN_XLSX","CORRECTED",2L),UUID.randomUUID()),400);
        var range=command("XLSX","CORRECTED",2L);
        range.putObject("range").put("fromDate","2026-09-28").put("throughDate","2026-09-28");
        data(wire.post("/exports",range,UUID.randomUUID()),400);
        data(wire.post(createPath(),command("XLSX","CORRECTED",999L),UUID.randomUUID()),409);
    }

    private void defaultVersion() throws Exception {
        var key=UUID.randomUUID();var body=command("XLSX","ORIGINAL_CONFIRMED",null);
        var first=data(wire.post(createPath(),body,key),200);
        assertThat(first.path("resultVersion").asLong()).isEqualTo(1L);
        var explicit=data(wire.post(createPath(),body.deepCopy().put("resultVersion",1),key),200);
        assertThat(explicit.path("id")).isEqualTo(first.path("id"));
        var job=generated(first);var p=context.getBean(ExportJobQueries.class).payload(job);
        // Historical payloads have no version field; replay and status must still resolve them to v1.
        context.getBean(org.springframework.jdbc.core.JdbcTemplate.class).update(
                "UPDATE background_job SET payload=JSON_REMOVE(payload,'$.resultVersion') WHERE id=?",
                com.acrylic.outbound.common.Ids.bytes(job.id()));
        assertThat(data(wire.post(createPath(),body,key),200).path("resultVersion").asLong()).isEqualTo(1L);
        var download=wire.get("/exports/"+job.id()+"/file");
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.headers().firstValue("Content-Disposition").orElseThrow()).contains("original-v1-");
        assertThat(p.basis()).isEqualTo("ORIGINAL_CONFIRMED");
    }

    private void corrected(String format) throws Exception {
        var key=UUID.randomUUID();var body=command(format,"CORRECTED",2L);
        var created=data(wire.post(createPath(),body,key),200);
        assertThat(created.path("resultVersion").asLong()).isEqualTo(2L);
        assertThat(created.path("basis").asText()).isEqualTo("CORRECTED");
        assertThat(data(wire.post(createPath(),body,key),200).path("id")).isEqualTo(created.path("id"));
        var conflict=wire.post(createPath(),body.deepCopy().put("resultVersion",3),key);
        data(conflict,409);assertThat(SubmissionJson.parse(conflict.body()).path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        var job=generated(created);verifyDownload(job,format);
        var replay=data(wire.post(createPath(),body,key),200);
        assertThat(replay.path("id")).isEqualTo(created.path("id"));
        assertThat(replay.path("resultVersion").asLong()).isEqualTo(2L);
        var listed=data(wire.get(createPath()),200).path("items");
        assertThat(listed.findValuesAsText("id")).contains(job.id().toString());
        if(format.equals("XLSX"))verifyRetry(job);
    }

    private BackgroundJob generated(JsonNode created) throws Exception {
        UUID id=UUID.fromString(created.path("id").asText());
        var jobs=context.getBean(BackgroundJobStore.class);
        for(int index=0;index<30;index++) {
            var status=data(wire.get("/exports/"+id),200);
            if(status.path("status").asText().equals("SUCCEEDED"))return jobs.find(id).orElseThrow();
            assertThat(status.path("status").asText()).isNotIn("FAILED","EXPIRED","INVALIDATED");
            assertThat(context.getBean(BackgroundJobWorker.class).runOnce("d-slice3-http")).isPresent();
        }
        throw new AssertionError("HTTP export did not complete");
    }

    private void verifyDownload(BackgroundJob job,String format) throws Exception {
        var queries=context.getBean(ExportJobQueries.class);var p=queries.payload(job);
        assertThat(p.version()).isEqualTo(2L);
        var files=context.getBean(ExportFiles.class);
        var snapshot=SubmissionJson.parse(Files.readString(files.snapshot(p.snapshotHash())));
        assertThat(snapshot.at("/metadata/basis").asText()).isEqualTo("CORRECTED");
        assertThat(snapshot.at("/orders/0/resultVersion").asLong()).isEqualTo(2L);
        assertThat(snapshot.at("/orders/0/adjustments")).hasSize(2);
        var response=wire.get("/exports/"+job.id()+"/file");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Disposition").orElseThrow()).contains("corrected-v2-");
        assertThat(response.body()).hasSize((int)p.artifact().bytes());
        assertThat(response.body()).isEqualTo(Files.readAllBytes(files.artifact(job.id(),p.artifact().token(),p.format())));
        Files.write(output.resolve("corrected."+p.format().extension),response.body());
        Files.writeString(output.resolve("snapshot.json"),snapshot.toPrettyString());
        System.out.println("D-SLICE3 HTTP "+format+" resultVersion=2 bytes="+response.body().length+" job="+job.id());
    }

    private void verifyRetry(BackgroundJob original) throws Exception {
        var retried=data(wire.post("/exports/"+original.id()+"/retry",SubmissionJson.object(),UUID.randomUUID()),200);
        assertThat(retried.path("resultVersion").asLong()).isEqualTo(2L);
        var job=generated(retried);var queries=context.getBean(ExportJobQueries.class);
        assertThat(queries.payload(job).snapshotHash()).isEqualTo(queries.payload(original).snapshotHash());
        assertThat(queries.payload(job).retryOf()).isEqualTo(original.id());
        verifyDownload(job,"XLSX");
    }
}
