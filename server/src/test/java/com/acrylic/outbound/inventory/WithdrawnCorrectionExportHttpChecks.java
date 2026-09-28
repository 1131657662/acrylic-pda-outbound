package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** Captures successful rollback facts before requiring a real HTTP export of that resulting version. */
public final class WithdrawnCorrectionExportHttpChecks {
    private final WebApplicationContext context;
    private final ExportModels.Scope scope;
    private final Path output;
    private JsonNode facts;
    private CorrectionExportHttpClient wire;

    private WithdrawnCorrectionExportHttpChecks(WebApplicationContext context,ExportModels.Scope scope,Path attempt) {
        this.context=context;this.scope=scope;this.output=attempt.resolve("storage/withdrawn-correction");
    }

    public static void verify(WebApplicationContext context,ExportModels.Scope scope,Path attempt) throws Exception {
        var check=new WithdrawnCorrectionExportHttpChecks(context,scope,attempt);
        Files.createDirectories(check.output);check.captureFacts();
        check.wire=CorrectionExportHttpClient.login(context,scope);
        check.export("XLSX");check.export("PDF");
        Files.writeString(attempt.resolve("withdrawn-http-completed.marker"),"withdrawn v3 HTTP XLSX/PDF completed\n");
    }

    private void captureFacts() throws Exception {
        var jdbc=context.getBean(JdbcTemplate.class);var order=Ids.bytes(scope.order());
        var versions=jdbc.queryForList("SELECT result_version version,actual_count count,weight_g weightG,"
                +"BIN_TO_UUID(source_correction_id) correctionId FROM order_result_version WHERE order_id=? ORDER BY result_version",order);
        var documents=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,status,effective_result_version appliedVersion,"
                +"HEX(approved_by) approvedByHex,HEX(withdrawal_confirmed_by) withdrawalConfirmedByHex,withdrawal_confirmed_at"
                +" FROM correction_document WHERE order_id=?",order);
        String lineId=jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM correction_line WHERE document_id=UUID_TO_BIN(?)",
                String.class,documents.getFirst().get("id"));
        var ledger=jdbc.queryForMap("SELECT COUNT(*) rowsCount,SUM(quantity_delta) countDelta,SUM(weight_delta_g) weightDeltaG"
                +" FROM correction_ledger WHERE correction_line_id=UUID_TO_BIN(?)",lineId);
        var effective=jdbc.queryForList("SELECT BIN_TO_UUID(dispatch_id) dispatchId,CAST(is_effective AS UNSIGNED) count,"
                +"effective_weight_g weightG FROM order_effective_scan WHERE order_id=? AND result_version=3 ORDER BY original_sequence",order);
        var values=new LinkedHashMap<String,Object>();values.put("versions",versions);values.put("documents",documents);
        values.put("ledger",ledger);values.put("effective",effective);
        values.put("correctionDocumentForV3",context.getBean(ExportFacts.class).correctionDocumentFor(scope.order(),3));
        String json=context.getBean(JsonText.class).encode(values);Files.writeString(output.resolve("database-facts.json"),json);
        facts=SubmissionJson.parse(json);assertThat(versions).hasSize(3);assertThat(documents).hasSize(1);
        assertThat(facts.at("/documents/0/status").asText()).isEqualTo("WITHDRAWN");
        assertThat(facts.at("/versions/2/count").asInt()).isEqualTo(2);
        assertThat(facts.at("/versions/2/weightG").asLong()).isEqualTo(601500);
        assertThat(facts.at("/ledger/rowsCount").asInt()).isEqualTo(2);
        assertThat(facts.at("/ledger/countDelta").asInt()).isZero();
        assertThat(facts.at("/ledger/weightDeltaG").asLong()).isZero();
    }

    private void export(String format) throws Exception {
        var body=SubmissionJson.object().put("format",format).put("basis","CORRECTED").put("resultVersion",3);
        Files.writeString(output.resolve("request-"+format+".json"),body.toPrettyString());
        var response=wire.post("/orders/"+scope.order()+"/exports",body,UUID.randomUUID());
        Files.write(output.resolve("create-"+format+".json"),response.body());
        Files.writeString(output.resolve("http-status-"+format+".txt"),Integer.toString(response.statusCode()));
        UUID id=UUID.fromString(data(response,200).path("id").asText());
        var store=context.getBean(BackgroundJobStore.class);var queries=context.getBean(ExportJobQueries.class);
        BackgroundJob job=null;
        for(int index=0;index<30;index++) {
            var status=data(wire.get("/exports/"+id),200);
            Files.writeString(output.resolve("status-"+format+".json"),status.toPrettyString());
            assertThat(status.path("status").asText()).as("withdrawn export status: %s",status).isNotIn("FAILED","EXPIRED","INVALIDATED");
            if(status.path("status").asText().equals("SUCCEEDED")){job=store.find(id).orElseThrow();break;}
            assertThat(context.getBean(BackgroundJobWorker.class).runOnce("d-withdraw-boundary")).isPresent();
        }
        assertThat(job).isNotNull();var payload=queries.payload(job);var files=context.getBean(ExportFiles.class);
        var snapshot=SubmissionJson.parse(Files.readString(files.snapshot(payload.snapshotHash())));
        Files.writeString(output.resolve("snapshot-"+payload.format().extension+".json"),snapshot.toPrettyString());
        assertSnapshot(snapshot);
        var download=wire.get("/exports/"+id+"/file");assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(Files.readAllBytes(files.artifact(id,payload.artifact().token(),payload.format())));
        Files.write(output.resolve("withdrawn."+payload.format().extension),download.body());
    }

    private void assertSnapshot(JsonNode snapshot) {
        assertThat(snapshot.at("/metadata/basis").asText()).isEqualTo("CORRECTED");
        var order=snapshot.at("/orders/0");assertThat(order.path("resultVersion").asInt()).isEqualTo(3);
        assertThat(order.path("effectiveLines")).hasSize(2);
        for(int index=0;index<2;index++) {
            var actual=order.path("effectiveLines").get(index);var expected=facts.path("effective").get(index);
            assertThat(actual.path("dispatchId").asText()).isEqualTo(expected.path("dispatchId").asText());
            assertThat(actual.path("count").asInt()).isEqualTo(expected.path("count").asInt()).isEqualTo(1);
            assertThat(actual.path("weightGrams").asLong()).isEqualTo(expected.path("weightG").asLong());
        }
        assertThat(order.path("originalHeader")).isEqualTo(order.path("effectiveHeader"));
    }
}
