package com.acrylic.outbound.jobs;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class BackgroundJobWorkerIT {
    @Autowired BackgroundJobStore jobs;
    @Autowired BackgroundJobLeases leases;
    @Autowired JdbcTemplate jdbc;
    private JobFixture f;

    @BeforeEach void fixture() { f = new JobFixture(jobs, leases, jdbc); }
    @AfterEach void cleanup() { f.close(); }

    @Test void preparesOutsideTransactionAndPublishesWithSuccess() {
        var job = f.enqueue(3); var result = f.input(1);
        var worker = f.worker(execution -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(f.current(job).status()).isEqualTo(BackgroundJob.Status.RUNNING);
            execution.renew();
            return () -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                jobs.enqueue(result);
            };
        });
        assertThat(worker.runOnce("worker").orElseThrow().outcome()).isEqualTo("SUCCEEDED");
        assertThat(f.current(job).status()).isEqualTo(BackgroundJob.Status.SUCCEEDED);
        assertThat(jobs.findByDedupeKey(result.dedupeKey())).isPresent();
    }

    @Test void failedPublicationRollsBackResultAndSuccessBeforeRetry() {
        var job = f.enqueue(3); var result = f.input(1);
        var worker = f.worker(execution -> () -> {
            jobs.enqueue(result); throw new IllegalStateException("Sensitive error text must not be stored");
        });
        assertThat(worker.runOnce("worker").orElseThrow().outcome()).isEqualTo("RETRY_SCHEDULED");
        assertThat(jobs.findByDedupeKey(result.dedupeKey())).isEmpty();
        assertThat(f.current(job).status()).isEqualTo(BackgroundJob.Status.RETRY);
        assertThat(f.current(job).lastError()).isEqualTo("HANDLER_FAILED");
    }

    @Test void permanentFailureStopsWithoutExhaustingAllAttempts() {
        var job = f.enqueue(5);
        var worker = f.worker(execution -> { throw new JobFailure("SNAPSHOT_INVALID", false); });
        assertThat(worker.runOnce("worker").orElseThrow().outcome()).isEqualTo("FAILED");
        assertThat(f.current(job).attemptCount()).isEqualTo(1);
        assertThat(f.current(job).lastError()).isEqualTo("SNAPSHOT_INVALID");
    }

    @Test void retryUsesSameJobPayloadAndNewLease() {
        var job = f.enqueue(3);
        var failed = f.worker(execution -> { throw new JobFailure("STORAGE_UNAVAILABLE", true); });
        assertThat(failed.runOnce("old-worker").orElseThrow().outcome()).isEqualTo("RETRY_SCHEDULED");
        f.ready(job);
        var restarted = f.worker(execution -> {
            assertThat(execution.job().id()).isEqualTo(job.id());
            assertThat(execution.job().payload()).isEqualTo(job.payload());
            assertThat(execution.job().attemptCount()).isEqualTo(2);
            return () -> {};
        });
        assertThat(restarted.runOnce("restarted-worker").orElseThrow().outcome()).isEqualTo("SUCCEEDED");
        assertThat(restarted.runOnce("restarted-worker")).isEmpty();
    }

    @Test void expiredAttemptCannotPublishAfterAnotherWorkerClaims() {
        var job = f.enqueue(3); var result = f.input(1);
        var worker = f.worker(execution -> {
            f.expire(execution.job()); leases.recoverExpired(Set.of(f.type)); f.ready(job);
            var current = leases.claim("new-owner", Set.of(f.type), Duration.ofMinutes(1)).orElseThrow();
            assertThat(current.leaseToken()).isNotEqualTo(execution.job().leaseToken());
            return () -> jobs.enqueue(result);
        });
        assertThat(worker.runOnce("old-owner").orElseThrow().outcome()).isEqualTo("STALE");
        assertThat(jobs.findByDedupeKey(result.dedupeKey())).isEmpty();
        assertThat(f.current(job).leaseOwner()).isEqualTo("new-owner");
    }

    @Test void unsupportedTaskRemainsPendingWithoutConsumingAttempts() {
        var job = f.enqueue(3);
        var worker = new BackgroundJobWorker(leases, List.of());
        assertThat(worker.runOnce("worker")).isEmpty();
        assertThat(f.current(job)).isEqualTo(job);
    }

    @Test void interruptedHandlerRestoresInterruptAndRetainsRetry() {
        var job = f.enqueue(3);
        var worker = f.worker(execution -> { throw new InterruptedException(); });
        try {
            assertThat(worker.runOnce("worker").orElseThrow().outcome()).isEqualTo("RETRY_SCHEDULED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        assertThat(f.current(job).lastError()).isEqualTo("WORKER_INTERRUPTED");
    }

    @Test void missingResultCallbackCannotProduceFalseSuccess() {
        var job = f.enqueue(2);
        assertThat(f.worker(execution -> null).runOnce("worker").orElseThrow().outcome()).isEqualTo("RETRY_SCHEDULED");
        assertThat(f.current(job).status()).isEqualTo(BackgroundJob.Status.RETRY);
    }
}
