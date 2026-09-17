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
class ContinuationConcurrencyIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private ContinuationFixture fixture() throws Exception {
        return new ContinuationFixture(new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,true,false));
    }
    @Test void competingSecondBatchesChooseExactlyOneCurrentWithoutReleasingRetainedBales() throws Exception {
        var c=fixture();c.request();c.approve();c.activate();var one=c.second();var two=c.second();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return c.r.f.send(one).andReturn().getResponse().getStatus();});
            var second=pool.submit(()->{start.await();return c.r.f.send(two).andReturn().getResponse().getStatus();});start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(c.r.f.count("bale_commitment")).isEqualTo(2);assertThat(c.r.f.count("dispatch_record")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission WHERE scope_id=(SELECT id FROM submission_scope WHERE source_order_id=?) AND work_round=2",Integer.class,
            Ids.bytes(UUID.fromString(c.r.f.order)))).isEqualTo(1);
    }
    @Test void competingApprovalsCannotIssueTwoQualifications() throws Exception {
        var c=fixture();c.request();var command=c.approveBody();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return c.r.f.web.send(c.approve(command,UUID.randomUUID().toString())).andReturn().getResponse().getStatus();});
            var second=pool.submit(()->{start.await();return c.r.f.web.send(c.approve(command,UUID.randomUUID().toString())).andReturn().getResponse().getStatus();});start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE order_id=? AND active_marker=1",Integer.class,Ids.bytes(UUID.fromString(c.r.f.order)))).isEqualTo(1);
    }
    @Test void failedApprovalOutboxRollsBackNewAuthorizationRoundAndStock() throws Exception {
        var c=fixture();c.request();byte[] order=Ids.bytes(UUID.fromString(c.r.f.order));
        long next=jdbc.queryForObject("SELECT row_version+2 FROM outbound_order WHERE id=?",Long.class,order);var conflict=Ids.next();
        jdbc.update("INSERT INTO order_sync_outbox VALUES(?,1,?,?,?,'TEST_CONFLICT','{}',NULL,UTC_TIMESTAMP(3))",Ids.bytes(conflict),Ids.bytes(c.r.f.web.identity().warehouse()),order,next);
        try {
            c.r.f.web.send(c.approve(c.approveBody(),UUID.randomUUID().toString())).andExpect(status().isConflict());
            assertThat(c.r.read().at("/continuation/status").asText()).isEqualTo("REQUESTED");
            assertThat(jdbc.queryForObject("SELECT work_round FROM submission_scope WHERE source_order_id=?",Long.class,order)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE order_id=?",Long.class,order)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT state FROM bale_commitment WHERE order_id=?",String.class,order)).isEqualTo("REVIEW_RESERVED");
        } finally { jdbc.update("DELETE FROM order_sync_outbox WHERE id=?",Ids.bytes(conflict)); }
        c.approve();
    }
}
