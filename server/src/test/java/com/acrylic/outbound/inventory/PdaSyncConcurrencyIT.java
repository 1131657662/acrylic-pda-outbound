package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PdaSyncConcurrencyIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager transactions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.acrylic.outbound.sync.SyncBaselineStore baselines;
    private PublicationDriver web; private PdaTestDriver pda; private JsonNode tokens; private String order;
    @BeforeEach void fixture() throws Exception {
        web = new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN"));
        pda = new PdaTestDriver(web); order = pda.readyOrder(); tokens = pda.login(pda.register());
    }
    @Test void olderUncommittedOutboxRowReceivesSequenceAfterLaterCommittedRow() throws Exception {
        var baseline = sync(""); var ready = new CountDownLatch(1); var commit = new CountDownLatch(1);
        // UUID and timestamp order intentionally disagree with commit order, in this isolated warehouse.
        final String late = "00000000" + UUID.randomUUID().toString().substring(8);
        var tx = new TransactionTemplate(transactions); tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> tx.executeWithoutResult(status -> {
                event(late, 3, "ASSIGNED", "2020-01-01 00:00:00"); ready.countDown();
                try { if (!commit.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("commit timeout"); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
            }));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                event(UUID.randomUUID().toString(), 4, "CANCELLED", "2026-01-01 00:00:00");
                var early = sync(baseline.get("nextCursor").asText());
                assertThat(early.get("events")).hasSize(1);
                assertThat(early.at("/events/0/entityVersion").asText()).isEqualTo("4");
                commit.countDown(); pending.get(15, TimeUnit.SECONDS);
                var later = sync(early.get("nextCursor").asText());
                assertThat(later.at("/events/0/eventId").asText()).isEqualTo(late);
                assertThat(later.at("/events/0/sequence").asLong()).isEqualTo(early.at("/events/0/sequence").asLong() + 1);
                assertThat(sync("").at("/baseline/0/status").asText()).isEqualTo("CANCELLED");
            } finally { commit.countDown(); }
        }
    }
    @Test void concurrentReadersPublishEachEventExactlyOnce() throws Exception {
        var baseline = sync(""); event(UUID.randomUUID().toString(), 3, "RECALL_PENDING", "2026-01-01 00:00:00");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<JsonNode> action = () -> { assertThat(start.await(5, TimeUnit.SECONDS)).isTrue(); return sync(baseline.get("nextCursor").asText()); };
            var first = pool.submit(action); var second = pool.submit(action); start.countDown();
            var a = first.get(15, TimeUnit.SECONDS); var b = second.get(15, TimeUnit.SECONDS);
            assertThat(a.get("events")).hasSize(1); assertThat(a.get("events")).isEqualTo(b.get("events"));
            assertThat(a.get("nextCursor")).isEqualTo(b.get("nextCursor"));
        }
    }
    @Test void failedPublicationRollsBackProjectionCounterAndOutboxMarker() throws Exception {
        var baseline = sync("");
        event(UUID.randomUUID().toString(), 3, "RECALL_PENDING", "2026-01-01 00:00:00");
        org.mockito.Mockito.doThrow(new IllegalStateException("Simulated baseline read failure after publication"))
            .when(baselines).page(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        pda.send(get("/api/v1/pda/sync/orders").header("X-Server-Recovery-Epoch", "1")
            , tokens).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) AND published_seq IS NOT NULL", Integer.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM sync_order_projection WHERE order_id=UUID_TO_BIN(?)", String.class, order)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT next_seq FROM sync_stream WHERE warehouse_id=UUID_TO_BIN(?)", Long.class, web.identity().warehouse().toString())).isEqualTo(2);
    }
    @Test void baselineOnlyIncludesBoundedPublishedProjectionAndRemainderArrivesAsDelta() throws Exception {
        for (int version = 3; version <= 207; version++) event(UUID.randomUUID().toString(), version, "ASSIGNED", "2026-09-18 00:00:00");
        var baseline = sync(""); assertThat(baseline.get("highWatermark").asLong()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) AND published_seq IS NULL", Integer.class, order)).isEqualTo(6);
        assertThat(baseline.at("/baseline/0/status").asText()).isEqualTo("ASSIGNED");
        var delta = sync(baseline.get("nextCursor").asText()); assertThat(delta.get("events")).hasSize(6);
        assertThat(delta.at("/events/0/sequence").asText()).isEqualTo("201");
        assertThat(delta.get("highWatermark").asText()).isEqualTo("206");
    }
    private void event(String id, int version, String status, String time) {
        jdbc.update("INSERT INTO order_sync_outbox VALUES(UUID_TO_BIN(?),1,UUID_TO_BIN(?),UUID_TO_BIN(?),?,'SYNC_TEST',?,NULL,?)",
            id, web.identity().warehouse().toString(), order, version, json.createObjectNode().put("status", status).toString(), time);
    }
    private JsonNode sync(String cursor) throws Exception {
        return pda.data(get("/api/v1/pda/sync/orders").header("X-Server-Recovery-Epoch", "1").param("cursor", cursor), tokens);
    }
}
