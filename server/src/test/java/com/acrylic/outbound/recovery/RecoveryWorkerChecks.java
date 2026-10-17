package com.acrylic.outbound.recovery;

import com.acrylic.outbound.jobs.BackgroundWorkerProcess;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real scheduler root retention, completion rollback, retirement and stale-authority refusal. */
public final class RecoveryWorkerChecks {
    public static void verify(ConfigurableApplicationContext context, JdbcTemplate raw,
            RecoveryAdmissionEntryFixture authority, Path evidence) throws Exception {
        var registry = context.getBean(RecoveryOperationRegistry.class);
        var probe = context.getBean(RecoveryWorkerProbe.class);
        raw.execute("CREATE TABLE recovery_worker_probe(id INT PRIMARY KEY)");
        String id = UUID.randomUUID().toString();
        raw.update("INSERT INTO background_job(id,job_type,dedupe_key,status,payload,max_attempts,available_at,created_at,updated_at,recovery_epoch) "
            + "VALUES(UUID_TO_BIN(?),?,?,'PENDING','{}',1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),1)", id, RecoveryWorkerProbe.TYPE, id);
        try {
            assertThat(probe.entered.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(probe.held.phase()).isEqualTo(RecoveryOperationPhase.SERVING);
            authority.assertIndependentWriterBlocked();
            assertThatThrownBy(() -> context.getBean(JdbcTemplate.class).queryForObject("SELECT 1", Integer.class))
                .hasStackTraceContaining("RECOVERY_OPERATION_REQUIRED");
        } finally { probe.release.countDown(); }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(status(raw, id)).isEqualTo("FAILED"));
        context.getBean(ThreadPoolTaskScheduler.class).shutdown();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(registry.liveRoots()).isZero());
        assertThat(probe.held.phase()).isEqualTo(RecoveryOperationPhase.RELEASED);
        assertThat(probe.held.openSubOperations()).isZero();
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM recovery_worker_probe", Integer.class)).isZero();
        stale(context, raw, authority, evidence, id);
    }

    private static void stale(ConfigurableApplicationContext context, JdbcTemplate raw,
            RecoveryAdmissionEntryFixture authority, Path evidence, String id) throws Exception {
        var before = raw.queryForList("SELECT HEX(id) id,status,attempt_count,payload FROM background_job ORDER BY id");
        authority.advanceWorkerAuthority();
        var entry = context.getBean(BackgroundWorkerProcess.class);
        entry.poll(); entry.enqueueReviewReminderSweep();
        assertThat(raw.queryForList("SELECT HEX(id) id,status,attempt_count,payload FROM background_job ORDER BY id")).isEqualTo(before);
        assertThat(context.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero();
        Files.writeString(evidence.resolve("worker-checks.json"), "{\"probeJob\":\"" + id
            + "\",\"kind\":\"WORKER_JOB\",\"writerBlocked\":true,\"rootlessRefused\":true,"
            + "\"lastSqlRolledBack\":true,\"rootReleased\":true,\"staleUnchanged\":true}", StandardOpenOption.CREATE_NEW);
    }

    private static String status(JdbcTemplate raw, String id) {
        return raw.queryForObject("SELECT status FROM background_job WHERE id=UUID_TO_BIN(?)", String.class, id);
    }
}
