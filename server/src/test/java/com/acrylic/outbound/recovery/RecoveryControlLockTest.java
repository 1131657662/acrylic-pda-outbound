package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Design row 13: the control lock is per root, nests the fixed layer order {@code P3 -> P2 -> P5},
 * refuses reverse order and same-root re-entry, and offers a bounded wait so concurrent admissions
 * serialize instead of being refused.
 */
class RecoveryControlLockTest {
    @Test void differentRootsNestInLayerOrderAndTheThreadOwnsBoth() throws Exception {
        var outer = RecoveryControlFixture.create();
        var inner = RecoveryControlFixture.create();
        var observed = new AtomicInteger();

        RecoveryControlLock.locked(RecoveryControlLock.AUTHORITY_LAYER, outer.root, () -> {
            RecoveryControlLock.requireOwner(outer.root);
            return RecoveryControlLock.locked(RecoveryControlLock.STORE_LAYER, inner.root, () -> {
                RecoveryControlLock.requireOwner(outer.root);
                RecoveryControlLock.requireOwner(inner.root);
                observed.set(RecoveryControlLock.heldByCurrentThread().size());
                return null;
            });
        });

        assertThat(observed.get()).as("both roots are held by this thread while nested").isEqualTo(2);
        assertThat(RecoveryControlLock.heldByCurrentThread()).isEmpty();
    }

    @Test void reverseLayerOrderIsRefusedAndNothingIsLeftHeld() throws Exception {
        var store = RecoveryControlFixture.create();
        var authority = RecoveryControlFixture.create();

        assertThatThrownBy(() -> RecoveryControlLock.locked(RecoveryControlLock.STORE_LAYER, store.root, () ->
            RecoveryControlLock.locked(RecoveryControlLock.AUTHORITY_LAYER, authority.root, () -> null)))
            .hasMessage("CONTROL_LOCK_ORDER_VIOLATION");
        assertThat(RecoveryControlLock.heldByCurrentThread()).as("the refused nesting released the outer root")
            .isEmpty();
    }

    @Test void sameRootReentryIsMisuseAndNotContention() throws Exception {
        var fixture = RecoveryControlFixture.create();

        RecoveryControlLock.locked(fixture.root, () -> {
            assertThatThrownBy(() -> RecoveryControlLock.locked(fixture.root, () -> null))
                .as("the immediate form keeps its original refusal").hasMessage("CONTROL_COORDINATOR_BUSY");
            assertThatThrownBy(() -> RecoveryControlLock.lockedWaiting(fixture.root, 500, () -> null))
                .as("the waiting form names the misuse instead of waiting").hasMessage("CONTROL_COORDINATOR_REENTRANT");
            return null;
        });
    }

    @Test void waitingFormBoundsItsOwnTimeoutRange() throws Exception {
        var fixture = RecoveryControlFixture.create();

        assertThatThrownBy(() -> RecoveryControlLock.lockedWaiting(fixture.root, 0, () -> null))
            .hasMessage("CONTROL_LOCK_TIMEOUT_RANGE");
        assertThatThrownBy(() -> RecoveryControlLock.lockedWaiting(fixture.root, 10_001, () -> null))
            .hasMessage("CONTROL_LOCK_TIMEOUT_RANGE");
        assertThat(RecoveryControlLock.lockedWaiting(fixture.root, 1, () -> "taken")).isEqualTo("taken");
    }

    @Test void aWaitingCallerIsAdmittedOnceTheHolderReleases() throws Exception {
        var fixture = RecoveryControlFixture.create();
        var holding = new CountDownLatch(1);
        var releaseHolder = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> {
                try {
                    RecoveryControlLock.locked(fixture.root, () -> {
                        holding.countDown();
                        await(releaseHolder);
                        return null;
                    });
                } catch (Exception error) { throw new IllegalStateException(error); }
            });
            assertThat(holding.await(30, TimeUnit.SECONDS)).isTrue();

            Future<String> waiter = executor.submit(() -> {
                try { return RecoveryControlLock.lockedWaiting(fixture.root, 5_000, () -> "admitted"); }
                catch (java.io.IOException error) { throw new IllegalStateException(error); }
            });
            Thread.sleep(300);
            assertThat(waiter.isDone()).as("the waiter is queued, not refused").isFalse();
            releaseHolder.countDown();
            assertThat(waiter.get(30, TimeUnit.SECONDS)).isEqualTo("admitted");
            holder.get(30, TimeUnit.SECONDS);
        } finally {
            releaseHolder.countDown();
            executor.shutdownNow();
        }
    }

    @Test void waitingCallerStillFailsClosedWhenTheBudgetRunsOut() throws Exception {
        var fixture = RecoveryControlFixture.create();
        var holding = new CountDownLatch(1);
        var releaseHolder = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                try {
                    RecoveryControlLock.locked(fixture.root, () -> {
                        holding.countDown();
                        await(releaseHolder);
                        return null;
                    });
                } catch (Exception error) { throw new IllegalStateException(error); }
            });
            assertThat(holding.await(30, TimeUnit.SECONDS)).isTrue();

            long started = System.nanoTime();
            assertThatThrownBy(() -> RecoveryControlLock.lockedWaiting(fixture.root, 200, () -> null))
                .hasMessage("CONTROL_COORDINATOR_BUSY");
            long waitedMillis = (System.nanoTime() - started) / 1_000_000L;
            assertThat(waitedMillis).as("the wait is bounded by the caller's budget").isLessThan(5_000L);
        } finally {
            releaseHolder.countDown();
            executor.shutdownNow();
        }
    }

    @Test void differentRootsDoNotBlockEachOtherAcrossThreads() throws Exception {
        var first = RecoveryControlFixture.create();
        var second = RecoveryControlFixture.create();
        var firstHeld = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondObserved = new AtomicReference<String>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> {
                try {
                    RecoveryControlLock.locked(first.root, () -> {
                        firstHeld.countDown();
                        await(releaseFirst);
                        return null;
                    });
                } catch (Exception error) { throw new IllegalStateException(error); }
            });
            assertThat(firstHeld.await(30, TimeUnit.SECONDS)).isTrue();
            Future<?> other = executor.submit(() -> {
                try { RecoveryControlLock.locked(second.root, () -> { secondObserved.set("taken"); return null; }); }
                catch (Exception error) { secondObserved.set("failed:" + error); }
            });
            other.get(30, TimeUnit.SECONDS);
            assertThat(secondObserved.get()).as("a different root is not blocked by the first").isEqualTo("taken");
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test void aForeignRootIsNotOwned() throws Exception {
        var fixture = RecoveryControlFixture.create();
        var foreign = RecoveryControlFixture.create();

        RecoveryControlLock.locked(fixture.root, () -> {
            RecoveryControlLock.requireOwner(fixture.root);
            assertThatThrownBy(() -> RecoveryControlLock.requireOwner(foreign.root))
                .hasMessage("CONTROL_COORDINATOR_REQUIRED");
            return null;
        });
        assertThatThrownBy(() -> RecoveryControlLock.requireOwner(fixture.root))
            .as("ownership does not survive the critical section").hasMessage("CONTROL_COORDINATOR_REQUIRED");
        assertThat(Path.of("").toAbsolutePath()).isNotNull();
    }
    private static void await(java.util.concurrent.CountDownLatch latch) throws java.io.IOException {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) throw new java.io.IOException("latch timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException(interrupted);
        }
    }
}