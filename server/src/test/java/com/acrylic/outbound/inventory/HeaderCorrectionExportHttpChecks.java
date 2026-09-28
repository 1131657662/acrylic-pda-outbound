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

/** Header-only evidence comes from the immutable version and document, without invented dispatch rows. */
public final class HeaderCorrectionExportHttpChecks {
    private final WebApplicationContext context;
    private final ExportModels.Scope scope;
    private final Path output;
    private final String expectedNumber;
    private JsonNode facts;
    private CorrectionExportHttpClient wire;

    private HeaderCorrectionExportHttpChecks(WebApplicationContext context,ExportModels.Scope scope,Path attempt,String expectedNumber) {
        this.context=context;this.scope=scope;this.output=attempt.resolve("storage/header-correction");this.expectedNumber=expectedNumber;
    }
    public static void verify(WebApplicationContext context,ExportModels.Scope scope,Path attempt,String expectedNumber) throws Exception {
        var check=new HeaderCorrectionExportHttpChecks(context,scope,attempt,expectedNumber);
        check.captureFacts();check.wire=CorrectionExportHttpClient.login(context,scope);
        check.export("XLSX",2);check.export("PDF",2);check.export("XLSX",1);
    }
    private void captureFacts() throws Exception {
        var jdbc=context.getBean(JdbcTemplate.class);var order=Ids.bytes(scope.order());
        var versions=jdbc.queryForList("SELECT result_version version,actual_count count,weight_g weightG,"
                +"CAST(header_snapshot AS CHAR) headerJson,BIN_TO_UUID(source_correction_id) correctionId"
                +" FROM order_result_version WHERE order_id=? ORDER BY result_version",order);
        var document=jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,type,status,reason,approved_at,HEX(approved_by) approvedByHex"
                +" FROM correction_document WHERE order_id=?",order);
        Integer lineCount=jdbc.queryForObject("SELECT COUNT(*) FROM correction_line WHERE document_id=UUID_TO_BIN(?)",Integer.class,document.get("id"));
        var receipt=jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,canonical_command_bytes FROM correction_command_receipt"
                +" WHERE document_id=UUID_TO_BIN(?) AND command_type='CREATE'",document.get("id"));
        document.put("createReceiptId",receipt.get("id"));
        document.put("patch",SubmissionJson.parse((byte[])receipt.get("canonical_command_bytes")).path("headerPatch"));
        var effective=jdbc.queryForList("SELECT BIN_TO_UUID(dispatch_id) dispatchId,CAST(is_effective AS UNSIGNED) count,"
                +"effective_weight_g weightG FROM order_effective_scan WHERE order_id=? AND result_version=2 ORDER BY original_sequence",order);
        Integer effects=jdbc.queryForObject("SELECT COUNT(*) FROM correction_effect WHERE order_id=?",Integer.class,order);
        String json=context.getBean(JsonText.class).encode(Map.of("versions",versions,"document",document,"lineCount",lineCount,
                "effective",effective,"effects",effects));
        Files.writeString(output.resolve("database-facts.json"),json);facts=SubmissionJson.parse(json);
        assertThat(versions).hasSize(2);assertThat(lineCount).isZero();assertThat(effects).isZero();
        assertThat(document.get("type")).isEqualTo("HEADER_CORRECTION");assertThat(document.get("status")).isEqualTo("EFFECTIVE");
        var v2=facts.at("/versions/1");assertThat(v2.path("count").asInt()).isEqualTo(2);
        assertThat(v2.path("weightG").asLong()).isEqualTo(601500);
        assertThat(SubmissionJson.parse(v2.path("headerJson").asText()).path("orderNo").asText()).isEqualTo(expectedNumber);
    }
    private void export(String format,long version) throws Exception {
        var destination=version==2?output:output.resolve("default-v1");Files.createDirectories(destination);
        var body=SubmissionJson.object().put("format",format).put("basis",version==2?"CORRECTED":"ORIGINAL_CONFIRMED");
        if(version==2)body.put("resultVersion",version);
        Files.writeString(destination.resolve("request-"+format+".json"),body.toPrettyString());
        var response=wire.post("/orders/"+scope.order()+"/exports",body,UUID.randomUUID());
        Files.write(destination.resolve("create-"+format+".json"),response.body());
        Files.writeString(destination.resolve("http-status-"+format+".txt"),Integer.toString(response.statusCode()));
        UUID id=UUID.fromString(data(response,200).path("id").asText());
        var queries=context.getBean(ExportJobQueries.class);
        var job=awaitCompletion(id,format,destination);var payload=queries.payload(job);var files=context.getBean(ExportFiles.class);
        var snapshot=SubmissionJson.parse(Files.readString(files.snapshot(payload.snapshotHash())));
        Files.writeString(destination.resolve("snapshot-"+payload.format().extension+".json"),snapshot.toPrettyString());
        if(version==2)assertContent(snapshot);else assertDefault(snapshot);
        var download=wire.get("/exports/"+id+"/file");assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(Files.readAllBytes(files.artifact(id,payload.artifact().token(),payload.format())));
        Files.write(destination.resolve("header."+payload.format().extension),download.body());
    }
    private BackgroundJob awaitCompletion(UUID id,String format,Path destination) throws Exception {
        for(int index=0;index<30;index++) {
            var status=data(wire.get("/exports/"+id),200);
            Files.writeString(destination.resolve("status-"+format+".json"),status.toPrettyString());
            assertThat(status.path("status").asText()).as("header export status: %s",status).isNotIn("FAILED","EXPIRED","INVALIDATED");
            if(status.path("status").asText().equals("SUCCEEDED"))return context.getBean(BackgroundJobStore.class).find(id).orElseThrow();
            assertThat(context.getBean(BackgroundJobWorker.class).runOnce("d-header-boundary")).isPresent();
        }
        throw new AssertionError("header export did not finish");
    }
    private void assertContent(JsonNode snapshot) {
        var order=snapshot.at("/orders/0");
        assertThat(order.path("number").asText()).isEqualTo(SubmissionJson.parse(facts.at("/versions/0/headerJson").asText()).path("orderNo").asText());
        assertThat(order.path("resultVersion").asInt()).isEqualTo(2);assertThat(order.path("effectiveLines")).hasSize(2);
        assertEffective(order.path("effectiveLines"));
        assertThat(order.path("adjustments")).hasSize(1);var adjustment=order.path("adjustments").get(0);
        assertThat(adjustment.path("kind").asText()).isEqualTo("HEADER_CORRECTION");
        assertThat(adjustment.path("dispatchId").asText()).isEmpty();
        assertThat(adjustment.path("lineId").asText()).isEmpty();
        assertThat(adjustment.path("headerField").asText()).isEqualTo("orderNo");
        assertThat(adjustment.path("beforeText").asText()).isEqualTo(SubmissionJson.parse(facts.at("/versions/0/headerJson").asText()).path("orderNo").asText());
        assertThat(adjustment.path("afterText").asText()).isEqualTo(expectedNumber);
        assertThat(adjustment.path("afterText")).isEqualTo(facts.at("/document/patch/orderNo"));
        assertThat(adjustment.path("reason").asText()).isEqualTo(facts.at("/document/reason").asText());
        assertThat(Instant.parse(adjustment.path("occurredAt").asText()))
                .isEqualTo(Instant.parse(facts.at("/document/approved_at").asText()+"Z"));
        assertThat(adjustment.path("beforeCount").asInt()).isZero();assertThat(adjustment.path("afterCount").asInt()).isZero();
        assertThat(adjustment.path("beforeWeightGrams").asLong()).isZero();assertThat(adjustment.path("afterWeightGrams").asLong()).isZero();
        assertThat(adjustment.path("evidenceReference").asText()).contains(facts.at("/document/createReceiptId").asText(),"headerPatch:/orderNo");
        assertThat(adjustment.path("correctionId").asText()).isEqualTo(facts.at("/document/id").asText());
        assertThat(Ids.bytes(UUID.fromString(adjustment.path("approver").asText())))
                .containsExactly(HexFormat.of().parseHex(facts.at("/document/approvedByHex").asText()));
    }
    private void assertDefault(JsonNode snapshot) {
        assertThat(snapshot.at("/metadata/basis").asText()).isEqualTo("ORIGINAL_CONFIRMED");
        assertThat(snapshot.at("/orders/0/resultVersion").asInt()).isEqualTo(1);
        assertThat(snapshot.at("/orders/0/adjustments")).isEmpty();
        assertThat(snapshot.at("/orders/0/number").asText()).isEqualTo(SubmissionJson.parse(facts.at("/versions/0/headerJson").asText()).path("orderNo").asText());
    }
    private void assertEffective(JsonNode lines) {
        for(int i=0;i<2;i++) {
            var actual=lines.get(i);var expected=facts.path("effective").get(i);
            assertThat(actual.path("dispatchId").asText()).isEqualTo(expected.path("dispatchId").asText());
            assertThat(actual.path("count").asInt()).isEqualTo(expected.path("count").asInt()).isEqualTo(1);
            assertThat(actual.path("weightGrams").asLong()).isEqualTo(expected.path("weightG").asLong());
        }
    }
}
