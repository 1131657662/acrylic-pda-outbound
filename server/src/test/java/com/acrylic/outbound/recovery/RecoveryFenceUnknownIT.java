package com.acrylic.outbound.recovery;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryFenceUnknownIT {
    static Path evidence;
    @BeforeAll static void environment() throws Exception {
        RecoveryFenceCrossProcessIT.environment(); evidence = RecoveryFenceCrossProcessIT.evidence;
    }
    @RepeatedTest(3) void ambiguousJdbcOutcomeInvokesProductionSequenceAndRealHalt() throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var gate = RecoveryEffectGate.enabled(supply.authority());
        try (var jdbc = new FenceRealJdbc(gate); var statement = jdbc.connection.createStatement();
                var child = new FenceChild(supply, "unknown", jdbc.table)) {
            assertThat(child.exit()).isEqualTo(70);
            String errors = Files.readString(child.directory.resolve("stderr.txt"));
            assertThat(errors).contains("FENCE_F1_PRIME_CONFIRMED", "FENCE_F2_AUDIT_CONFIRMED",
                "FENCE_F3_RELEASE_CONFIRMED", "FENCE_F4_WRITERS_RETAINED");
            try (var files = Files.list(supply.roots().root().resolve("leases"))) {
                assertThat(files.filter(p -> p.toString().endsWith(".unknown.json")).count()).isEqualTo(1);
            }
            jdbc.rejected(statement, "RECOVERY_LEASE_OUTSTANDING");
            assertThat(jdbc.count()).isZero();
            EffectRealEnvironment.record("REAL|production-unknown|childExit=70|orphan-visible|committedRows=0|trigger=MODEL");
        }
    }
}
