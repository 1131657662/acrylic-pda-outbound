package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** Boundary 1: one cancellation plus one unchanged package, through the existing HTTP export route. */
public final class PartialCorrectionExportHttpChecks {
    private final WebApplicationContext context;
    private final ExportModels.Scope scope;
    private final Path output;
    private JsonNode facts,original;
    private CorrectionExportHttpClient wire;

    private PartialCorrectionExportHttpChecks(WebApplicationContext context,ExportModels.Scope scope,Path attempt) {
        this.context=context;this.scope=scope;this.output=attempt.resolve("storage/partial-correction");
    }

    public static void verify(WebApplicationContext context,ExportModels.Scope scope,Path attempt) throws Exception {
        var check=new PartialCorrectionExportHttpChecks(context,scope,attempt);
        Files.createDirectories(check.output);check.captureFacts();
        check.wire=CorrectionExportHttpClient.login(context,scope);
        check.export("XLSX");check.export("PDF");
        Files.writeString(attempt.resolve("partial-http-completed.marker"),"partial v2 HTTP XLSX/PDF completed\n");
    }

    private void captureFacts() throws Exception {
        var jdbc=context.getBean(JdbcTemplate.class);var order=Ids.bytes(scope.order());
        var dispatches=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,weight_g weightG,order_scan_seq sequence"
                +" FROM dispatch_record WHERE order_id=? ORDER BY order_scan_seq",order);
        var totals=jdbc.queryForMap("SELECT actual_count count,weight_g weightG FROM order_result_version WHERE order_id=? AND result_version=2",order);
        var document=jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,reason,HEX(approved_by) approvedByHex FROM correction_document WHERE order_id=?",order);
        var line=jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(dispatch_id) dispatchId,before_count beforeCount,"
                +"target_count afterCount,before_weight_g beforeWeightG,target_weight_g afterWeightG,business_occurred_at occurredAt"
                +" FROM correction_line WHERE document_id=UUID_TO_BIN(?)",document.get("id"));
        var effective=jdbc.queryForList("SELECT BIN_TO_UUID(dispatch_id) dispatchId,CAST(is_effective AS UNSIGNED) count,"
                +"effective_weight_g weightG FROM order_effective_scan WHERE order_id=? AND result_version=2 ORDER BY original_sequence",order);
        String json=context.getBean(JsonText.class).encode(Map.of("dispatches",dispatches,"result",totals,
                "document",document,"line",line,"effective",effective));
        Files.writeString(output.resolve("database-facts.json"),json);facts=SubmissionJson.parse(json);
        assertThat(dispatches).hasSize(2);assertThat(((Number)totals.get("count")).intValue()).isEqualTo(1);
        assertThat(totals.get("weightG")).isEqualTo(dispatches.get(1).get("weightG"));
        assertThat(line.get("dispatchId")).isEqualTo(dispatches.getFirst().get("id"));
        assertThat(((Number)line.get("afterCount")).intValue()).isZero();
        assertThat(((Number)line.get("afterWeightG")).longValue()).isZero();
        var v1=context.getBean(ExportSnapshotReader.class).read(scope,ExportModels.Format.XLSX,Instant.now());
        original=SubmissionJson.parse(context.getBean(JsonText.class).encode(v1));
        Files.writeString(output.resolve("original-v1.json"),original.toPrettyString());
    }

    private void export(String format) throws Exception {
        var body=SubmissionJson.object().put("format",format).put("basis","CORRECTED").put("resultVersion",2);
        var response=wire.post("/orders/"+scope.order()+"/exports",body,UUID.randomUUID());
        Files.write(output.resolve("create-"+format+".json"),response.body());
        var created=data(response,200);UUID id=UUID.fromString(created.path("id").asText());
        var store=context.getBean(BackgroundJobStore.class);var queries=context.getBean(ExportJobQueries.class);
        BackgroundJob job=null;
        for(int index=0;index<30;index++) {
            var status=data(wire.get("/exports/"+id),200);
            Files.writeString(output.resolve("status-"+format+".json"),status.toPrettyString());
            assertThat(status.path("status").asText()).as("partial export status: %s",status).isNotIn("FAILED","EXPIRED","INVALIDATED");
            if(status.path("status").asText().equals("SUCCEEDED")){job=store.find(id).orElseThrow();break;}
            assertThat(context.getBean(BackgroundJobWorker.class).runOnce("d-partial-boundary")).isPresent();
        }
        assertThat(job).as("partial export completed").isNotNull();
        var payload=queries.payload(job);var files=context.getBean(ExportFiles.class);
        var snapshot=SubmissionJson.parse(Files.readString(files.snapshot(payload.snapshotHash())));
        Files.writeString(output.resolve("snapshot-"+payload.format().extension+".json"),snapshot.toPrettyString());
        assertSnapshot(snapshot);assertAdjustment(snapshot.at("/orders/0/adjustments/0"));
        var download=wire.get("/exports/"+id+"/file");assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(Files.readAllBytes(files.artifact(id,payload.artifact().token(),payload.format())));
        Files.write(output.resolve("partial."+payload.format().extension),download.body());
    }

    private void assertSnapshot(JsonNode snapshot) {
        assertThat(snapshot.at("/metadata/basis").asText()).isEqualTo("CORRECTED");
        var order=snapshot.at("/orders/0");assertThat(order.path("resultVersion").asInt()).isEqualTo(2);
        assertThat(order.path("originalLines")).isEqualTo(original.at("/orders/0/originalLines"));
        assertThat(order.path("effectiveLines")).hasSize(2);
        for(int index=0;index<2;index++) {
            var actual=order.path("effectiveLines").get(index);var expected=facts.path("effective").get(index);
            assertThat(actual.path("dispatchId").asText()).isEqualTo(expected.path("dispatchId").asText());
            assertThat(actual.path("count").asInt()).isEqualTo(expected.path("count").asInt());
            assertThat(actual.path("weightGrams").asLong()).isEqualTo(expected.path("weightG").asLong());
        }
        assertThat(order.path("effectiveLines").get(1).path("count").asInt()).isEqualTo(1);
        assertThat(order.path("adjustments")).hasSize(1);
    }

    private void assertAdjustment(JsonNode adjustment) {
        var doc=facts.path("document");var line=facts.path("line");
        assertThat(adjustment.path("correctionId").asText()).isEqualTo(doc.path("id").asText());
        assertThat(adjustment.path("lineId").asText()).isEqualTo(line.path("id").asText());
        assertThat(adjustment.path("dispatchId").asText()).isEqualTo(line.path("dispatchId").asText());
        assertThat(adjustment.path("beforeCount").asInt()).isEqualTo(1);
        assertThat(adjustment.path("afterCount").asInt()).isZero();
        assertThat(adjustment.path("beforeWeightGrams").asLong()).isEqualTo(line.path("beforeWeightG").asLong());
        assertThat(adjustment.path("afterWeightGrams").asLong()).isZero();
        assertThat(adjustment.path("reason").asText()).isEqualTo(doc.path("reason").asText());
        assertThat(Ids.bytes(UUID.fromString(adjustment.path("approver").asText())))
                .containsExactly(HexFormat.of().parseHex(doc.path("approvedByHex").asText()));
        assertThat(adjustment.path("kind").asText()).isEqualTo("DISPATCH_CANCELLATION");
    }
}
