package com.acrylic.outbound.ledgerverify;

import java.time.Duration;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Captures actual MySQL connection/wait/completion evidence for this round only. */
final class LedgerLockEvidence {
    private final JdbcTemplate jdbc;
    private final Map<String,Object> evidence=Collections.synchronizedMap(new LinkedHashMap<>());
    LedgerLockEvidence(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    long transaction(String actor) {
        var state=jdbc.queryForMap("SELECT CONNECTION_ID() connectionId,@@transaction_isolation isolationLevel,@@autocommit autocommit");
        assertThat(state.get("isolationLevel")).isEqualTo("READ-COMMITTED");
        assertThat(((Number)state.get("autocommit")).intValue()).isZero();evidence.put(actor,state);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { evidence.put(actor+"Completion",status==STATUS_COMMITTED?"COMMITTED":"ROLLED_BACK"); }
        });
        return ((Number)state.get("connectionId")).longValue();
    }

    void waiting(long blocker,long waiter) {
        assertThat(blocker).isNotEqualTo(waiter);
        await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(25)).untilAsserted(()->{
            var rows=jdbc.queryForList("SELECT r.OBJECT_SCHEMA,r.OBJECT_NAME,r.LOCK_MODE requestedMode,b.LOCK_MODE blockingMode,"
                +"rt.PROCESSLIST_ID waitingConnection,bt.PROCESSLIST_ID blockingConnection FROM performance_schema.data_lock_waits w "
                +"JOIN performance_schema.data_locks r ON r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND r.ENGINE=w.ENGINE "
                +"JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID AND b.ENGINE=w.ENGINE "
                +"JOIN performance_schema.threads rt ON rt.THREAD_ID=r.THREAD_ID JOIN performance_schema.threads bt ON bt.THREAD_ID=b.THREAD_ID "
                +"WHERE rt.PROCESSLIST_ID=? AND bt.PROCESSLIST_ID=? AND r.OBJECT_SCHEMA=?",waiter,blocker,LedgerVerifyEnvironment.DATABASE);
            assertThat(rows).isNotEmpty();evidence.put("waits",rows);
        });
        evidence.put("blockerLocks",locks(blocker));evidence.put("waiterLocks",locks(waiter));
    }

    private List<Map<String,Object>> locks(long connection) {
        return jdbc.queryForList("SELECT l.OBJECT_NAME,l.INDEX_NAME,l.LOCK_TYPE,l.LOCK_MODE,l.LOCK_STATUS,l.LOCK_DATA "
            +"FROM performance_schema.data_locks l JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID "
            +"WHERE t.PROCESSLIST_ID=? AND l.OBJECT_SCHEMA=? ORDER BY l.OBJECT_NAME,l.LOCK_TYPE,l.LOCK_MODE",connection,LedgerVerifyEnvironment.DATABASE);
    }

    void save(String name,Object finalState) throws Exception {
        evidence.put("finalState",finalState);new LedgerOriginalFacts(jdbc).save(name+"-"+UUID.randomUUID()+".json",evidence);
    }
}
