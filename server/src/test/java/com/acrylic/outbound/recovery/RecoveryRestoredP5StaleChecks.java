package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Records a real live P5 operation from each separate restored product JVM. */
public final class RecoveryRestoredP5StaleChecks {
    public static void serve(ConfigurableApplicationContext context, Path home, boolean worker) throws Exception {
        observe(context, home, worker ? "worker" : "api");
        RecoveryRestoredStaleChecks.serve(context, home, worker);
    }

    private static void observe(ConfigurableApplicationContext context, Path home, String role) throws Exception {
        var registry = context.getBean(RecoveryOperationRegistry.class);
        var fence = context.getBean(RecoveryFenceAuthority.class); fence.checkEffect();
        var token = new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.WORKER_JOB,
            registry.startup().installationId(), registry.startup().recordSha256(), null);
        Path config = home.getParent().resolve("p5-stale-authority/config.json");
        Path leaseRoot = Path.of(RecoveryControlHistory.read(config).path("lease").asText());
        var result = SubmissionJson.object().put("pid", ProcessHandle.current().pid()).put("role", role);
        try (var operation = registry.admit(token)) {
            assertThat(operation.leaseId()).isNotNull();
            var lease = RecoveryControlHistory.read(leaseRoot.resolve("leases/" + operation.leaseId() + ".json"));
            assertThat(lease.path("operationId").asText()).isEqualTo(operation.leaseId().toString());
            var process = RecoveryControlHistory.read(leaseRoot.resolve("processes/" + lease.path("processIdentity").asText() + ".json"));
            assertThat(process.path("pid").asLong()).isEqualTo(ProcessHandle.current().pid());
            var index = RecoveryControlHistory.read(leaseRoot.resolve("leases/index.json"));
            assertThat(index.path("entries").toString()).contains(operation.leaseId().toString());
            result.set("lease", lease); result.set("process", process); result.set("index", index);
            result.put("phase", operation.phase().toString());
        }
        result.put("retiredOwnOperation", true);
        Files.writeString(home.resolve(role + "-p5-live.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
