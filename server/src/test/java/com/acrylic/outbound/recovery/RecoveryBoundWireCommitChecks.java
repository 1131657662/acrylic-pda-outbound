package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Observe real product API/worker roots; no test-provided worker operation or halt. */
public final class RecoveryBoundWireCommitChecks {
    public static void serve(ConfigurableApplicationContext context, Path home, boolean worker) throws Exception {
        Path p5 = Path.of(context.getEnvironment().getRequiredProperty("outbound.recovery.lease.root")).toRealPath();
        assertThat(p5).isEqualTo(home.getParent().resolve("restored-admission-p5/p5").toRealPath());
        assertThat(context.getBean(javax.sql.DataSource.class)).isInstanceOf(RecoveryRequestAdmittedDataSource.class);
        if (worker) {
            assertThat(context.getBean(RecoveryAdmissionConfiguration.MIGRATION_DATASOURCE))
                .isInstanceOf(RecoveryWireCommitDataSource.class);
            waitFor(home.resolve("worker-stop.marker"));
        } else observeApi(context, home, p5);
    }

    private static void observeApi(ConfigurableApplicationContext context, Path home, Path p5) throws Exception {
        var registry = context.getBean(RecoveryOperationRegistry.class);
        var token = new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.WORKER_JOB,
            registry.startup().installationId(), registry.startup().recordSha256(), null);
        try (var operation = registry.admit(token)) { record(home.resolve("api-p5-live.json"), operation, p5); }
        waitFor(home.resolve("check-live.marker"));
        context.getBean(RecoveryFenceAuthority.class).checkEffect();
        Files.writeString(home.resolve("api-live-accepted.marker"), "actual bound API checkEffect accepted", StandardOpenOption.CREATE_NEW);
        waitFor(home.resolve("check-commit.marker"));
        assertThatThrownBy(() -> registry.admit(token)).hasMessage("RECOVERY_LEASE_FENCED");
        assertThatThrownBy(() -> context.getBean(RecoveryFenceAuthority.class).checkEffect()).hasMessage("RECOVERY_LEASE_FENCED");
        assertThat(registry.liveRoots()).isZero();
        var result = SubmissionJson.object().put("pid", ProcessHandle.current().pid())
            .put("admissionCode", "RECOVERY_LEASE_FENCED").put("effectCode", "RECOVERY_LEASE_FENCED").put("liveRoots", 0);
        Files.writeString(home.resolve("api-commit-refused.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
        waitFor(home.resolve("api-stop.marker"));
        for (var scheduler : context.getBeansOfType(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class).values())
            scheduler.shutdown();
        assertThat(registry.liveRoots()).isZero();
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
