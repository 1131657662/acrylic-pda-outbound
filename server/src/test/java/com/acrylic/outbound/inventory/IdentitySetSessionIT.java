package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentitySetSessionIT extends IdentitySetFixture {
    @Test void unsignedUserOrderWaitsOnLowUuidBeforeTakingHighUuid() throws Exception {
        UUID low=UUID.fromString("0"+UUID.randomUUID().toString().substring(1));
        UUID high=UUID.fromString("f"+UUID.randomUUID().toString().substring(1));
        for(UUID user:List.of(low,high)) jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(user),"l2-"+user,"L2 ordering fixture",passwords.encode(UUID.randomUUID().toString()));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var reached=new CountDownLatch(1);
        var blocker=new AtomicLong();var waiter=new AtomicLong();var evidence=new LinkedHashMap<String,Object>();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->transactional(()->holdUser(low,blocker,entered,release)));
            Future<String> b;
            try {
                assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
                b=pool.submit(()->transactional(()->{
                    guard.lock("1");waiter.set(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));reached.countDown();
                    guard.users(Arrays.asList(high,low,high,null));
                }));
                assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();captureWait(blocker.get(),waiter.get(),evidence);
                assertThat(count("""
                    SELECT COUNT(*) FROM performance_schema.data_locks l
                    INNER JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID
                    WHERE t.PROCESSLIST_ID=? AND l.OBJECT_NAME='app_user' AND l.LOCK_TYPE='RECORD' AND l.LOCK_STATUS='GRANTED'
                    """,waiter.get())).isZero();
            } finally { release.countDown(); }
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo("OK");assertThat(b.get(20,TimeUnit.SECONDS)).isEqualTo("OK");
        }
        evidence.put("low",low);evidence.put("high",high);save("C03-unsigned-user-order",evidence);
    }
    @Test void refreshWaitsForUserBeforeWarehouseDeviceOrSessionLocks() throws Exception {
        var api=web();var pda=new PdaTestDriver(api);var device=pda.register();var tokens=pda.login(device);
        var request=pda.command("/auth/refresh",pda.refreshBody(device,tokens.get("refreshToken").asText()));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var reached=new CountDownLatch(1);
        var blocker=new AtomicLong();var waiter=new AtomicLong();var evidence=new LinkedHashMap<String,Object>();
        doAnswer(call->{
            if(Thread.currentThread().getName().equals("l2-refresh")) {
                waiter.set(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));reached.countDown();
            }
            return call.callRealMethod();
        }).when(guardTarget()).users(anyCollection());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->transactional(()->holdUser(api.identity().user().id(),blocker,entered,release)));
            Future<String> b;
            try {
                assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
                b=pool.submit(()->{Thread.currentThread().setName("l2-refresh");return code(mvc.perform(request));});
                assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();captureWait(blocker.get(),waiter.get(),evidence);
            } finally { release.countDown(); }
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo("OK");assertThat(b.get(20,TimeUnit.SECONDS)).isEqualTo("AUTH_EXPIRED");
        }
        assertThat(count("SELECT COUNT(*) FROM pda_session WHERE id=UUID_TO_BIN(?) AND refresh_hash=?",
            tokens.get("sessionId").asText(),com.acrylic.outbound.devices.DeviceCrypto.sha(tokens.get("refreshToken").asText()))).isEqualTo(1);
        save("C03-refresh-user-before-warehouse",evidence);
    }
    private void holdUser(UUID user,AtomicLong connection,CountDownLatch entered,CountDownLatch release) {
        connection.set(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));
        jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",Ids.bytes(user));entered.countDown();
        try { if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("User lock barrier timeout"); }
        catch(InterruptedException error) { Thread.currentThread().interrupt();throw new AssertionError(error); }
    }
    private void captureWait(long blocker,long waiter,Map<String,Object> evidence) {
        await().atMost(Duration.ofSeconds(8)).untilAsserted(()->{
            var waits=jdbc.queryForList("""
                SELECT r.OBJECT_NAME,r.LOCK_MODE,rt.PROCESSLIST_ID waitingConnection,bt.PROCESSLIST_ID blockingConnection
                FROM performance_schema.data_lock_waits w
                INNER JOIN performance_schema.data_locks r ON r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND r.ENGINE=w.ENGINE
                INNER JOIN performance_schema.threads rt ON rt.THREAD_ID=w.REQUESTING_THREAD_ID
                INNER JOIN performance_schema.threads bt ON bt.THREAD_ID=w.BLOCKING_THREAD_ID
                WHERE rt.PROCESSLIST_ID=? AND bt.PROCESSLIST_ID=?
                """,waiter,blocker);
            assertThat(waits).anySatisfy(row->assertThat(row.get("OBJECT_NAME")).isEqualTo("app_user"));evidence.put("waits",waits);
        });
        var locks=jdbc.queryForList("""
            SELECT l.OBJECT_NAME,l.LOCK_TYPE,l.LOCK_MODE,l.LOCK_STATUS,l.LOCK_DATA
            FROM performance_schema.data_locks l INNER JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID
            WHERE t.PROCESSLIST_ID=?
            """,waiter);
        assertThat(locks).noneSatisfy(row->{
            assertThat(row.get("OBJECT_NAME")).isIn("warehouse","device","pda_session");
            assertThat(row.get("LOCK_TYPE")).isEqualTo("RECORD");assertThat(row.get("LOCK_STATUS")).isEqualTo("GRANTED");
        });evidence.put("waitingLocks",locks);
    }
    private void save(String name,Object evidence) throws Exception {
        String directory=System.getenv("L2_LOCK_EVIDENCE");
        if(directory!=null) { Files.createDirectories(Path.of(directory));json.writerWithDefaultPrettyPrinter()
            .writeValue(Path.of(directory,name+"-"+UUID.randomUUID()+".json").toFile(),evidence); }
    }
}
