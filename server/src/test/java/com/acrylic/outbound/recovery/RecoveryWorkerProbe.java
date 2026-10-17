package com.acrylic.outbound.recovery;

import com.acrylic.outbound.jobs.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Explicit test handler: observes production scheduler admission without supplying its root. */
public final class RecoveryWorkerProbe implements BackgroundJobHandler {
    public static final String TYPE = "RECOVERY_WORKER_PROBE";
    final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    private final RecoveryOperationRegistry operations;
    private final JdbcTemplate jdbc;
    volatile RecoveryOperationLease held;

    RecoveryWorkerProbe(RecoveryOperationRegistry operations, JdbcTemplate jdbc) {
        this.operations = operations; this.jdbc = jdbc;
    }

    public String jobType() { return TYPE; }

    public Runnable execute(BackgroundJobExecution execution) throws Exception {
        var token = operations.bound(Thread.currentThread());
        assertThat(token.kind()).isEqualTo(RecoveryOperationKind.WORKER_JOB);
        held = operations.live(token);
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        entered.countDown();
        if (!release.await(30, TimeUnit.SECONDS)) throw new JobFailure("PROBE_TIMEOUT", false);
        execution.renew();
        return () -> {
            jdbc.update("INSERT INTO recovery_worker_probe VALUES(1)");
            // Deliberately fail the final statement in the real completion transaction.
            jdbc.update("INSERT INTO recovery_worker_probe VALUES(1)");
        };
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {
        @Bean RecoveryWorkerProbe recoveryWorkerProbe(RecoveryOperationRegistry operations, JdbcTemplate jdbc) {
            return new RecoveryWorkerProbe(operations, jdbc);
        }
    }
}
