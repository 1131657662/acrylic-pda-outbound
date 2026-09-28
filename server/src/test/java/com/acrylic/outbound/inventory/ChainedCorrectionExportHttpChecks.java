package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.reports.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** Two actual approvals, complete chain and exact DB facts; no production behavior replacement. */
public final class ChainedCorrectionExportHttpChecks {
    private final WebApplicationContext context;
    private final ExportModels.Scope scope;
    private final Path output;
    private JsonNode facts;
    private CorrectionExportHttpClient wire;

    private ChainedCorrectionExportHttpChecks(WebApplicationContext context,ExportModels.Scope scope,Path attempt) {
        this.context=context;this.scope=scope;this.output=attempt.resolve("storage/chained-correction");
    }

    public static void verify(WebApplicationContext context,ExportModels.Scope scope,Path attempt) throws Exception {
        var check=new ChainedCorrectionExportHttpChecks(context,scope,attempt);
        Files.createDirectories(check.output);check.captureFacts();
        check.wire=CorrectionExportHttpClient.login(context,scope);
        check.export("XLSX");check.export("PDF");
        Files.writeString(attempt.resolve("chained-http-completed.marker"),"complete v2/v3 HTTP XLSX/PDF chain\n");
    }

    private void captureFacts() throws Exception {
        var jdbc=context.getBean(JdbcTemplate.class);var order=Ids.bytes(scope.order());
        var result=jdbc.queryForMap("SELECT actual_count count,weight_g weightG FROM order_result_version WHERE order_id=? AND result_version=3",order);
        var effective=jdbc.queryForList("SELECT BIN_TO_UUID(dispatch_id) dispatchId,CAST(is_effective AS UNSIGNED) count,"
                +"effective_weight_g weightG FROM order_effective_scan WHERE order_id=? AND result_version=3 ORDER BY original_sequence",order);
        var documents=jdbc.queryForList("SELECT BIN_TO_UUID(id) id,reason,HEX(approved_by) approvedByHex,"
                +"effective_result_version version FROM correction_document WHERE order_id=? ORDER BY effective_result_version",order);
        var adjustments=new ArrayList<Map<String,Object>>();
        for(var document:documents) {
            var line=jdbc.queryForMap("SELECT BIN_TO_UUID(id) lineId,BIN_TO_UUID(dispatch_id) dispatchId,before_count beforeCount,"
                    +"target_count afterCount,before_weight_g beforeWeightG,target_weight_g afterWeightG,business_occurred_at occurredAt"
                    +" FROM correction_line WHERE document_id=UUID_TO_BIN(?)",document.get("id"));
            var fact=new LinkedHashMap<>(document);fact.putAll(line);adjustments.add(fact);
        }
        String json=context.getBean(JsonText.class).encode(Map.of("result",result,"effective",effective,"adjustments",adjustments));
        Files.writeString(output.resolve("database-facts.json"),json);facts=SubmissionJson.parse(json);
        assertThat(((Number)result.get("count")).intValue()).isEqualTo(2);
        assertThat(((Number)result.get("weightG")).longValue()).isEqualTo(600500);
        assertThat(adjustments).hasSize(2);
        assertThat(facts.at("/adjustments/0/beforeWeightG").asLong()).isEqualTo(300500);
        assertThat(facts.at("/adjustments/0/afterWeightG").asLong()).isEqualTo(300000);
        assertThat(facts.at("/adjustments/1/beforeWeightG").asLong()).isEqualTo(300000);
        assertThat(facts.at("/adjustments/1/afterWeightG").asLong()).isEqualTo(299500);
    }

