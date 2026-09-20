package com.acrylic.outbound.jobs;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class BackgroundJobLifecycleIT {
    @Autowired BackgroundJobStore jobs;
    @Autowired BackgroundJobLeases leases;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private JobFixture f;

    @BeforeEach void fixture() { f = new JobFixture(jobs, leases, jdbc); }
    @AfterEach void cleanup() { f.close(); }

    @Test void duplicateJsonWithDifferentFormattingReturnsOriginalSchedule() {
        var input = new NewBackgroundJob(f.type, f.type, "{\"a\":1,\"b\":2}", 3, Instant.EPOCH);
        var first = jobs.enqueue(input);
        var replay = jobs.enqueue(new NewBackgroundJob(f.type, f.type, "{ \"b\": 2, \"a\": 1 }", 3, Instant.now()));
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.availableAt()).isEqualTo(Instant.EPOCH);
    }

    @Test void conflictingDedupeDoesNotChangeOriginalTask() {
        var first = jobs.enqueue(new NewBackgroundJob(f.type, f.type, "{\"version\":1}", 3, Instant.EPOCH));
        assertThatThrownBy(() -> jobs.enqueue(new NewBackgroundJob(f.type, f.type, "{\"version\":2}", 3, Instant.EPOCH)))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        assertThat(f.current(first)).isEqualTo(first);
    }

    @Test void producerRollbackDoesNotLeaveAnExecutableTask() {
        var input = f.input(3);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jobs.enqueue(input);
            status.setRollbackOnly();
        });
        assertThat(jobs.findByDedupeKey(input.dedupeKey())).isEmpty();
    }

    @Test void futureTaskCannotBeClaimed() {
        var job = jobs.enqueue(new NewBackgroundJob(f.type, f.type, "{}", 3, Instant.now().plusSeconds(3600)));
        assertThat(leases.claim("worker", Set.of(f.type), Duration.ofMinutes(1))).isEmpty();
        assertThat(f.current(job).attemptCount()).isZero();
    }

    @Test void expiredLeaseRejectsAllWritesEvenBeforeRecovery() {
        f.enqueue(3); var old = f.claim(); f.expire(old);
        var published = new AtomicBoolean();
        assertThat(leases.renew(old, Duration.ofMinutes(1))).isFalse();
        assertThat(leases.complete(old, () -> published.set(true))).isFalse();
        assertThat(leases.fail(old, new JobFailure("LATE_FAILURE", true))).isEqualTo(BackgroundJobLeases.Outcome.STALE);
        assertThat(published).isFalse();
    }

    @Test void expiredLeaseBacksOffAndFencesPreviousOwnerAfterReclaim() {
        f.enqueue(3); var old = f.claim(); f.expire(old);
        assertThat(leases.recoverExpired(Set.of(f.type))).isEqualTo(1);
        var retry = f.current(old);
        assertThat(retry.availableAt()).isEqualTo(retry.updatedAt().plusSeconds(5));
        assertThat(leases.claim("next", Set.of(f.type), Duration.ofMinutes(1))).isEmpty();
        f.ready(old); var current = f.claim();
        assertThat(current.attemptCount()).isEqualTo(2);
        assertThat(current.leaseToken()).isNotEqualTo(old.leaseToken());
        assertThat(leases.renew(old, Duration.ofMinutes(1))).isFalse();
        assertThat(leases.complete(old, () -> fail("Stale publication must not run"))).isFalse();
        assertThat(leases.fail(old, new JobFailure("LATE_FAILURE", true))).isEqualTo(BackgroundJobLeases.Outcome.STALE);
        assertThat(leases.complete(current, () -> {})).isTrue();
    }

    @Test void retryBackoffIncreasesAndMaxAttemptsStopsExecution() {
        var job = f.enqueue(3);
        for (int attempt = 1; attempt <= 2; attempt++) {
            var claim = f.claim();
            assertThat(leases.fail(claim, new JobFailure("TEMPORARY_FAILURE", true)))
                .isEqualTo(BackgroundJobLeases.Outcome.RETRY_SCHEDULED);
            var retry = f.current(job);
            assertThat(retry.availableAt()).isEqualTo(retry.updatedAt().plusSeconds(attempt == 1 ? 5 : 10));
            assertThat(leases.claim("worker", Set.of(f.type), Duration.ofMinutes(1))).isEmpty();
            f.ready(job);
        }
        assertThat(leases.fail(f.claim(), new JobFailure("TEMPORARY_FAILURE", true))).isEqualTo(BackgroundJobLeases.Outcome.FAILED);
        assertThat(f.current(job).attemptCount()).isEqualTo(3);
        assertThat(leases.claim("worker", Set.of(f.type), Duration.ofMinutes(1))).isEmpty();
    }

    @Test void finalExpiredAttemptBecomesFailed() {
        var job = f.enqueue(1); var claimed = f.claim(); f.expire(claimed);
        assertThat(leases.recoverExpired(Set.of(f.type))).isEqualTo(1);
        var failed = f.current(job);
        assertThat(failed.status()).isEqualTo(BackgroundJob.Status.FAILED);
        assertThat(failed.lastError()).isEqualTo("LEASE_EXPIRED");
        assertThat(failed.leaseToken()).isNull();
    }

    @Test void liveOwnerCanRenewUsingDatabaseClock() {
        f.enqueue(2); var claim = f.claim();
        assertThat(leases.renew(claim, Duration.ofMinutes(2))).isTrue();
        var renewed = f.current(claim);
        assertThat(renewed.leaseUntil()).isEqualTo(renewed.updatedAt().plusSeconds(120));
        assertThat(renewed.leaseToken()).isEqualTo(claim.leaseToken());
        assertThat(leases.complete(claim, () -> {})).isTrue();
    }

    @Test void invalidJsonCannotEnterQueue() {
        for (String payload : List.of("null", "[]", "{bad", "{} {}", "{\"x\":1,\"x\":2}")) {
            assertThatThrownBy(() -> jobs.enqueue(new NewBackgroundJob(f.type, f.type, payload, 3, Instant.EPOCH)))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(jobs.findByDedupeKey(f.type)).isEmpty();
    }
}
