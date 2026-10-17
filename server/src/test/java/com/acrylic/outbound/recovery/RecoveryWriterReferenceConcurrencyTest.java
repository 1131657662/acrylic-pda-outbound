package com.acrylic.outbound.recovery;

import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Actual reference churn must neither release an active root nor leak its final lock. */
class RecoveryWriterReferenceConcurrencyTest {
    @Test void concurrentBorrowAndClosePreserveTheOwnersLockUntilItsOwnClose() throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();
        var owner = RecoveryControlLock.locked(control.root, () -> RecoveryWriterLease.acquire(control.root));
        try (var workers = Executors.newFixedThreadPool(8)) {
            var ready = new CountDownLatch(8); var start = new CountDownLatch(1);
            var results = new ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++) results.add(workers.submit(() -> churn(owner, ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (var result : results) result.get(20, TimeUnit.SECONDS);
            assertThat(owner.confirmed()).isTrue();
            assertThatThrownBy(() -> RecoveryControlLock.locked(control.root,
                () -> RecoveryWriterLease.acquireClosed(control.root))).hasMessage("CONTROL_WRITERS_ACTIVE");
        } finally { owner.close(); }
        try (var next = RecoveryControlLock.locked(control.root, () -> RecoveryWriterLease.acquireClosed(control.root))) {
            assertThat(next.confirmed()).isTrue();
        }
    }

    private static void churn(RecoveryWriterLease owner, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 1000; i++) {
                try (var borrowed = owner.reference()) { assertThat(borrowed.confirmed()).isTrue(); Thread.yield(); }
            }
        } catch (Exception error) { throw new AssertionError(error); }
    }
}
