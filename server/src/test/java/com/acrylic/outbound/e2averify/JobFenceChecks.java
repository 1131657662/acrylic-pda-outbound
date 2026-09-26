package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.jobs.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

final class JobFenceChecks {
    private final JobFenceFixture f;
    JobFenceChecks(JobFenceFixture f) { this.f = f; }

    void verify() {
        assertThatThrownBy(() -> f.state.lockCurrentWritable()).isInstanceOf(IllegalStateException.class);
        legacy(); currentLifecycle(); closedMode(); forgedEpoch(); rollback();
    }

    private void legacy() {
        assertThat(f.leases.claim("worker", Set.of("LEGACY.FENCE"), Duration.ofMinutes(1))).isEmpty();
        assertThat(f.leases.recoverExpired(Set.of("LEGACY.FENCE"))).isZero();
        var original = f.jobs.findByDedupeKey("legacy-fence:RUNNING").orElseThrow();
        assertThat(f.leases.renew(original, Duration.ofMinutes(1))).isFalse();
        assertThat(f.leases.complete(original, () -> fail("unknown origin must not publish"))).isFalse();
        assertThat(f.leases.fail(original, new JobFailure("RETRY", true))).isEqualTo(BackgroundJobLeases.Outcome.STALE);
        assertThat(f.current(original)).isEqualTo(original);
        assertThatThrownBy(() -> f.jobs.enqueue(new NewBackgroundJob("LEGACY.FENCE", "legacy-fence:PENDING",
            "{\"original\":\"retain\"}", 3, Instant.EPOCH)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("JOB_RECOVERY_EPOCH_MISMATCH"));
    }

    private void currentLifecycle() {
        var input = f.input(); var pending = f.jobs.enqueue(input);
        assertThat(pending.recoveryEpoch()).isEqualTo(f.state.current().epoch());
        assertThat(f.jobs.enqueue(input)).isEqualTo(pending);
        var claim = f.claim(); assertThat(f.leases.renew(claim, Duration.ofMinutes(1))).isTrue();
        assertThat(f.leases.fail(claim, new JobFailure("TRANSIENT", true))).isEqualTo(BackgroundJobLeases.Outcome.RETRY_SCHEDULED);
        f.jdbc.update("UPDATE background_job SET available_at='2026-09-20' WHERE id=?", Ids.bytes(claim.id()));
        var retry = f.claim(); var published = f.input();
        assertThat(f.leases.complete(retry, () -> f.jobs.enqueue(published))).isTrue();
        assertThat(f.current(retry).status()).isEqualTo(BackgroundJob.Status.SUCCEEDED);
        assertThat(f.jobs.findByDedupeKey(published.dedupeKey())).isPresent();
        assertThat(f.leases.complete(retry, () -> fail("duplicate callback"))).isFalse();
        var result = f.claim(); assertThat(f.leases.complete(result, () -> {})).isTrue();
    }

    private void closedMode() {
        f.enqueue(); var claim = f.claim(); String epoch = f.state.current().epoch();
        f.state(epoch, "RECOVERY_PENDING");
        try {
            for (Runnable action : List.<Runnable>of(() -> f.enqueue(),
                    () -> f.leases.claim("worker", Set.of(f.type), Duration.ofMinutes(1)),
                    () -> f.leases.recoverExpired(Set.of(f.type)), () -> f.leases.renew(claim, Duration.ofMinutes(1)),
                    () -> f.leases.fail(claim, new JobFailure("TRANSIENT", true)),
                    () -> f.leases.complete(claim, () -> fail("recovery mode callback")))) {
                assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.code()).isEqualTo("SERVER_RECOVERY_REQUIRED"));
            }
            assertThat(f.current(claim)).isEqualTo(claim);
        } finally { f.state(epoch, "NORMAL"); }
        assertThat(f.leases.complete(claim, () -> {})).isTrue();
    }

    void oldEpoch() {
        f.enqueue(); var claim = f.claim(); f.expire(claim); var preserved = f.current(claim);
        var pending = f.enqueue(); String next = Long.toString(Long.parseLong(f.state.current().epoch()) + 1);
        f.state(next, "NORMAL");
        for (Runnable action : List.<Runnable>of(() -> f.enqueue(), () -> f.claim(),
                () -> f.leases.renew(claim, Duration.ofMinutes(1)),
                () -> f.leases.complete(claim, () -> fail("old epoch callback")),
                () -> f.leases.fail(claim, new JobFailure("LATE", true)),
                () -> f.leases.recoverExpired(Set.of(f.type)))) {
            assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("SERVER_RECOVERY_EPOCH_MISMATCH"));
        }
        assertThat(f.current(claim)).isEqualTo(preserved); assertThat(f.current(pending)).isEqualTo(pending);
    }

    void restartedOldJob(BackgroundJob job) {
        assertThat(f.leases.renew(job, Duration.ofMinutes(1))).isFalse();
        assertThat(f.leases.complete(job, () -> fail("old job after restart"))).isFalse();
        assertThat(f.leases.fail(job, new JobFailure("LATE", true))).isEqualTo(BackgroundJobLeases.Outcome.STALE);
        assertThat(f.leases.recoverExpired(Set.of(job.jobType()))).isZero();
        assertThat(f.leases.claim("worker", Set.of(job.jobType()), Duration.ofMinutes(1))).isEmpty();
        var forged = JobFenceFixture.withEpoch(job, f.state.current().epoch());
        assertThat(f.leases.complete(forged, () -> fail("forged epoch"))).isFalse();
        assertThat(f.current(job)).isEqualTo(job);
    }

    private void forgedEpoch() {
        var old = f.jobs.findByDedupeKey("legacy-fence:RUNNING").orElseThrow();
        var forged = JobFenceFixture.withEpoch(old, f.state.current().epoch());
        assertThat(f.leases.renew(forged, Duration.ofMinutes(1))).isFalse();
        assertThat(f.leases.complete(forged, () -> fail("forged DTO callback"))).isFalse();
        assertThat(f.leases.fail(forged, new JobFailure("LATE", true))).isEqualTo(BackgroundJobLeases.Outcome.STALE);
        assertThat(f.current(old)).isEqualTo(old);
    }

    private void rollback() {
        f.enqueue(); var claim = f.claim(); var result = f.input();
        assertThatThrownBy(() -> f.leases.complete(claim, () -> {
            f.jobs.enqueue(result); throw new IllegalStateException("synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(f.current(claim)).isEqualTo(claim);
        assertThat(f.jobs.findByDedupeKey(result.dedupeKey())).isEmpty();
        assertThat(f.leases.complete(claim, () -> {})).isTrue();
    }
}
