package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real transactions; the synthetic source writer exercises the still-closed evidence owner branch. */
@SpringBootTest @AutoConfigureMockMvc
class SourceFoundationConcurrencyIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;@Autowired IdentityWriteGuard guard;
    @ParameterizedTest @ValueSource(booleans={true,false})
    void sourceAndFormalCommitInBothOrdersWithoutOverwritingOwner(boolean sourceFirst) throws Exception {
        var t=new TemporarySubmissionFixture(new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false));
        t.abort();String set=SourceFoundationFixture.evidenceSet(t);var formal=new SubmissionFixture(t.f);
        var waiterId=new AtomicLong();var evidence=new LinkedHashMap<String,Object>();var tx=transaction();
        try(var pool=Executors.newSingleThreadExecutor()) {
            var future=new java.util.concurrent.atomic.AtomicReference<Future<String>>();
            tx.executeWithoutResult(status->{
                long blocker=jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class);
                evidence.put("blocker",blocker);evidence.put("isolation",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));
                String first=sourceFirst?source(t,set):formal(formal);evidence.put("first",first);
                future.set(pool.submit(()->transaction().execute(s->{
                    waiterId.set(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));
                    return sourceFirst?formal(formal):source(t,set);
                })));
                evidence.put("waits",waits(blocker,waiterId));
            });
            evidence.put("second",future.get().get(20,TimeUnit.SECONDS));
        }
        finish(t,set,sourceFirst,evidence);
    }
    private TransactionTemplate transaction() {
        var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setTimeout(25);return tx;
    }
    private String source(TemporarySubmissionFixture t,String set) {
        guard.lock("1");guard.users(List.of(UUID.fromString(t.f.tokens.get("userId").asText())));
        jdbc.queryForList("SELECT id FROM warehouse WHERE id=? FOR UPDATE",Ids.bytes(t.f.web.identity().warehouse()));
        byte[] bale=Ids.bytes(UUID.fromString(t.body.at("/scans/0/baleId").asText()));
        jdbc.queryForList("SELECT id FROM bale WHERE id=? FOR UPDATE",bale);
        if(!jdbc.queryForList("SELECT bale_id FROM bale_commitment WHERE bale_id=? FOR UPDATE",bale).isEmpty())return "CONFLICT";
        SourceFoundationFixture.reserveSet(t,set,null);return "SOURCE_RESERVED";
    }
    private String formal(SubmissionFixture f) {
        try { return f.submit().path("issues").toString().contains("BALE_ALREADY_COMMITTED")?"CONFLICT":"FORMAL_COMMITTED"; }
        catch(Exception error) { throw new IllegalStateException(error); }
    }
    private List<Map<String,Object>> waits(long blocker,AtomicLong waiter) {
        var rows=new java.util.concurrent.atomic.AtomicReference<List<Map<String,Object>>>();
        await().atMost(Duration.ofSeconds(8)).untilAsserted(()->{
            rows.set(jdbc.queryForList("""
                SELECT r.OBJECT_NAME,r.LOCK_MODE,rt.PROCESSLIST_ID waitingConnection,bt.PROCESSLIST_ID blockingConnection
                FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks r ON r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND r.ENGINE=w.ENGINE
                JOIN performance_schema.threads rt ON rt.THREAD_ID=w.REQUESTING_THREAD_ID JOIN performance_schema.threads bt ON bt.THREAD_ID=w.BLOCKING_THREAD_ID
                WHERE rt.PROCESSLIST_ID=? AND bt.PROCESSLIST_ID=?
                """,waiter.get(),blocker));
            assertThat(rows.get()).anySatisfy(r->assertThat(r.get("OBJECT_NAME")).isEqualTo("user_management_guard"));
        });return rows.get();
    }
    private void finish(TemporarySubmissionFixture t,String set,boolean sourceFirst,Map<String,Object> evidence) throws Exception {
        var rows=jdbc.queryForList("SELECT state,BIN_TO_UUID(submission_id) submissionId,BIN_TO_UUID(source_evidence_set_id) evidenceSet,dispatch_cycle FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",t.body.at("/scans/0/baleId").asText());
        assertThat(rows).hasSize(1);assertThat(rows.getFirst().get("state")).isEqualTo(sourceFirst?"SOURCE_RESERVED":"DISPATCHED");
        assertThat(evidence.get("second")).isEqualTo("CONFLICT");
        if(sourceFirst)assertThat(rows.getFirst().get("evidenceSet")).isEqualTo(set);else assertThat(rows.getFirst().get("evidenceSet")).isNull();
        evidence.put("sourceFirst",sourceFirst);evidence.put("finalCommitment",rows);
        Path directory=Path.of("../.local/evidence/source-concurrency");Files.createDirectories(directory);
        json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("source-first-"+sourceFirst+".json").toFile(),evidence);
    }
}
