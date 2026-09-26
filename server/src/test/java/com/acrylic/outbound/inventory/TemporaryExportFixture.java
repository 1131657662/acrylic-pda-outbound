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

final class TemporaryExportFixture {
    final TemporaryConfirmationFixture f;final TemporaryCandidateHttp http=new TemporaryCandidateHttp();
    final String order,warehouse;final Path output;final JsonNode decision;
    TemporaryExportFixture(WebApplicationContext context,boolean shortShipment,Path output) throws Exception {
        f=new TemporaryConfirmationFixture(context,shortShipment);this.output=output;
        warehouse=f.source.f.web.identity().warehouse().toString();
        if(shortShipment) { var supervisor=f.supervisor();f.execute(f.command("SHORT_APPROVE",supervisor),supervisor); }
        decision=SubmissionJson.parse(f.execute(f.command("NORMAL_CONFIRM",f.operator()),f.operator()));
        order=decision.at("/originalContext/canonicalOrderId").asText();http.login(f.operator().getUsername());
    }
    String path() { return "/orders/"+order+"/exports?warehouseId="+warehouse; }
    JsonNode command(String format) { return SubmissionJson.object().put("format",format).put("basis","ORIGINAL_CONFIRMED"); }
    JsonNode create(String format,String key) throws Exception { return http.post(path(),command(format),key); }
    ReportSnapshot snapshot(JsonNode view) throws Exception {
        var job=f.bean(BackgroundJobStore.class).find(UUID.fromString(view.path("id").asText())).orElseThrow();
        var payload=f.bean(ExportJobQueries.class).payload(job);
        return SnapshotJson.read(f.bean(ExportFiles.class).snapshot(payload.snapshotHash()));
    }
    byte[] generate(JsonNode view,String filename) throws Exception {
        UUID id=UUID.fromString(view.path("id").asText());var jobs=f.bean(BackgroundJobStore.class);
        for(int i=0;i<30 && jobs.find(id).orElseThrow().status()!=BackgroundJob.Status.SUCCEEDED;i++) {
            var outcome=f.bean(BackgroundJobWorker.class).runOnce("temp-export-http").orElseThrow();
            assertThat(outcome.outcome()).isEqualTo("SUCCEEDED");
        }
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo(BackgroundJob.Status.SUCCEEDED);
        byte[] bytes=http.original("/exports/"+id+"/file?warehouseId="+warehouse);
        Files.write(output.resolve(filename),bytes);SnapshotJson.write(snapshot(view),output.resolve(filename+".snapshot.json"));return bytes;
    }
    int jobs() {
        return f.jdbc.queryForObject("SELECT COUNT(*) FROM background_job",Integer.class);
    }
}
