package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
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
class R08ExportFenceIT {
    static {
        if(!Objects.toString(System.getenv("OUTBOUND_DB_URL"),"").startsWith("jdbc:mysql://127.0.0.1:23308/acrylic_r08_it"))
            throw new IllegalStateException("R08 fence tests require the dedicated database");
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired BackgroundJobLeases leases;@Autowired BackgroundJobStore jobs;@Autowired ExportJobHandler handler;
    @Autowired ExportJobQueries queries;@Autowired ExportFiles files;
    private PublicationDriver api;private UUID id;
    @BeforeEach void task() throws Exception {
        api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        var fixture=new PcFixture(api,false,1,1);fixture.start();for(int i=0;i<3;i++) fixture.add(i);fixture.submit("");
        var response=api.data(post("/api/v1/orders/"+fixture.order+"/exports").header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content("{\"format\":\"XLSX\",\"basis\":\"ORIGINAL_CONFIRMED\"}"));
        id=UUID.fromString(response.get("id").asText());
    }
    private String run(Consumer<BackgroundJob> afterRender,boolean failPublication) {
        var wrapped=new BackgroundJobHandler() {
            public String jobType() { return ExportModels.TYPE; }
            public Runnable execute(BackgroundJobExecution execution) throws Exception {
                Runnable publish=handler.execute(execution);
                if(!execution.job().id().equals(id)) return publish;
                afterRender.accept(execution.job());
                return ()->{publish.run();if(failPublication) throw new IllegalStateException("Injected rollback");};
            }
        };
        var worker=new BackgroundJobWorker(leases,List.of(wrapped));
        for(int i=0;i<40;i++) {
            var result=worker.runOnce("r08-fence");assertThat(result).isPresent();
            if(result.get().jobId().equals(id)) return result.get().outcome();
        }
        throw new AssertionError("Target export not claimed");
    }
    private void retryNow() { jdbc.update("UPDATE background_job SET available_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(id)); }
    @Test void expiredLeaseCannotPublishEvenAfterFileWasWritten() {
        assertThat(run(job->jdbc.update("UPDATE background_job SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?",Ids.bytes(id)),false)).isEqualTo("STALE");
        assertThat(queries.payload(jobs.find(id).orElseThrow()).artifact()).isNull();
        leases.recoverExpired(Set.of(ExportModels.TYPE));retryNow();assertThat(run(job->{},false)).isEqualTo("SUCCEEDED");
        assertThat(jobs.find(id).orElseThrow().attemptCount()).isEqualTo(2);
    }
    @Test void publicationRollbackDoesNotExposeArtifactOrSuccessfulStatus() {
        assertThat(run(job->{},true)).isEqualTo("RETRY_SCHEDULED");
        assertThat(queries.payload(jobs.find(id).orElseThrow()).artifact()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE event_type='REPORT_EXPORT_GENERATED' AND details->>'$.jobId'=?",Long.class,id.toString())).isZero();
        retryNow();assertThat(run(job->{},false)).isEqualTo("SUCCEEDED");
        assertThat(queries.payload(jobs.find(id).orElseThrow()).artifact()).isNotNull();
    }
    @Test void recoveryGenerationBlocksPublicationAndDownload() throws Exception {
        try {
            assertThat(run(job->jdbc.update("UPDATE server_state SET recovery_epoch=recovery_epoch+1 WHERE id=1"),false)).isEqualTo("RETRY_SCHEDULED");
            assertThat(queries.view(jobs.find(id).orElseThrow()).status()).isEqualTo("INVALIDATED");
            api.send(get("/api/v1/exports/"+id+"/file")).andExpect(status().isConflict());
            retryNow();assertThat(run(job->{},false)).isEqualTo("FAILED");
            assertThat(jobs.find(id).orElseThrow().lastError()).isEqualTo("EXPORT_GENERATION_CHANGED");
        } finally { jdbc.update("UPDATE server_state SET recovery_epoch=1 WHERE id=1"); }
    }
    @Test void missingArtifactAndExpiredTaskAreNotDownloadable() throws Exception {
        assertThat(run(job->{},false)).isEqualTo("SUCCEEDED");var p=queries.payload(jobs.find(id).orElseThrow());
        Files.delete(files.artifact(id,p.artifact().token(),p.format()));
        api.send(get("/api/v1/exports/"+id+"/file")).andExpect(status().isGone());
        jdbc.update("UPDATE background_job SET payload=JSON_SET(payload,'$.expiresAt','2020-01-01T00:00:00Z') WHERE id=?",Ids.bytes(id));
        assertThat(queries.view(jobs.find(id).orElseThrow()).status()).isEqualTo("EXPIRED");
        api.send(get("/api/v1/exports/"+id+"/file")).andExpect(status().isConflict());
    }
}
