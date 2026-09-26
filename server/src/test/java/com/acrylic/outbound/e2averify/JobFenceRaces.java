package com.acrylic.outbound.e2averify;

import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

final class JobFenceRaces {
    private final JobFenceFixture f;
    JobFenceRaces(JobFenceFixture f) { this.f = f; }

    void verify() throws Exception {
        writerFirst(release -> f.tx().executeWithoutResult(status -> { f.enqueue(); release.accept(null); }));
        f.enqueue(); var job = f.claim();
        writerFirst(release -> f.leases.complete(job, () -> release.accept(null)));
        recoveryFirst();
    }

    private void writerFirst(Consumer<Consumer<Void>> writer) throws Exception {
        var ready = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var writing = pool.submit(() -> writer.accept(ignored -> { ready.countDown(); await(release); }));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                var switching = pool.submit(() -> f.state(f.state.current().epoch(), "RECOVERY_PENDING"));
                observeStateWait(); assertThat(switching.isDone()).isFalse(); release.countDown();
                writing.get(10, TimeUnit.SECONDS); switching.get(10, TimeUnit.SECONDS);
                assertThat(f.state.current().mode()).isEqualTo("RECOVERY_PENDING");
            } finally { release.countDown(); }
        } finally { f.state(f.state.current().epoch(), "NORMAL"); }
    }

    private void recoveryFirst() throws Exception {
        f.enqueue(); var job = f.claim(); var ready = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var switcher = pool.submit(() -> f.tx().executeWithoutResult(status -> {
                f.state(f.state.current().epoch(), "RECOVERY_PENDING"); ready.countDown(); await(release);
            }));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                var writing = pool.submit(() -> f.leases.complete(job, () -> fail("switch won; must not publish")));
                observeStateWait(); assertThat(writing.isDone()).isFalse(); release.countDown();
                switcher.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> writing.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(com.acrylic.outbound.common.BusinessException.class);
                assertThat(f.current(job)).isEqualTo(job);
            } finally { release.countDown(); }
        } finally { f.state(f.state.current().epoch(), "NORMAL"); }
    }

    private void observeStateWait() throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            int waits = f.jdbc.queryForObject("""
                SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='server_state'
                """, Integer.class);
            if (waits > 0) return;
            Thread.sleep(20);
        } while (System.nanoTime() < end);
        fail("Expected a real server_state lock wait");
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(12, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
}
