package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc
class PublicationConcurrencyIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean Access access;
    @Test void competingPublishersCannotBothReplaceTheSameBaseline() throws Exception {
        var api = new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN"));
        String code = String.format("999B%07d", Math.floorMod(System.nanoTime(), 10000000));
        String first = api.stage(SkuFixture.row(code, "305"));
        String second = api.stage(SkuFixture.row(code, "306"));
        var body = api.decisions(first); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> compete(api, first, body.deepCopy(), ready, start));
            var b = pool.submit(() -> compete(api, second, body.deepCopy(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var responses = java.util.List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertThat(responses).containsExactlyInAnyOrder("OK", "PUBLICATION_BASELINE_CHANGED");
        }
        assertThat(api.data(get("/api/v1/inventory/snapshots")).get("items").size()).isEqualTo(1);
        assertThat(api.data(get("/api/v1/delivery-mappings/999/history")).get("items").size()).isEqualTo(1);
    }
    private String compete(PublicationDriver api, String id, com.fasterxml.jackson.databind.node.ObjectNode body,
                           CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
        var response = api.send(api.publication(id, body, UUID.randomUUID().toString())).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("code").asText();
    }
    @Test void publisherWaitingForWarehouseLockMustReadNewCommittedBaseline() throws Exception {
        var api = new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN"));
        String code = String.format("996B%07d", Math.floorMod(System.nanoTime(), 10000000));
        String first = api.stage(SkuFixture.row(code, "305")); String second = api.stage(SkuFixture.row(code, "306"));
        var original = api.decisions(first); var authorized = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            var result = call.callRealMethod();
            if (Thread.currentThread().getName().equals("stale-publisher")) authorized.countDown();
            return result;
        }).when(access).require("inventory:publish", api.identity().warehouse());
        var tx = new TransactionTemplate(transactions); tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var pending = new java.util.concurrent.atomic.AtomicReference<Future<String>>();
        try (var pool = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("stale-publisher").factory())) {
            tx.executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM warehouse WHERE id=? FOR UPDATE", Ids.bytes(api.identity().warehouse()));
                pending.set(pool.submit(() -> {
                    var response = api.send(api.publication(second, original, UUID.randomUUID().toString())).andReturn().getResponse();
                    return json.readTree(response.getContentAsString()).get("code").asText();
                }));
                try {
                    assertThat(authorized.await(5, TimeUnit.SECONDS)).isTrue();
                    api.data(api.publication(first, original, UUID.randomUUID().toString()));
                } catch (Exception error) { throw new IllegalStateException(error); }
            });
            assertThat(pending.get().get(30, TimeUnit.SECONDS)).isEqualTo("PUBLICATION_BASELINE_CHANGED");
        }
    }
}
