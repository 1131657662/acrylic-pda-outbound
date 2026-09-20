package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class R08RetryIT extends R08CenterFixture {
    @Test void legacySingleOrderPayloadRemainsQueryableAndRetryable() throws Exception {
        var fixture=completed();
        UUID original=id(api.data(post("/api/v1/orders/"+fixture.order+"/exports").header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content("{\"format\":\"XLSX\",\"basis\":\"ORIGINAL_CONFIRMED\"}")));
        state(original,"FAILED");
        jdbc.update("UPDATE background_job SET payload=JSON_REMOVE(payload,'$.range','$.orderCount','$.retryOf') WHERE id=?",Ids.bytes(original));
        var view=api.data(get("/api/v1/exports/"+original));
        assertThat(view.path("orderId").asText()).isEqualTo(fixture.order);assertThat(view.path("orderCount").asInt()).isEqualTo(1);
        UUID next=id(api.data(retry(original,UUID.randomUUID().toString())));
        assertThat(payload(next).snapshotHash()).isEqualTo(payload(original).snapshotHash());
        assertThat(queries.view(jobs.find(next).orElseThrow()).orderCount()).isEqualTo(1);
    }
    @Test void concurrentSameRetryKeyCreatesOneNewTask() throws Exception {
        UUID original=id(create(UUID.randomUUID().toString(),"XLSX"));state(original,"FAILED");String key=UUID.randomUUID().toString();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->api.data(retry(original,key)));var second=pool.submit(()->api.data(retry(original,key)));
            assertThat(id(first.get(15,java.util.concurrent.TimeUnit.SECONDS))).isEqualTo(id(second.get(15,java.util.concurrent.TimeUnit.SECONDS)));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_job WHERE dedupe_key=?",Long.class,
                "r08:"+api.identity().user().id()+":"+key)).isEqualTo(1);
    }
    @Test void failedRangeRetryPreservesSnapshotAfterMoreOrdersAndReplaysSameTask() throws Exception {
        var first=completed();UUID original=id(create(UUID.randomUUID().toString(),"XLSX"));state(original,"FAILED");
        completed();String key=UUID.randomUUID().toString();UUID next=id(api.data(retry(original,key)));
        assertThat(payload(next).snapshotHash()).isEqualTo(payload(original).snapshotHash());
        assertThat(payload(next).snapshotId()).isEqualTo(payload(original).snapshotId());
        assertThat(snapshot(next).orders()).extracting(o->o.id()).containsExactly(first.order);
        assertThat(id(api.data(retry(original,key)))).isEqualTo(next);
        generate(next);download(next,"center-retry.xlsx");
    }
    @Test void expiredTaskRetriesPreservedSnapshotWithNewExpiry() throws Exception {
        UUID original=id(create(UUID.randomUUID().toString(),"XLSX"));expire(original);
        UUID next=id(api.data(retry(original,UUID.randomUUID().toString())));
        assertThat(payload(next).snapshotHash()).isEqualTo(payload(original).snapshotHash());
        assertThat(payload(next).expiresAt()).isAfter(java.time.Instant.now());
        assertThat(payload(next).retryOf()).isEqualTo(original);
    }
    @Test void activeTaskCannotRetryAndRequestKeyCannotChangeToAnotherOperation() throws Exception {
        String key=UUID.randomUUID().toString();UUID original=id(create(key,"XLSX"));
        api.send(retry(original,UUID.randomUUID().toString())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPORT_NOT_RETRYABLE"));
        state(original,"FAILED");
        api.send(retry(original,key)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        String retryKey=UUID.randomUUID().toString();api.data(retry(original,retryKey));
        String today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        api.send(request(retryKey,"XLSX",today,today)).andExpect(status().isConflict());
    }
    @Test void missingOrAlteredSnapshotCannotRetry() throws Exception {
        UUID original=id(create(UUID.randomUUID().toString(),"XLSX"));state(original,"FAILED");
        Files.writeString(files.snapshot(payload(original).snapshotHash()),"tampered");
        api.send(retry(original,UUID.randomUUID().toString())).andExpect(status().isGone());
        Files.delete(files.snapshot(payload(original).snapshotHash()));
        api.send(retry(original,UUID.randomUUID().toString())).andExpect(status().isGone());
    }
    @Test void oldEpochAndUnavailableTemplateCannotRetry() throws Exception {
        UUID original=id(create(UUID.randomUUID().toString(),"XLSX"));state(original,"FAILED");
        jdbc.update("UPDATE background_job SET payload=JSON_SET(payload,'$.template','retired') WHERE id=?",Ids.bytes(original));
        api.send(retry(original,UUID.randomUUID().toString())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPORT_TEMPLATE_UNAVAILABLE"));
        jdbc.update("UPDATE background_job SET payload=JSON_SET(payload,'$.epoch','0') WHERE id=?",Ids.bytes(original));
        api.send(retry(original,UUID.randomUUID().toString())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPORT_GENERATION_CHANGED"));
    }
}
