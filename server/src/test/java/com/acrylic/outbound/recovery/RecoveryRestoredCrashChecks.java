package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** One disposable product worker halts with a real outstanding P5 operation; API remains alive. */
public final class RecoveryRestoredCrashChecks {
    public static void serve(ConfigurableApplicationContext context, Path home, boolean worker) throws Exception {
        if (worker) crashWorker(context, home);
        else observeApi(context, home);
    }

    private static void crashWorker(ConfigurableApplicationContext context, Path home) throws Exception {
        waitFor(home.resolve("pause-worker.marker"));
        RecoverySplitWorkerChecks.drain(context);
        Files.writeString(home.resolve("worker-paused.marker"), "scheduler drained; JVM live", StandardOpenOption.CREATE_NEW);
        waitFor(home.resolve("arm-crash.marker"));
        var registry = context.getBean(RecoveryOperationRegistry.class);
        var operation = registry.admit(token(registry));
        assertThat(operation.leaseId()).isNotNull();
        saveOperation(home, operation, "worker-crash-held.json");
        waitFor(home.resolve("halt-worker.marker"));
        Runtime.getRuntime().halt(71);
        throw new AssertionError("Owned worker halt returned");
    }

    private static void observeApi(ConfigurableApplicationContext context, Path home) throws Exception {
        var registry = context.getBean(RecoveryOperationRegistry.class);
        try (var operation = registry.admit(token(registry))) { saveOperation(home, operation, "api-p5-live.json"); }
        waitFor(home.resolve("check-live.marker"));
        context.getBean(RecoveryFenceAuthority.class).checkEffect();
        Files.writeString(home.resolve("api-live-accepted.marker"), "actual product checkEffect accepted while worker owns lease",
            StandardOpenOption.CREATE_NEW);
        waitFor(home.resolve("check-crash.marker"));
        assertThatThrownBy(() -> registry.admit(token(registry))).hasMessage("RECOVERY_LEASE_FENCED");
        assertThatThrownBy(() -> context.getBean(RecoveryFenceAuthority.class).checkEffect()).hasMessage("RECOVERY_LEASE_FENCED");
        assertThat(registry.liveRoots()).isZero();
        var result = SubmissionJson.object().put("pid", ProcessHandle.current().pid())
            .put("admissionCode", "RECOVERY_LEASE_FENCED").put("effectCode", "RECOVERY_LEASE_FENCED").put("liveRoots", 0);
        Files.writeString(home.resolve("api-crash-refused.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
        waitFor(home.resolve("api-stop.marker"));
        for (var scheduler : context.getBeansOfType(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class).values())
            scheduler.shutdown();
        assertThat(registry.liveRoots()).isZero();
    }

    private static RecoveryOperationToken token(RecoveryOperationRegistry registry) {
        return new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.WORKER_JOB,
            registry.startup().installationId(), registry.startup().recordSha256(), null);
    }

    private static void saveOperation(Path home, RecoveryOperationLease operation, String name) throws Exception {
        var config = RecoveryControlHistory.read(home.getParent().resolve("p5-stale-authority/config.json"));
        Path p5 = Path.of(config.path("lease").asText());
        var lease = RecoveryControlHistory.read(p5.resolve("leases/" + operation.leaseId() + ".json"));
        var process = RecoveryControlHistory.read(p5.resolve("processes/" + lease.path("processIdentity").asText() + ".json"));
        assertThat(process.path("pid").asLong()).isEqualTo(ProcessHandle.current().pid());
        var result = SubmissionJson.object().put("pid", ProcessHandle.current().pid()).put("phase", operation.phase().toString());
        result.set("lease", lease); result.set("process", process);
        result.set("index", RecoveryControlHistory.read(p5.resolve("leases/index.json")));
        Files.writeString(home.resolve(name), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private static void waitFor(Path marker) {
        await().atMost(Duration.ofSeconds(240)).until(() -> Files.isRegularFile(marker));
    }
}
