package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.temporary.command.CommandReplay;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A concurrent writer wins between the first read and the write transaction; the second lookup returns its original bytes. */
class LedgerSecondLookupIT extends LedgerRuntimeFixture {
    @MockitoSpyBean CommandReplay replay;

    @Test void secondOriginalKeyLookupWinsAfterInitialMiss() throws Exception {
        var seed=seed("complete");var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");
        var firstMiss=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        doAnswer(call->{
            Object result=call.callRealMethod();
            if(result==null && calls.incrementAndGet()==1) { firstMiss.countDown();release.await(15,TimeUnit.SECONDS); }
            return result;
        }).when(replay).existing(any(),any());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->execute(seed,command));assertThat(firstMiss.await(10,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->execute(seed,command));byte[] winner=second.get(20,TimeUnit.SECONDS);release.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(winner);
            var counts=new LedgerDatabaseFacts(jdbc).counts(seed,"verify-"+command.path("commandId").asText());
            for(String key:java.util.List.of("requests","receipts","events","audit"))assertThat(((Number)counts.get(key)).longValue()).isEqualTo(1);
            assertThat(original(seed,command)).isEqualTo(winner);
        } finally {release.countDown();pool.shutdownNow();pool.awaitTermination(10,TimeUnit.SECONDS);reset(replay);}
    }
}
