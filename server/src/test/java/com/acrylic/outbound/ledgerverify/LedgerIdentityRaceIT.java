package com.acrylic.outbound.ledgerverify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.temporary.command.CommandLocks;
import com.acrylic.outbound.users.UserStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LedgerIdentityRaceIT extends LedgerRuntimeFixture {
    @MockitoSpyBean UserStore users;
    @MockitoSpyBean PdaSessions sessions;
    @MockitoSpyBean CommandLocks locks;

    @ParameterizedTest @ValueSource(strings={"roles","disabled","warehouse","session"})
    void committedIdentityMutationWinsBeforeBlockedCommandCanWrite(String kind) throws Exception {
        var seed=seed("complete");var mutation=seed.identityMutation(kind);var command=new LedgerCommandData(jdbc).command(seed,"NORMAL_CONFIRM");
        var probe=new LedgerLockEvidence(jdbc);var blocker=new AtomicLong();var waiter=new AtomicLong();
        var held=new CountDownLatch(1);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        org.mockito.stubbing.Answer<Object> block=call->{
            Object result=call.callRealMethod();blocker.set(probe.transaction("mutation"));held.countDown();
            if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Mutation coordinator timed out");return result;
        };
        if(kind.equals("session"))doAnswer(block).when(sessions).revoke(any());else doAnswer(block).when(users).update(any(),any());
        doAnswer(call->{waiter.set(probe.transaction("command"));entered.countDown();return call.callRealMethod();}).when(locks).identity(any(),any());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(mutation);assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();
            var b=pool.submit(()->execute(seed,command));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();probe.waiting(blocker.get(),waiter.get());
            release.countDown();a.get(15,TimeUnit.SECONDS);
            assertThatThrownBy(()->b.get(15,TimeUnit.SECONDS)).isInstanceOfSatisfying(ExecutionException.class,
                e->assertThat(e.getCause()).isInstanceOfSatisfying(BusinessException.class,x->assertThat(x.code()).isEqualTo("AUTH_EXPIRED")));
            var counts=new LedgerDatabaseFacts(jdbc).counts(seed,"verify-"+command.path("commandId").asText());
            for(Object count:counts.values())assertThat(((Number)count).longValue()).isZero();probe.save("identity-"+kind,counts);
        } finally {release.countDown();pool.shutdownNow();pool.awaitTermination(10,TimeUnit.SECONDS);reset(users,sessions,locks);}
    }
}
