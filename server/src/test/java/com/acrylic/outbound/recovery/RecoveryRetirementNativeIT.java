package com.acrylic.outbound.recovery;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Independent JVMs actually consume normal retirement and a real file-operation refusal. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class RecoveryRetirementNativeIT {
    @Test void normalRetirementAllowsTheNextProcess() throws Exception {
        Path config = prepare("normal"), home = config.getParent(), out = home.resolve("owner.stdout");
        assertThat(RecoveryRetirementProcess.finish(RecoveryRetirementProcess.launch(config, "normal", out))).isZero();
        assertThat(Files.readString(home.resolve("normal-return.txt"))).isEqualTo("RELEASED|roots=0");
        assertThat(RecoveryControlHistory.read(home.resolve("p5/leases/index.json")).path("entries")).isEmpty();
        Path next = home.resolve("next.stdout");
        assertThat(RecoveryRetirementProcess.finish(RecoveryRetirementProcess.launch(config, "startup", next))).isZero();
        assertThat(Files.readString(next)).contains("STARTED");
    }

    @Test void actualRenameRefusalInvokesHaltAndKeepsTheOriginalLease() throws Exception {
        Path config = prepare("delete-failure"), home = config.getParent(), out = home.resolve("owner.stdout");
        assertThat(RecoveryRetirementProcess.finish(RecoveryRetirementProcess.launch(config, "delete-failure", out))).isEqualTo(70);
        var original = RecoveryControlHistory.read(home.resolve("published-original.json"));
        Path record = home.resolve("p5/leases/" + original.path("operationId").asText() + ".json");
        assertThat(Files.readAllBytes(record)).isEqualTo(Files.readAllBytes(home.resolve("published-original.json")));
        assertThat(home.resolve("normal-return.txt")).doesNotExist();
        assertThat(Files.readString(home.resolve("owner.stdout.stderr")))
            .contains("FENCE_F1_PRIME_CONFIRMED", "FENCE_F2_AUDIT_CONFIRMED", "FENCE_F3_RELEASE_CONFIRMED", "FENCE_F4_WRITERS_RETAINED");
        Files.copy(record, home.resolve("orphan-original.json"));
        Files.copy(record.resolveSibling(original.path("operationId").asText() + ".unknown.json"), home.resolve("unknown-original.json"));
        Path next = home.resolve("next.stdout");
        assertThat(RecoveryRetirementProcess.finish(RecoveryRetirementProcess.launch(config, "startup", next))).isEqualTo(2);
        assertThat(Files.readString(next)).contains("RECOVERY_LEASE_OUTSTANDING");
    }

    private static Path prepare(String name) throws Exception {
        Path attempt = Path.of(System.getenv("E2A_CROSS_ROOT"), ".local/e2a-cross-20260925-" + System.getenv("E2A_CROSS_RUN"));
        Path home = Files.createDirectory(attempt.resolve("private/retirement-" + name));
        var pin = new RecoveryInitialAuthorityProvision.Provisioned[1];
        var control = RecoveryWriterLeaseTest.protocol(f -> {
            pin[0] = RecoveryInitialAuthorityProvision.provision(f.root, home.resolve("p5"), home.resolve("closed"));
            return RecoveryControlHistory.read(f.root.resolve("identity.json"));
        });
        return RecoveryRetirementProcess.config(home, control, pin[0]);
    }
}
