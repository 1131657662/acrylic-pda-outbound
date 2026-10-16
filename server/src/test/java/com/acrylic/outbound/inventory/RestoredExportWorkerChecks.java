package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;
import static org.assertj.core.api.Assertions.assertThat;

/** Authenticated HTTP creates a real pending export; a restored JVM executes its original task. */
public final class RestoredExportWorkerChecks {
    private RestoredExportWorkerChecks() { }

    public static void prepare(WebApplicationContext context,Path attempt) throws Exception {
        var original=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("private/formal-http-input.json")));
        var scope=new ExportModels.Scope(UUID.fromString(original.at("/session/userId").asText()),
            UUID.fromString(original.at("/session/warehouseId").asText()),UUID.fromString(original.at("/assignment/orderId").asText()));
        var wire=CorrectionExportHttpClient.login(context,scope);var key=UUID.randomUUID();
        var command=SubmissionJson.object().put("format","XLSX").put("basis","ORIGINAL_CONFIRMED");
        String path="/orders/"+scope.order()+"/exports";
        var response=wire.post(path,command,key);var created=data(response,200);
        assertThat(created.path("status").asText()).isEqualTo("PENDING");
        var id=UUID.fromString(created.path("id").asText());
        assertThat(data(wire.post(path,command,key),200).path("id")).isEqualTo(created.path("id"));
        data(wire.post(path,command.deepCopy().put("format","SCAN_XLSX"),key),409);
        var job=context.getBean(BackgroundJobStore.class).find(id).orElseThrow();
        assertThat(job.attemptCount()).isZero();assertThat(job.status()).isEqualTo(BackgroundJob.Status.PENDING);
        var input=SubmissionJson.object().put("job",id.toString()).put("key",key.toString())
            .put("actor",scope.actor().toString()).put("warehouse",scope.warehouse().toString())
            .put("order",scope.order().toString()).put("payload",job.payload());
        input.set("command",command);
        Files.write(attempt.resolve("pending-create-response.json"),response.body(),StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("worker-input.json"),SubmissionJson.canonical(input),StandardOpenOption.CREATE_NEW);
        saveSnapshot(context,job,attempt.resolve("pending-snapshot.json"));
    }

    public static void consume(WebApplicationContext context,Path attempt) throws Exception {
        var input=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("worker-input.json")));
        var scope=new ExportModels.Scope(UUID.fromString(input.path("actor").asText()),
            UUID.fromString(input.path("warehouse").asText()),UUID.fromString(input.path("order").asText()));
        var wire=CorrectionExportHttpClient.login(context,scope);
        var id=UUID.fromString(input.path("job").asText());var store=context.getBean(BackgroundJobStore.class);
        var original=store.find(id).orElseThrow();
        assertThat(original.status()).isEqualTo(BackgroundJob.Status.PENDING);
        assertThat(original.payload()).isEqualTo(input.path("payload").asText());assertThat(original.attemptCount()).isZero();
        assertThat(data(wire.get("/exports/"+id),200).path("status").asText()).isEqualTo("PENDING");
        var worker=context.getBean(BackgroundJobWorker.class);var run=worker.runOnce("d15-restored-export").orElseThrow();
        assertThat(run.jobId()).isEqualTo(id);assertThat(run.outcome()).isEqualTo("SUCCEEDED");
        var complete=store.find(id).orElseThrow();assertThat(complete.attemptCount()).isEqualTo(1);
        assertThat(complete.status()).isEqualTo(BackgroundJob.Status.SUCCEEDED);
        download(context,wire,complete,attempt);
        assertThat(worker.runOnce("d15-restored-replay")).isEmpty();
        assertThat(store.find(id).orElseThrow()).isEqualTo(complete);
        var replay=data(wire.post("/orders/"+scope.order()+"/exports",input.path("command"),
            UUID.fromString(input.path("key").asText())),200);
        assertThat(replay.path("id").asText()).isEqualTo(id.toString());assertThat(replay.path("attempts").asInt()).isEqualTo(1);
        Files.writeString(attempt.resolve("restored-replay.json"),replay.toPrettyString(),StandardOpenOption.CREATE_NEW);
        saveSnapshot(context,complete,attempt.resolve("restored-snapshot.json"));
        assertThat(Files.readAllBytes(attempt.resolve("restored-snapshot.json")))
            .containsExactly(Files.readAllBytes(attempt.resolve("pending-snapshot.json")));
    }

    private static void download(WebApplicationContext context,CorrectionExportHttpClient wire,
            BackgroundJob job,Path attempt) throws Exception {
        var response=wire.get("/exports/"+job.id()+"/file");assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Disposition").orElseThrow()).contains("original-v1-");
        var payload=context.getBean(ExportJobQueries.class).payload(job);var files=context.getBean(ExportFiles.class);
        var artifact=payload.artifact();assertThat(artifact).isNotNull();
        assertThat(response.body()).containsExactly(files.verified(files.artifact(job.id(),artifact.token(),payload.format()),
            artifact.sha256(),artifact.bytes()));
        Files.write(attempt.resolve("restored-worker.xlsx"),response.body(),StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("restored-artifact.json"),context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
            .writeValueAsString(artifact),StandardOpenOption.CREATE_NEW);
    }

    private static void saveSnapshot(WebApplicationContext context,BackgroundJob job,Path target) throws Exception {
        var payload=context.getBean(ExportJobQueries.class).payload(job);
        Files.copy(context.getBean(ExportFiles.class).snapshot(payload.snapshotHash()),target);
    }
}