    private void export(String format) throws Exception {
        var body=SubmissionJson.object().put("format",format).put("basis","CORRECTED").put("resultVersion",3);
        var response=wire.post("/orders/"+scope.order()+"/exports",body,UUID.randomUUID());
        Files.write(output.resolve("create-"+format+".json"),response.body());
        UUID id=UUID.fromString(data(response,200).path("id").asText());
        var store=context.getBean(BackgroundJobStore.class);var queries=context.getBean(ExportJobQueries.class);
        BackgroundJob job=null;
        for(int index=0;index<30;index++) {
            var status=data(wire.get("/exports/"+id),200);
            Files.writeString(output.resolve("status-"+format+".json"),status.toPrettyString());
            assertThat(status.path("status").asText()).as("v3 export status: %s",status).isNotIn("FAILED","EXPIRED","INVALIDATED");
            if(status.path("status").asText().equals("SUCCEEDED")){job=store.find(id).orElseThrow();break;}
            assertThat(context.getBean(BackgroundJobWorker.class).runOnce("d-chain-boundary")).isPresent();
        }
        assertThat(job).isNotNull();var payload=queries.payload(job);var files=context.getBean(ExportFiles.class);
        var snapshot=SubmissionJson.parse(Files.readString(files.snapshot(payload.snapshotHash())));
        Files.writeString(output.resolve("snapshot-"+payload.format().extension+".json"),snapshot.toPrettyString());
        assertSnapshot(snapshot);assertChainValidation(snapshot);
        var download=wire.get("/exports/"+id+"/file");assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(Files.readAllBytes(files.artifact(id,payload.artifact().token(),payload.format())));
        Files.write(output.resolve("chained."+payload.format().extension),download.body());
    }

    private void assertSnapshot(JsonNode snapshot) {
        assertThat(snapshot.at("/metadata/basis").asText()).isEqualTo("CORRECTED");
        var order=snapshot.at("/orders/0");assertThat(order.path("resultVersion").asInt()).isEqualTo(3);
        assertThat(order.path("effectiveLines")).hasSize(2);assertThat(order.path("adjustments")).hasSize(2);
        for(int index=0;index<2;index++) {
            var actual=order.path("effectiveLines").get(index);var expected=facts.path("effective").get(index);
            assertThat(actual.path("dispatchId").asText()).isEqualTo(expected.path("dispatchId").asText());
            assertThat(actual.path("count").asInt()).isEqualTo(expected.path("count").asInt());
            assertThat(actual.path("weightGrams").asLong()).isEqualTo(expected.path("weightG").asLong());
            assertAdjustment(order.path("adjustments").get(index),facts.path("adjustments").get(index));
        }
    }

    private void assertAdjustment(JsonNode actual,JsonNode expected) {
        assertThat(actual.path("correctionId").asText()).isEqualTo(expected.path("id").asText());
        assertThat(actual.path("resultVersion").asInt()).isEqualTo(expected.path("version").asInt());
        for(String field:List.of("lineId","dispatchId","reason"))
            assertThat(actual.path(field).asText()).isEqualTo(expected.path(field).asText());
        for(String prefix:List.of("before","after")) {
            assertThat(actual.path(prefix+"Count").asInt()).isEqualTo(expected.path(prefix+"Count").asInt());
            assertThat(actual.path(prefix+"WeightGrams").asLong()).isEqualTo(expected.path(prefix+"WeightG").asLong());
        }
        assertThat(actual.path("kind").asText()).isEqualTo("WEIGHT_CORRECTION");
        assertThat(Ids.bytes(UUID.fromString(actual.path("approver").asText())))
                .containsExactly(HexFormat.of().parseHex(expected.path("approvedByHex").asText()));
    }

    private void assertChainValidation(JsonNode snapshot) throws Exception {
        var mapper=context.getBean(ObjectMapper.class);
        SnapshotValidator.validate(mapper.treeToValue(snapshot,ReportSnapshot.class));
        var missing=snapshot.deepCopy();((ArrayNode)missing.at("/orders/0/adjustments")).remove(0);
        assertThatThrownBy(()->SnapshotValidator.validate(mapper.treeToValue(missing,ReportSnapshot.class)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("调整版本链不完整或乱序");
        Files.writeString(output.resolve("chain-validator-checks.txt"),"complete v2/v3: accepted; missing v2: rejected for incomplete chain\n");
    }
}
