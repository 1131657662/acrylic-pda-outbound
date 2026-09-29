package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.temporary.command.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Records actual MySQL connection/lock-wait/commit evidence; latches only control scheduling. */
final class TemporaryConfirmationRaceProbe {
    final CountDownLatch held=new CountDownLatch(1),entered=new CountDownLatch(1),release=new CountDownLatch(1);
    private final Map<String,Object> evidence=Collections.synchronizedMap(new LinkedHashMap<>());
    private final TemporaryConfirmationFixture f;
    private volatile long holder,waiter;
    TemporaryConfirmationRaceProbe(TemporaryConfirmationFixture f) { this.f=f; }
    void holder() {
        holder=transaction("holder");held.countDown();gate(release);
    }
    CommandLocks locks() {
        return new CommandLocks(f.bean(com.acrylic.outbound.common.IdentityWriteGuard.class),
            f.bean(com.acrylic.outbound.inventory.master.WarehouseBaseline.class),f.bean(com.acrylic.outbound.devices.DeviceStore.class),
            f.bean(com.acrylic.outbound.pda.PdaSessionLocks.class),f.jdbc) {
            @Override public void identity(CommandIdentity.Actor actor,CommandContext hint) {
                if(Thread.currentThread().getName().equals("confirmation-waiter")) { waiter=transaction("waiter");entered.countDown(); }
                super.identity(actor,hint);
            }
        };
    }
    private long transaction(String name) {
        var row=f.jdbc.queryForMap("SELECT CONNECTION_ID() id,@@transaction_isolation isolationLevel,@@autocommit autocommit");
        assertThat(row.get("isolationLevel")).isEqualTo("READ-COMMITTED");assertThat(((Number)row.get("autocommit")).intValue()).isZero();
        evidence.put(name,row);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { evidence.put(name+"Completion",status==STATUS_COMMITTED?"COMMITTED":"ROLLED_BACK"); }
        });
        return ((Number)row.get("id")).longValue();
    }
    void waiting() {
        gate(entered);assertThat(holder).isNotEqualTo(waiter);
        await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(25)).untilAsserted(()->{
            var rows=f.jdbc.queryForList("""
                SELECT r.OBJECT_NAME,r.LOCK_MODE requestedMode,b.LOCK_MODE blockingMode,
                  rt.PROCESSLIST_ID waitingConnection,bt.PROCESSLIST_ID blockingConnection
                FROM performance_schema.data_lock_waits w
                JOIN performance_schema.data_locks r ON r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND r.ENGINE=w.ENGINE
                JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID AND b.ENGINE=w.ENGINE
                JOIN performance_schema.threads rt ON rt.THREAD_ID=r.THREAD_ID
                JOIN performance_schema.threads bt ON bt.THREAD_ID=b.THREAD_ID
                WHERE rt.PROCESSLIST_ID=? AND bt.PROCESSLIST_ID=? AND r.OBJECT_SCHEMA=DATABASE()
                """,waiter,holder);
            assertThat(rows).isNotEmpty();evidence.put("waits",rows);
        });
    }
    void save(String name) throws Exception {
        evidence.put("finalState",f.state());
        String run=System.getenv("E2A_CROSS_RUN");if(run==null || !run.matches("[1-9][0-9]{0,2}"))throw new IllegalStateException();
        var path=com.acrylic.outbound.e2averify.E2aWorktree.ROOT.resolve(".local/e2a-cross-20260925-"+run).resolve("confirmation-race-"+name+".json");
        Files.writeString(path,f.bean(JsonText.class).encode(evidence),StandardOpenOption.CREATE_NEW);
    }
    static void gate(CountDownLatch latch) {
        try { if(!latch.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Race scheduling timed out"); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw new IllegalStateException(interrupted); }
    }
}
