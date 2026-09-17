package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class RecallConcurrencyIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private RecallFixture fixture() throws Exception {
        var web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        return new RecallFixture(new SubmissionFixture(web,jdbc,true,false));
    }
    @Test void submissionAndRecallRaceCannotBothComplete() throws Exception {
        var r=fixture();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var recall=pool.submit(()->{start.await();return r.f.web.send(r.request()).andReturn().getResponse();});
            var submission=pool.submit(()->{start.await();return r.f.submit();});start.countDown();
            var a=recall.get(20,TimeUnit.SECONDS);var b=submission.get(20,TimeUnit.SECONDS);
            if(a.getStatus()==200) { assertThat(b.get("status").asText()).isEqualTo("REVIEW_REQUIRED");assertThat(r.f.count("dispatch_record")).isZero(); }
            else { assertThat(a.getStatus()).isEqualTo(409);assertThat(b.get("status").asText()).isEqualTo("SUCCEEDED");assertThat(r.f.count("dispatch_record")).isEqualTo(2); }
        }
    }
    @Test void stopOutboxFailureRollsBackRevocationAndProof() throws Exception {
        var r=fixture();r.start(true);r.upload();
        var bytes=Ids.bytes(UUID.fromString(r.f.order));long next=jdbc.queryForObject("SELECT row_version+1 FROM outbound_order WHERE id=?",Long.class,bytes);
        var conflict=Ids.next();jdbc.update("INSERT INTO order_sync_outbox VALUES(?,1,?,?,?,'TEST_CONFLICT','{}',NULL,UTC_TIMESTAMP(3))",
            Ids.bytes(conflict),Ids.bytes(r.f.web.identity().warehouse()),bytes,next);
        try {
            r.f.pda.send(r.ack(r.ackBody()),r.f.tokens).andExpect(status().isConflict());
            assertThat(r.read().get("status").asText()).isEqualTo("REQUESTED");assertThat(r.read().has("stopProof")).isFalse();
            assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM offline_authorization WHERE assignment_id=?",Boolean.class,
                Ids.bytes(UUID.fromString(r.f.assignment.get("id").asText())))).isTrue();
        } finally { jdbc.update("DELETE FROM order_sync_outbox WHERE id=?",Ids.bytes(conflict)); }
        assertThat(r.stop().get("status").asText()).isEqualTo("STOPPED");
    }
}
