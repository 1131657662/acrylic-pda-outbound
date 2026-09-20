package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

final class IdentityLockEvidence {
    private final JdbcTemplate jdbc;
    private final Map<String,Object> evidence=new LinkedHashMap<>();
    private final Map<String,Long> requested=new HashMap<>();
    IdentityLockEvidence(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    long connection(String actor) {
        var state=jdbc.queryForMap("SELECT CONNECTION_ID() connectionId,@@transaction_isolation isolationLevel,@@autocommit autocommit");
        assertThat(state.get("isolationLevel")).isEqualTo("READ-COMMITTED");
        assertThat(((Number)state.get("autocommit")).intValue()).isZero();
        synchronized(evidence) { evidence.putIfAbsent(actor,state);requested.putIfAbsent(actor,System.nanoTime()); }
        return ((Number)state.get("connectionId")).longValue();
    }
    void acquired(String actor) {
        long acquired=System.nanoTime();
        synchronized(evidence) {
            if(evidence.containsKey(actor+"WaitMs")) return;
            evidence.put(actor+"WaitMs",(acquired-requested.get(actor))/1_000_000.0);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                synchronized(evidence) {
                    evidence.put(actor+"HoldMs",(System.nanoTime()-acquired)/1_000_000.0);
                    evidence.put(actor+"Completion",status==STATUS_COMMITTED?"COMMITTED":"ROLLED_BACK");
                }
            }
        });
    }
    void waiting(long blocker,long waiter,boolean userHeld) {
        await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(25)).untilAsserted(()->{
            var rows=jdbc.queryForList("""
                SELECT r.OBJECT_SCHEMA,r.OBJECT_NAME,r.LOCK_MODE requestedMode,b.LOCK_MODE blockingMode,
                       rt.PROCESSLIST_ID waitingConnection,bt.PROCESSLIST_ID blockingConnection
                FROM performance_schema.data_lock_waits w
                INNER JOIN performance_schema.data_locks r ON r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND r.ENGINE=w.ENGINE
                INNER JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID AND b.ENGINE=w.ENGINE
                INNER JOIN performance_schema.threads rt ON rt.THREAD_ID=r.THREAD_ID
                INNER JOIN performance_schema.threads bt ON bt.THREAD_ID=b.THREAD_ID
                WHERE rt.PROCESSLIST_ID=? AND bt.PROCESSLIST_ID=?
                """,waiter,blocker);
            assertThat(rows).anySatisfy(r->assertThat(r.get("OBJECT_NAME")).isEqualTo("user_management_guard"));
            evidence.put("waits",rows);
        });
        var held=locks(blocker);var blocked=locks(waiter);
        if(userHeld) assertThat(held).anySatisfy(r->{
            assertThat(r.get("OBJECT_NAME")).isEqualTo("app_user");
            assertThat(r.get("LOCK_MODE").toString()).startsWith("X");
            assertThat(r.get("LOCK_TYPE")).isEqualTo("RECORD");
        });
        assertThat(blocked).noneSatisfy(r->{
            assertThat(r.get("OBJECT_NAME")).isIn("warehouse","device");
            assertThat(r.get("LOCK_TYPE")).isEqualTo("RECORD");
            assertThat(r.get("LOCK_STATUS")).isEqualTo("GRANTED");
        });
        evidence.put("blockerLocks",held);evidence.put("waiterLocks",blocked);
    }
    private List<Map<String,Object>> locks(long connection) {
        return jdbc.queryForList("""
            SELECT l.OBJECT_NAME,l.INDEX_NAME,l.LOCK_TYPE,l.LOCK_MODE,l.LOCK_STATUS,l.LOCK_DATA
            FROM performance_schema.data_locks l INNER JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID
            WHERE t.PROCESSLIST_ID=? ORDER BY l.OBJECT_NAME,l.LOCK_TYPE,l.LOCK_MODE
            """,connection);
    }
    void save(String scenario,long elapsedMillis,String result) throws Exception {
        evidence.put("scenario",scenario);evidence.put("elapsedMillis",elapsedMillis);evidence.put("result",result);
        String directory=System.getenv("L1_LOCK_EVIDENCE");
        if(directory!=null) {
            Path path=Path.of(directory);Files.createDirectories(path);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.resolve(scenario+"-"+UUID.randomUUID()+".json").toFile(),evidence);
        }
    }
}
