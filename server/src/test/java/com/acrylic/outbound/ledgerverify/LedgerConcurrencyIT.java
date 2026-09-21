package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.temporary.command.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LedgerConcurrencyIT extends LedgerRuntimeFixture {
    @MockitoSpyBean CommandStore store;
    @MockitoSpyBean CommandLocks locks;

    @ParameterizedTest @ValueSource(strings={"same-key","changed-body","same-version"})
    void realIdentityWaitSerializesCompetingCommands(String variant) throws Exception {
        var seed=seed("complete");var first=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");
        var second=first.deepCopy();
        if(variant.equals("changed-body"))second.put("reason","同键不同正文");
        if(variant.equals("same-version"))second.put("commandId",UUID.randomUUID().toString());
        var probe=new LedgerLockEvidence(jdbc);var blocker=new AtomicLong();var waiter=new AtomicLong();
        var reserved=new CountDownLatch(1);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        instrument(probe,blocker,waiter,entered,reserved,release);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{Thread.currentThread().setName("ledger-holder");return execute(seed,first);});
            assertThat(reserved.await(10,TimeUnit.SECONDS)).isTrue();
            var b=pool.submit(()->{Thread.currentThread().setName("ledger-waiter");return execute(seed,second);});
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();probe.waiting(blocker.get(),waiter.get());
            release.countDown();byte[] bytes=a.get(15,TimeUnit.SECONDS);
            if(variant.equals("same-key"))assertThat(b.get(15,TimeUnit.SECONDS)).isEqualTo(bytes);
            else assertThatThrownBy(()->b.get(15,TimeUnit.SECONDS)).isInstanceOfSatisfying(ExecutionException.class,
                e->assertThat(e.getCause()).isInstanceOfSatisfying(BusinessException.class,x->assertThat(x.code())
                    .isEqualTo(variant.equals("changed-body")?"IDEMPOTENCY_CONTENT_MISMATCH":"COMMAND_CONTEXT_CHANGED")));
            var counts=new LedgerDatabaseFacts(jdbc).counts(seed,"verify-"+first.path("commandId").asText());
            for(String key:List.of("requests","receipts","events","audit"))assertThat(((Number)counts.get(key)).longValue()).isEqualTo(1);
            assertThat(((Number)counts.get("decisionVersion")).longValue()).isEqualTo(1);
            assertThat(original(seed,first)).isEqualTo(bytes);probe.save("concurrent-"+variant,counts);
        } finally { release.countDown();pool.shutdownNow();pool.awaitTermination(10,TimeUnit.SECONDS);reset(store,locks); }
    }

    private void instrument(LedgerLockEvidence probe,AtomicLong blocker,AtomicLong waiter,
        CountDownLatch entered,CountDownLatch reserved,CountDownLatch release) {
        doAnswer(call->{
            boolean holder=Thread.currentThread().getName().equals("ledger-holder");
            long id=probe.transaction(holder?"holder":"waiter");
            if(holder)blocker.set(id);else {waiter.set(id);entered.countDown();}
            return call.callRealMethod();
        }).when(locks).identity(any(),any());
        doAnswer(call->{
            Object result=call.callRealMethod();
            if(Thread.currentThread().getName().equals("ledger-holder")) {
                reserved.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Coordinator timed out");
            }
            return result;
        }).when(store).reserve(any());
    }
}
