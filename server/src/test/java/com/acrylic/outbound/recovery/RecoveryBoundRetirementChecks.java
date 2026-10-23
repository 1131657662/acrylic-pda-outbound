package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Actual bound product worker encounters a native rename refusal during P5 retirement. */
public final class RecoveryBoundRetirementChecks {
    public static void serve(ConfigurableApplicationContext context, Path home, boolean worker) throws Exception {
        Path p5 = Path.of(context.getEnvironment().getRequiredProperty("outbound.recovery.lease.root")).toRealPath();
        assertThat(p5).isEqualTo(home.getParent().resolve("restored-admission-p5/p5").toRealPath());
        if (worker) retireWorker(context, home, p5);
        else observeApi(context, home, p5);
    }

    private static void retireWorker(ConfigurableApplicationContext context, Path home, Path p5) throws Exception {
        waitFor(home.resolve("pause-worker.marker"));
        RecoverySplitWorkerChecks.drain(context);
        Files.writeString(home.resolve("worker-paused.marker"), "scheduler drained; bound worker JVM live", StandardOpenOption.CREATE_NEW);
        waitFor(home.resolve("arm-retirement.marker"));
        var registry = context.getBean(RecoveryOperationRegistry.class);
        var operation = registry.admit(token(registry));
        assertThat(operation.leaseId()).isNotNull();
        record(home.resolve("worker-retirement-held.json"), operation, p5);
        waitFor(home.resolve("fail-retirement.marker"));
        denyDelete(home, p5.resolve("leases/" + operation.leaseId() + ".json"), operation);
    }

    private static void denyDelete(Path home, Path record, RecoveryOperationLease operation) throws Exception {
        var nativeApi = com.sun.jna.platform.win32.Kernel32.INSTANCE;
        var handle = nativeApi.CreateFile(record.toString(), com.sun.jna.platform.win32.WinNT.GENERIC_READ,
            com.sun.jna.platform.win32.WinNT.FILE_SHARE_READ | com.sun.jna.platform.win32.WinNT.FILE_SHARE_WRITE,
            null, com.sun.jna.platform.win32.WinNT.OPEN_EXISTING, 0, null);
        assertThat(handle).isNotEqualTo(com.sun.jna.platform.win32.WinBase.INVALID_HANDLE_VALUE);
        var fact = SubmissionJson.object().put("pid", ProcessHandle.current().pid()).put("path", record.toString())
            .put("handleValid", true).put("shareRead", true).put("shareWrite", true).put("shareDelete", false)
            .put("recordSha256", RecoveryControlFormat.sha256(Files.readAllBytes(record)));
        Files.writeString(home.resolve("deny-delete-handle.json"), fact.toPrettyString(), StandardOpenOption.CREATE_NEW);
        try { operation.close(); }
        finally { nativeApi.CloseHandle(handle); }
        throw new AssertionError("Native retirement refusal did not halt owned product worker");
    }

    private static void observeApi(ConfigurableApplicationContext context, Path home, Path p5) throws Exception {
        var registry = context.getBean(RecoveryOperationRegistry.class);
        try (var operation = registry.admit(token(registry))) {
            record(home.resolve("api-p5-live.json"), operation, p5);
        }
        waitFor(home.resolve("check-live.marker"));
        context.getBean(RecoveryFenceAuthority.class).checkEffect();
        Files.writeString(home.resolve("api-live-accepted.marker"), "actual bound API checkEffect accepted", StandardOpenOption.CREATE_NEW);
        waitFor(home.resolve("check-retirement.marker"));
        assertThatThrownBy(() -> registry.admit(token(registry))).hasMessage("RECOVERY_LEASE_FENCED");
        assertThatThrownBy(() -> context.getBean(RecoveryFenceAuthority.class).checkEffect()).hasMessage("RECOVERY_LEASE_FENCED");
        assertThat(registry.liveRoots()).isZero();
        var result = SubmissionJson.object().put("pid", ProcessHandle.current().pid())
            .put("admissionCode", "RECOVERY_LEASE_FENCED").put("effectCode", "RECOVERY_LEASE_FENCED").put("liveRoots", 0);
        Files.writeString(home.resolve("api-retirement-refused.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
        waitFor(home.resolve("api-stop.marker"));
        for (var scheduler : context.getBeansOfType(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class).values())
            scheduler.shutdown();
        assertThat(registry.liveRoots()).isZero();
    }

    private static RecoveryOperationToken token(RecoveryOperationRegistry registry) {
        return new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.WORKER_JOB,
            registry.startup().installationId(), registry.startup().recordSha256(), null);
    }

    private static void record(Path output, RecoveryOperationLease operation, Path p5) throws Exception {
        var lease = RecoveryControlHistory.read(p5.resolve("leases/" + operation.leaseId() + ".json"));
        var process = RecoveryControlHistory.read(p5.resolve("processes/" + lease.path("processIdentity").asText() + ".json"));
        assertThat(process.path("pid").asLong()).isEqualTo(ProcessHandle.current().pid());
        var result = SubmissionJson.object().put("pid", ProcessHandle.current().pid()).put("phase", operation.phase().toString());
        result.set("lease", lease); result.set("process", process);
        result.set("index", RecoveryControlHistory.read(p5.resolve("leases/index.json")));
        Files.writeString(output, result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }

    private static void waitFor(Path marker) {
        await().atMost(Duration.ofSeconds(180)).until(() -> Files.isRegularFile(marker));
    }
}
