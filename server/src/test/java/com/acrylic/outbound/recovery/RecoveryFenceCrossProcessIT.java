package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryFenceCrossProcessIT {
    static Path evidence;
    @BeforeAll static void environment() throws Exception {
        EffectRealEnvironment.requireEnvironment();
        evidence = Path.of(System.getenv("B12_FENCE_EVIDENCE_DIR"));
        assertThat(evidence.toRealPath()).isEqualTo(Path.of(System.getenv("B11_EFFECT_EVIDENCE_DIR")).toRealPath());
    }
    @RepeatedTest(3) void sharedSignalsCoexistAndOldStatementCannotDelegate() throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var gate = RecoveryEffectGate.enabled(supply.authority());
        try (var jdbc = new FenceRealJdbc(gate); var old = jdbc.connection.createStatement();
                var first = new FenceChild(supply, "signal")) {
            first.await("LOCKED");
            try (var second = new FenceChild(supply, "signal")) {
                second.await("LOCKED");
                assertThat(first.process.isAlive()).isTrue();
                jdbc.rejected(old, "RECOVERY_LEASE_FENCED");
                assertThatThrownBy(() -> RecoveryEffectGate.enabled(supply.authority())).hasMessage("RECOVERY_LEASE_FENCED");
            }
        }
    }
    static Stream<Arguments> termination() {
        return IntStream.range(0, 3).boxed().flatMap(i -> Stream.of("kill", "halt").map(m -> Arguments.of(i, m)));
    }
    @ParameterizedTest @MethodSource("termination")
    void osReleaseAllowsFreshProcessDespiteResidualFile(int repeat, String mode) throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var gate = RecoveryEffectGate.enabled(supply.authority());
        try (var jdbc = new FenceRealJdbc(gate); var old = jdbc.connection.createStatement();
                var child = new FenceChild(supply, "signal")) {
            child.await("LOCKED"); jdbc.rejected(old, "RECOVERY_LEASE_FENCED");
            if (mode.equals("kill")) child.process.destroyForcibly(); else child.send("stop");
            int exit = child.exit(); if (mode.equals("halt")) assertThat(exit).isEqualTo(70);
            assertThat(supply.roots().root().resolve("fenced.lock")).exists();
            try (var fresh = new FenceChild(supply, "effect", jdbc.table)) {
                assertThat(fresh.exit()).isZero(); assertThat(fresh.status()).contains("EFFECT|delegate=1|observer=1");
            }
            assertThat(jdbc.count()).isEqualTo(1);
            jdbc.rejected(old, "RECOVERY_LEASE_FENCED"); // Original local fence is deliberately irreversible.
        }
    }
    static Stream<Arguments> matrix() {
        return IntStream.range(0, 3).boxed().flatMap(i -> Stream.of("111", "110", "101", "100", "011", "010", "001", "000")
            .map(bits -> Arguments.of(i, bits)));
    }
    @ParameterizedTest @MethodSource("matrix")
    void everyFailureMatrixBranchUsesObservedState(int repeat, String bits) throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var gate = RecoveryEffectGate.enabled(supply.authority());
        try (var jdbc = new FenceRealJdbc(gate); var old = jdbc.connection.createStatement();
                var child = new FenceChild(supply, "matrix", bits)) {
            child.await("READY"); gate.check(); child.send("go");
            if (bits.charAt(2) == '1') assertThat(child.exit()).isEqualTo(70); else child.await("MATRIX-DONE");
            assertThat(child.status()).contains("LOCAL|RECOVERY_LEASE_FENCED");
            String basis = bits.charAt(2) == '0' && bits.charAt(0) == '1' ? "RECOVERY_LEASE_FENCED" : "RECOVERY_LEASE_OUTSTANDING";
            if (bits.equals("000")) {
                gate.check();
                assertThat(Files.readString(child.directory.resolve("stderr.txt"))).contains("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
                old.executeUpdate(jdbc.insert(1));
                assertThat(jdbc.calls.count("Statement.executeUpdate")).isEqualTo(1);
                assertThat(jdbc.count()).isEqualTo(1);
                basis = "UNCONFIRMED_NO_CROSS_PROCESS_BASIS: external isolation required; canary delegate=1 observer=1";
            } else jdbc.rejected(old, basis);
            EffectRealEnvironment.record("REAL|matrix|repeat=" + repeat + "|steps=" + bits + "|basis=" + basis
                + "|childAlive=" + child.process.isAlive() + "|observer=" + jdbc.count());
        }
    }
}
