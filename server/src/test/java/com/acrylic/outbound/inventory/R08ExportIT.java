package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.reports.SnapshotJson;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class R08ExportIT {
    static {
        String url=System.getenv("OUTBOUND_DB_URL");
        boolean combined=url!=null && url.startsWith("jdbc:mysql://127.0.0.1:13316/acrylic_outbound_wave1_20260920?")
                && "outbound_wave1_20260920".equals(System.getenv("OUTBOUND_DB_USER"));
        if(!combined && (url==null || !url.startsWith("jdbc:mysql://127.0.0.1:23308/acrylic_r08_")))
            throw new IllegalStateException("R08 tests require the dedicated R08 or first-wave synthetic database");
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired BackgroundJobWorker worker;@Autowired BackgroundJobStore jobs;@Autowired ExportFiles files;@Autowired ExportJobQueries queries;
    private PublicationDriver api;
    @BeforeEach void identity() { api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")); }
    private PcFixture completed() throws Exception {
        var fixture=new PcFixture(api,false,1,1);fixture.start();for(int i=0;i<3;i++) fixture.add(i);fixture.submit("");return fixture;
    }
    private JsonNode create(String order,String format,String key) throws Exception {
        return api.data(post("/api/v1/orders/"+order+"/exports").header("Idempotency-Key",key)
                .contentType("application/json").content("{\"format\":\""+format+"\",\"basis\":\"ORIGINAL_CONFIRMED\"}"));
    }
    private BackgroundJob generated(JsonNode response) {
        UUID id=UUID.fromString(response.get("id").asText());
        for(int attempt=0;attempt<30;attempt++) {
            var current=jobs.find(id).orElseThrow();
            if(current.status()==BackgroundJob.Status.SUCCEEDED) return current;
            var outcome=worker.runOnce("r08-test");assertThat(outcome).isPresent();
            if(outcome.get().jobId().equals(id)) assertThat(outcome.get().outcome()).isEqualTo("SUCCEEDED");
        }
        throw new AssertionError("Export did not complete");
    }
    @Test void pcExcelFreezesOriginalFactsAndDownloadsVerifiedBytes() throws Exception {
        var fixture=completed();var response=create(fixture.order,"XLSX",UUID.randomUUID().toString());
        var pending=jobs.find(UUID.fromString(response.get("id").asText())).orElseThrow();var payload=queries.payload(pending);
        var snapshot=SnapshotJson.read(files.snapshot(payload.snapshotHash()));var order=snapshot.orders().getFirst();
        assertThat(snapshot.metadata().provenance().name()).isEqualTo("TEST_SAMPLE");
        assertThat(order.originalLines()).extracting(l->l.barcode()).containsExactlyElementsOf(fixture.barcodes);
        assertThat(order.originalLines()).extracting(l->l.weightGrams()).containsOnly("300501");
        jdbc.update("UPDATE order_revision SET vehicle_no='Changed after acceptance' WHERE order_id=?",Ids.bytes(UUID.fromString(fixture.order)));
        var job=generated(response);var download=api.send(get("/api/v1/exports/"+job.id()+"/file")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        assertThat(download.getContentAsByteArray()).startsWith((byte)'P',(byte)'K');
        assertThat(SnapshotJson.read(files.snapshot(payload.snapshotHash())).orders().getFirst().originalHeader().vehicle()).isEqualTo("苏A-测试车");
        Files.write(Path.of(System.getenv("OUTBOUND_STORAGE"),"verified-pc.xlsx"),download.getContentAsByteArray());
    }
    @Test void pdaPdfPreservesInputExceptionsAndOriginalDay() throws Exception {
        var fixture=new SubmissionFixture(api,jdbc,false,false);fixture.addAcknowledgedFailures(2);fixture.submit();
        var response=create(fixture.order,"PDF",UUID.randomUUID().toString());var job=generated(response);var payload=queries.payload(job);
        var snapshot=SnapshotJson.read(files.snapshot(payload.snapshotHash()));assertThat(snapshot.orders().getFirst().exceptions()).hasSize(2);
        assertThat(payload.originalDay()).isEqualTo(snapshot.metadata().fromDate().toString());
        byte[] bytes=api.send(get("/api/v1/exports/"+job.id()+"/file")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        Files.write(Path.of(System.getenv("OUTBOUND_STORAGE"),"verified-pda.pdf"),bytes);
    }
    @Test void idempotencyReplaysAfterPublicationAndRejectsChangedFormat() throws Exception {
        var fixture=completed();String key=UUID.randomUUID().toString();var first=create(fixture.order,"XLSX",key);generated(first);
        assertThat(create(fixture.order,"XLSX",key).get("id")).isEqualTo(first.get("id"));
        api.send(post("/api/v1/orders/"+fixture.order+"/exports").header("Idempotency-Key",key).contentType("application/json")
                .content("{\"format\":\"PDF\",\"basis\":\"ORIGINAL_CONFIRMED\"}")).andExpect(status().isConflict());
    }
    @Test void concurrentIdenticalRequestsCreateOneTask() throws Exception {
        var fixture=completed();String key=UUID.randomUUID().toString();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->create(fixture.order,"XLSX",key));var second=pool.submit(()->create(fixture.order,"XLSX",key));
            var result=first.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(second.get(15,java.util.concurrent.TimeUnit.SECONDS).get("id")).isEqualTo(result.get("id"));
            generated(result);
        }
        assertThat(api.data(get("/api/v1/orders/"+fixture.order+"/exports")).get("items")).hasSize(1);
    }
    @Test void revokedPermissionAndWrongWarehouseCannotRequestOrDownload() throws Exception {
        var fixture=completed();var job=generated(create(fixture.order,"XLSX",UUID.randomUUID().toString()));
        var other=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(api.identity().user().id()),Ids.bytes(other.warehouse()));
        var wrong=new PublicationDriver(mvc,json,new IdentityFixture(api.identity().user(),other.warehouse()));
        wrong.send(get("/api/v1/exports/"+job.id()+"/file")).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(api.identity().user().id()));
        api.send(get("/api/v1/exports/"+job.id()+"/file")).andExpect(status().isForbidden());
    }
    @Test void rejectsCurrentNetAndUncompletedOrders() throws Exception {
        var fixture=new PcFixture(api,false,1,1);
        api.send(post("/api/v1/orders/"+fixture.order+"/exports").header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content("{\"format\":\"XLSX\",\"basis\":\"CURRENT_EFFECTIVE\"}")).andExpect(status().isBadRequest());
        api.send(post("/api/v1/orders/"+fixture.order+"/exports").header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content("{\"format\":\"XLSX\",\"basis\":\"ORIGINAL_CONFIRMED\"}")).andExpect(status().isConflict());
    }
    @Test void creatorAndCurrentWarehouseAuthorizationAreRequired() throws Exception {
        var fixture=completed();var job=generated(create(fixture.order,"XLSX",UUID.randomUUID().toString()));
        var other=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(other.user().id()),Ids.bytes(api.identity().warehouse()));
        var stranger=new PublicationDriver(mvc,json,new IdentityFixture(other.user(),api.identity().warehouse()));
        stranger.send(get("/api/v1/exports/"+job.id())).andExpect(status().isNotFound());
        stranger.send(get("/api/v1/exports/"+job.id()+"/file")).andExpect(status().isNotFound());
        assertThat(stranger.data(get("/api/v1/orders/"+fixture.order+"/exports")).get("items")).isEmpty();
        jdbc.update("DELETE FROM user_warehouse WHERE user_id=?",Ids.bytes(api.identity().user().id()));
        api.send(get("/api/v1/exports/"+job.id()+"/file")).andExpect(status().isNotFound());
    }
    @Test void tamperedArtifactCannotBeDownloaded() throws Exception {
        var fixture=completed();var job=generated(create(fixture.order,"XLSX",UUID.randomUUID().toString()));var p=queries.payload(job);
        Files.writeString(files.artifact(job.id(),p.artifact().token(),p.format()),"tampered");
        api.send(get("/api/v1/exports/"+job.id()+"/file")).andExpect(status().isGone());
    }
    @Test void tamperedSnapshotFailsWithoutPublishingArtifact() throws Exception {
        var fixture=completed();var result=create(fixture.order,"XLSX",UUID.randomUUID().toString());UUID id=UUID.fromString(result.get("id").asText());
        var p=queries.payload(jobs.find(id).orElseThrow());Files.writeString(files.snapshot(p.snapshotHash()),"tampered");
        for(int i=0;i<30 && jobs.find(id).orElseThrow().status()!=BackgroundJob.Status.FAILED;i++) worker.runOnce("r08-tamper");
        var failed=jobs.find(id).orElseThrow();assertThat(failed.lastError()).isEqualTo("EXPORT_SNAPSHOT_UNAVAILABLE");
        assertThat(queries.payload(failed).artifact()).isNull();
    }
    @Test void shortPlanIncludesZeroProductTotalsAndVoidedEvidence() throws Exception {
        var fixture=new PcFixture(api,false,2,1);fixture.start();String entry=fixture.add(0).at("/result/entryId").asText();
        fixture.send("void",fixture.write("").put("entryId",entry).put("reason","合成撤销证据"));fixture.add(0);
        fixture.submit("合成短装");fixture.send("review",fixture.reviewBody("APPROVE"));
        var result=create(fixture.order,"XLSX",UUID.randomUUID().toString());var job=jobs.find(UUID.fromString(result.get("id").asText())).orElseThrow();
        var order=SnapshotJson.read(files.snapshot(queries.payload(job).snapshotHash())).orders().getFirst();
        assertThat(order.products()).hasSize(3);assertThat(order.originalLines()).hasSize(1);assertThat(order.exceptions()).hasSize(1);
        assertThat(order.originalLines().getFirst().orderScanSeq()).isEqualTo(2);generated(result);
    }
    @Test void pdaScanRecordsFreezeOperatorNameAndOriginalCaptureTime() throws Exception {
        var fixture=new SubmissionFixture(api,jdbc,false,false);fixture.submit();
        byte[] operator=Ids.bytes(UUID.fromString(fixture.tokens.get("userId").asText()));
        jdbc.update("UPDATE app_user SET display_name=? WHERE id=?","刘  颖",operator);
        String key=UUID.randomUUID().toString();var result=create(fixture.order,"SCAN_XLSX",key);
        var payload=queries.payload(jobs.find(UUID.fromString(result.get("id").asText())).orElseThrow());
        var snapshot=SnapshotJson.read(files.snapshot(payload.snapshotHash()));
        assertThat(payload.template()).isEqualTo("r08-scan-records-1");
        assertThat(snapshot.orders().getFirst().originalLines()).allSatisfy(line->{
            assertThat(line.operatorName()).isEqualTo("刘  颖");assertThat(line.receivedAt()).isEqualTo("1970-01-01T00:00:01Z");
            assertThat(line.remark()).contains("导出申请时人员资料",fixture.tokens.get("userId").asText());
        });
        jdbc.update("UPDATE app_user SET display_name=? WHERE id=?","改名后人员",operator);
        assertThat(create(fixture.order,"SCAN_XLSX",key).get("id")).isEqualTo(result.get("id"));
        var job=generated(result);
        var response=api.send(get("/api/v1/exports/"+job.id()+"/file")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Content-Disposition")).contains("scan-records-v1-", ".xlsx");
        Files.write(Path.of(System.getenv("OUTBOUND_STORAGE"),"verified-pda-scan.xlsx"),response.getContentAsByteArray());
        assertThat(SnapshotJson.read(files.snapshot(payload.snapshotHash())).orders().getFirst().originalLines())
                .extracting(l->l.operatorName()).containsOnly("刘  颖");
    }
    @Test void pcScanRecordsUseFrozenNamesAndDownloadAsXlsx() throws Exception {
        var fixture=completed();jdbc.update("UPDATE app_user SET display_name='New name' WHERE id=?",Ids.bytes(api.identity().user().id()));
        var job=generated(create(fixture.order,"SCAN_XLSX",UUID.randomUUID().toString()));var payload=queries.payload(job);
        assertThat(SnapshotJson.read(files.snapshot(payload.snapshotHash())).orders().getFirst().originalLines())
                .extracting(l->l.operatorName()).containsOnly("测试人员");
        byte[] bytes=api.send(get("/api/v1/exports/"+job.id()+"/file")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith((byte)'P',(byte)'K');
        Files.write(Path.of(System.getenv("OUTBOUND_STORAGE"),"verified-pc-scan.xlsx"),bytes);
    }
    @Test void placeholderOperatorNameRejectsScanExportBeforeTaskCreation() throws Exception {
        var fixture=new SubmissionFixture(api,jdbc,false,false);fixture.submit();
        byte[] operator=Ids.bytes(UUID.fromString(fixture.tokens.get("userId").asText()));
        String account=jdbc.queryForObject("SELECT username FROM app_user WHERE id=?",String.class,operator);
        for(String name:List.of("","   ","原人员ID:"+fixture.tokens.get("userId").asText(),fixture.tokens.get("userId").asText(),account))
            rejectScanExport(fixture,name,operator);
        assertThat(api.data(get("/api/v1/orders/"+fixture.order+"/exports")).get("items")).isEmpty();
    }
    private void rejectScanExport(SubmissionFixture fixture,String name,byte[] operator) throws Exception {
        jdbc.update("UPDATE app_user SET display_name=? WHERE id=?",name,operator);
        api.send(post("/api/v1/orders/"+fixture.order+"/exports").header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content("{\"format\":\"SCAN_XLSX\",\"basis\":\"ORIGINAL_CONFIRMED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXPORT_OPERATOR_NAME_UNAVAILABLE"));
    }
    @Test void asciiOperatorNameIsAcceptedForScanExport() throws Exception {
        var fixture=new SubmissionFixture(api,jdbc,false,false);fixture.submit();
        jdbc.update("UPDATE app_user SET display_name='test_pda01' WHERE id=?",
                Ids.bytes(UUID.fromString(fixture.tokens.get("userId").asText())));
        var result=create(fixture.order,"SCAN_XLSX",UUID.randomUUID().toString());
        var payload=queries.payload(jobs.find(UUID.fromString(result.get("id").asText())).orElseThrow());
        assertThat(payload.template()).isEqualTo("r08-scan-records-1");
        assertThat(SnapshotJson.read(files.snapshot(payload.snapshotHash())).orders().getFirst().originalLines())
                .extracting(l->l.operatorName()).containsOnly("test_pda01");
        generated(result);
    }
}
