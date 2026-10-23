package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Observes an actual product registry operation before advertising readiness to the test parent. */
public final class RecoveryRegisteredP5Checks {
    public static void observe(ConfigurableApplicationContext context, Path home, String role) throws Exception {
        var registry = context.getBean(RecoveryOperationRegistry.class);
        context.getBean(RecoveryFenceAuthority.class).checkEffect();
        var token = new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.WORKER_JOB,
            registry.startup().installationId(), registry.startup().recordSha256(), null);
        Path leaseRoot = home.getParent().resolve("restored-admission-p5/p5");
        var result = RecoveryControlFixture.JSON.createObjectNode().put("pid", ProcessHandle.current().pid()).put("role", role);
        try (var operation = registry.admit(token)) {
            assertThat(operation.leaseId()).isNotNull();
            var lease = RecoveryControlHistory.read(leaseRoot.resolve("leases/" + operation.leaseId() + ".json"));
            assertThat(lease.path("operationId").asText()).isEqualTo(operation.leaseId().toString());
            var process = RecoveryControlHistory.read(leaseRoot.resolve("processes/" + lease.path("processIdentity").asText() + ".json"));
            assertThat(process.path("pid").asLong()).isEqualTo(ProcessHandle.current().pid());
            result.set("lease", lease); result.set("process", process);
            result.set("index", RecoveryControlHistory.read(leaseRoot.resolve("leases/index.json")));
            assertThat(result.path("index").path("entries").toString()).contains(operation.leaseId().toString());
            result.put("phase", operation.phase().toString());
        }
        context.getBean(RecoveryFenceAuthority.class).checkEffect();
        result.put("retiredOwnOperation", true);
        Files.writeString(home.resolve(role + "-p5-live.json"), result.toPrettyString(), StandardOpenOption.CREATE_NEW);
    }
}
