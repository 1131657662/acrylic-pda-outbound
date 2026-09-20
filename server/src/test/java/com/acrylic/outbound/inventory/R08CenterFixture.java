package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.reports.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class R08CenterFixture {
    static {
        if(!Objects.toString(System.getenv("OUTBOUND_DB_URL"),"").startsWith("jdbc:mysql://127.0.0.1:23308/acrylic_r08_it?"))
            throw new IllegalStateException("R08 center tests require the dedicated integration database");
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired BackgroundJobWorker worker;@Autowired BackgroundJobStore jobs;@Autowired ExportJobQueries queries;@Autowired ExportFiles files;
    PublicationDriver api;boolean configured;
    @BeforeEach void identity() { api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")); }
    PcFixture completed() throws Exception {
        var fixture=new PcFixture(api,false,1,1,!configured);configured=true;fixture.start();
        for(int i=0;i<3;i++) fixture.add(i);
        fixture.submit("");return fixture;
    }
    PcFixture completedAt(String instant) throws Exception {
        var fixture=completed();
        jdbc.update("UPDATE order_result_version SET created_at=? WHERE order_id=? AND result_version=1",
                java.sql.Timestamp.from(Instant.parse(instant)),Ids.bytes(UUID.fromString(fixture.order)));
        return fixture;
    }
    MockHttpServletRequestBuilder request(String key,String format,String from,String through) {
        return post("/api/v1/exports").header("Idempotency-Key",key).contentType("application/json")
                .content(json.createObjectNode().put("format",format).put("basis","ORIGINAL_CONFIRMED")
                        .set("range",json.createObjectNode().put("fromDate",from).put("throughDate",through)).toString());
    }
    JsonNode create(String key,String format) throws Exception {
        String day=LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();return api.data(request(key,format,day,day));
    }
    MockHttpServletRequestBuilder retry(UUID id,String key) {
        return post("/api/v1/exports/"+id+"/retry").header("Idempotency-Key",key);
    }
    UUID id(JsonNode response) { return UUID.fromString(response.get("id").asText()); }
    ExportModels.Payload payload(UUID id) { return queries.payload(jobs.find(id).orElseThrow()); }
    ReportSnapshot snapshot(UUID id) throws Exception { return SnapshotJson.read(files.snapshot(payload(id).snapshotHash())); }
    void state(UUID id,String status) {
        jdbc.update("UPDATE background_job SET status=?,attempt_count=1 WHERE id=?",status,Ids.bytes(id));
    }
    void expire(UUID id) {
        jdbc.update("UPDATE background_job SET payload=JSON_SET(payload,'$.expiresAt','2020-01-01T00:00:00Z') WHERE id=?",Ids.bytes(id));
    }
    void generate(UUID id) {
        for(int i=0;i<100;i++) {
            if(jobs.find(id).orElseThrow().status()==BackgroundJob.Status.SUCCEEDED) return;
            var result=worker.runOnce("r08-center-test");assertThat(result).isPresent();
            if(result.get().jobId().equals(id)) assertThat(result.get().outcome()).isEqualTo("SUCCEEDED");
        }
        throw new AssertionError("Target export did not finish");
    }
    void download(UUID id,String filename) throws Exception {
        byte[] bytes=api.send(get("/api/v1/exports/"+id+"/file")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        Files.write(Path.of(System.getenv("OUTBOUND_STORAGE"),filename),bytes);
        Files.copy(files.snapshot(payload(id).snapshotHash()),Path.of(System.getenv("OUTBOUND_STORAGE"),filename+".snapshot.json"),StandardCopyOption.REPLACE_EXISTING);
    }
}
