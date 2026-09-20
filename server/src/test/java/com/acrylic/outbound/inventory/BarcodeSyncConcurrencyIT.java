package com.acrylic.outbound.inventory;

import com.acrylic.outbound.sync.BarcodeChanges;
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
class BarcodeSyncConcurrencyIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.acrylic.outbound.sync.BarcodeBaselineStore baselines;
    private PublicationDriver web;private PdaTestDriver pda;private JsonNode tokens;private String bale;
    @BeforeEach void fixture() throws Exception {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        pda=new PdaTestDriver(web);pda.readyOrder();tokens=pda.login(pda.register());
        bale=sync("").at("/baseline/0/baleId").asText();
    }
    @Test void uncommittedStockChangeIsSkippedAndPublishedAfterCommit() throws Exception {
        var baseline=sync("");var ready=new CountDownLatch(1);var commit=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var pending=pool.submit(()->tx().executeWithoutResult(status->{
                change();ready.countDown();
                try { if(!commit.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("commit timeout"); }
                catch(InterruptedException error) { throw new IllegalStateException(error); }
            }));
            try {
                assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();
                var early=sync(baseline.get("nextCursor").asText());assertThat(early.get("events")).isEmpty();
                commit.countDown();pending.get(15,TimeUnit.SECONDS);
                var later=sync(early.get("nextCursor").asText());assertThat(later.get("events")).hasSize(1);
                assertThat(later.at("/events/0/status").asText()).isEqualTo("UNAVAILABLE");
                assertThat(later.at("/events/0/sequence").asLong()).isEqualTo(early.get("highWatermark").asLong()+1);
            } finally { commit.countDown(); }
        }
    }
    @Test void businessRollbackNeverPublishesRestriction() throws Exception {
        var baseline=sync("");tx().executeWithoutResult(status->{ change();status.setRollbackOnly(); });
        assertThat(sync(baseline.get("nextCursor").asText()).get("events")).isEmpty();
    }
    @Test void twoReadersReceiveIdenticalEventWithoutDoublePublication() throws Exception {
        var baseline=sync("");tx().executeWithoutResult(status->change());var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<JsonNode> action=()->{ assertThat(start.await(5,TimeUnit.SECONDS)).isTrue();return sync(baseline.get("nextCursor").asText()); };
            var one=pool.submit(action);var two=pool.submit(action);start.countDown();
            var a=one.get(15,TimeUnit.SECONDS);var b=two.get(15,TimeUnit.SECONDS);
            assertThat(a.get("events")).hasSize(1);assertThat(a).isEqualTo(b);
        }
    }
    @Test void failedBaselineRollsBackPublicationAndPendingMarker() throws Exception {
        var baseline=sync("");tx().executeWithoutResult(status->change());
        org.mockito.Mockito.doThrow(new IllegalStateException("read failure")).when(baselines).page(
            org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
        pda.send(get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","1"),tokens).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT pending FROM barcode_sync_pending WHERE bale_id=UUID_TO_BIN(?)",Boolean.class,bale)).isTrue();
        assertThat(jdbc.queryForObject("SELECT MAX(sequence_no) FROM barcode_sync_event WHERE warehouse_id=UUID_TO_BIN(?)",Long.class,web.identity().warehouse().toString())).isEqualTo(baseline.get("highWatermark").asLong());
    }
    private void change() {
        jdbc.update("UPDATE inventory_position SET blocked_reason='隔离测试',row_version=row_version+1 WHERE bale_id=UUID_TO_BIN(?)",bale);
        BarcodeChanges.bale(jdbc,UUID.fromString(bale));
    }
    private TransactionTemplate tx() {
        var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return tx;
    }
    private JsonNode sync(String cursor) throws Exception {
        return pda.data(get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","1").param("cursor",cursor),tokens);
    }
}
