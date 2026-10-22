package com.acrylic.outbound.recovery;

import com.acrylic.outbound.jobs.BackgroundWorkerProcess;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Handshakes for a live restored worker; explicit production calls must reject stale authority. */
public final class RecoveryRestoredStaleChecks {
    public static void serve(ConfigurableApplicationContext context, Path home, boolean worker) throws Exception {
        String expected = home.getFileName().toString().equals("restored-p5-fault")
            ? RecoveryRestoredAuthorityFault.expected() : "CONTROL_INSTANCE_STALE";
        if (worker) {
            waitFor(home.resolve("pause-worker.marker"));
            drain(context);
            Files.writeString(home.resolve("worker-paused.marker"), "scheduler drained; JVM remains live",
                StandardOpenOption.CREATE_NEW);
            waitFor(home.resolve("check-stale.marker"));
            refuse(context, home, expected);
        }
        waitFor(home.resolve((worker ? "worker" : "api") + "-stop.marker"));
        drain(context);
    }

    private static void refuse(ConfigurableApplicationContext context, Path home, String expected) throws Exception {
        var operations = context.getBean(RecoveryOperationRegistry.class);
        var worker = context.getBean(BackgroundWorkerProcess.class);
        worker.poll(); worker.enqueueReviewReminderSweep();
        assertThat(operations.liveRoots()).isZero();
        var token = new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.WORKER_JOB,
            operations.startup().installationId(), operations.startup().recordSha256(), null);
        assertThatThrownBy(() -> operations.admit(token)).isInstanceOf(RecoveryControlException.class)
            .hasMessage(expected);
        var result = SubmissionJson.object().put("pid", ProcessHandle.current().pid())
            .put("productionPollCalled", true).put("productionReminderCalled", true)
            .put("admissionCode", expected).put("liveRoots", operations.liveRoots());
        Files.writeString(home.resolve("worker-stale.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private static void drain(ConfigurableApplicationContext context) {
        for (var scheduler : context.getBeansOfType(ThreadPoolTaskScheduler.class).values()) scheduler.shutdown();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
            assertThat(context.getBean(RecoveryOperationRegistry.class).liveRoots()).isZero());
    }

    private static void waitFor(Path marker) {
        await().atMost(Duration.ofSeconds(240)).until(() -> Files.isRegularFile(marker));
    }
}
