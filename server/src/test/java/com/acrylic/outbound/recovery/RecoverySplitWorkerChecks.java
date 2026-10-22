package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** File handshakes observe a real scheduler root in a different JVM. */
public final class RecoverySplitWorkerChecks {
    public static void probe(ConfigurableApplicationContext context, Path home) throws Exception {
        var probe = context.getBean(RecoveryWorkerProbe.class);
        try {
            assertThat(probe.entered.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(probe.held.phase()).isEqualTo(RecoveryOperationPhase.SERVING);
            assertThatThrownBy(() -> context.getBean(JdbcTemplate.class).queryForObject(
                "SELECT 1", Integer.class)).hasStackTraceContaining("RECOVERY_OPERATION_REQUIRED");
            Files.writeString(home.resolve("probe-held.marker"), "WORKER_JOB SERVING; rootless refused",
                StandardOpenOption.CREATE_NEW);
            await().atMost(Duration.ofSeconds(15)).until(() -> Files.exists(home.resolve("release-probe.marker")));
        } finally { probe.release.countDown(); }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
            assertThat(probe.held.phase()).isEqualTo(RecoveryOperationPhase.RELEASED));
        assertThat(probe.held.openSubOperations()).isZero();
        drain(context);
        Files.writeString(home.resolve("probe-released.marker"), "RELEASED; no suboperations or roots",
            StandardOpenOption.CREATE_NEW);
    }

    public static void writerBlocked(Path config) throws Exception {
        Path root = Path.of(RecoveryControlHistory.read(config).path("root").asText());
        byte[] before = Files.readAllBytes(root.resolve("current.json"));
        assertThatThrownBy(() -> RecoveryControlLock.lockedWaiting(root, 10000, () -> {
            try (var lease = RecoveryWriterLease.acquireClosed(root)) { return true; }
        })).hasMessage("CONTROL_WRITERS_ACTIVE");
        assertThat(Files.readAllBytes(root.resolve("current.json"))).isEqualTo(before);
    }

    public static void drain(ConfigurableApplicationContext context) throws Exception {
        for (var scheduler : context.getBeansOfType(ThreadPoolTaskScheduler.class).values()) scheduler.shutdown();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(context.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero());
        context.getBean(RecoveryFenceAuthority.class).checkEffect();
    }
}
