package com.acrylic.outbound.jobs;

import com.acrylic.outbound.common.Ids;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class BackgroundJobConcurrencyIT {
    @Autowired BackgroundJobStore jobs;
    @Autowired BackgroundJobLeases leases;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private JobFixture f;

    @BeforeEach void fixture() { f = new JobFixture(jobs, leases, jdbc); }
    @AfterEach void cleanup() { f.close(); }

    @Test void twoWorkersCannotClaimSameJob() throws Exception {
        var job = f.enqueue(3); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Optional<BackgroundJob>> action = () -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return leases.claim(UUID.randomUUID().toString(), Set.of(f.type), Duration.ofMinutes(1));
            };
            var one = pool.submit(action); var two = pool.submit(action); start.countDown();
            var claimed = List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS))
                .stream().flatMap(Optional::stream).toList();
            assertThat(claimed).hasSize(1);
            assertThat(claimed.getFirst().id()).isEqualTo(job.id());
            assertThat(f.current(job).attemptCount()).isEqualTo(1);
        }
    }

    @Test void simultaneousEnqueueReturnsOneDurableTask() throws Exception {
        var input = f.input(3); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<BackgroundJob> action = () -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue(); return jobs.enqueue(input);
            };
            var one = pool.submit(action); var two = pool.submit(action); start.countDown();
            assertThat(one.get(10, TimeUnit.SECONDS).id()).isEqualTo(two.get(10, TimeUnit.SECONDS).id());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_job WHERE job_type=?", Integer.class, f.type)).isEqualTo(1);
        }
    }

    @Test void lockedTaskDoesNotBlockOtherTask() throws Exception {
        var locked = f.enqueue(3); var other = f.enqueue(3);
        var ready = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var holding = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT attempt_count FROM background_job WHERE id=? FOR UPDATE", Integer.class, Ids.bytes(locked.id()));
                ready.countDown(); await(release);
            }));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(f.claim().id()).isEqualTo(other.id());
                release.countDown(); holding.get(10, TimeUnit.SECONDS);
                assertThat(f.claim().id()).isEqualTo(locked.id());
            } finally { release.countDown(); }
        }
    }

    @Test void completionAndFailureHaveOnlyOneWinner() throws Exception {
        f.enqueue(3); var claim = f.claim(); var result = f.input(1); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var completion = pool.submit(() -> { await(start); return leases.complete(claim, () -> jobs.enqueue(result)); });
            var failure = pool.submit(() -> { await(start); return leases.fail(claim, new JobFailure("TRANSIENT", true)); });
            start.countDown();
            boolean success = completion.get(10, TimeUnit.SECONDS);
            var failed = failure.get(10, TimeUnit.SECONDS);
            assertThat(f.current(claim).status()).isEqualTo(success ? BackgroundJob.Status.SUCCEEDED : BackgroundJob.Status.RETRY);
            assertThat(jobs.findByDedupeKey(result.dedupeKey()).isPresent()).isEqualTo(success);
            assertThat(failed).isEqualTo(success ? BackgroundJobLeases.Outcome.STALE : BackgroundJobLeases.Outcome.RETRY_SCHEDULED);
        }
    }

    @Test void simultaneousRecoveriesDoNotResetAttemptOrDuplicateRetry() throws Exception {
        var job = f.enqueue(3); f.expire(f.claim()); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> action = () -> { await(start); return leases.recoverExpired(Set.of(f.type)); };
            var one = pool.submit(action); var two = pool.submit(action); start.countDown();
            assertThat(one.get(10, TimeUnit.SECONDS) + two.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(f.current(job).attemptCount()).isEqualTo(1);
            assertThat(f.current(job).status()).isEqualTo(BackgroundJob.Status.RETRY);
        }
    }

    private void await(CountDownLatch signal) {
        try { assertThat(signal.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
}
