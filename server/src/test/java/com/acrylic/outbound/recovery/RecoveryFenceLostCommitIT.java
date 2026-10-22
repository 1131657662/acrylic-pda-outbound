package com.acrylic.outbound.recovery;

import java.nio.channels.FileChannel;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Actual committed row plus lost acknowledgement: P5 must preserve UNKNOWN and refuse successors. */
class RecoveryFenceLostCommitIT {
    @Test void committedRowSurvivesLostAcknowledgementWhileP5StaysFenced() throws Exception {
        RecoveryFenceCrossProcessIT.environment();
        var evidence = RecoveryFenceCrossProcessIT.evidence;
        var supply = FenceTestSupply.create(evidence);
        var authority = supply.authority();
        var gate = RecoveryEffectGate.enabled(authority);
        try (var jdbc = new FenceRealJdbc(gate); var old = jdbc.connection.createStatement()) {
            baseline(jdbc);
            try (var child = new FenceChild(supply, "lost-commit", jdbc.table)) {
                committedWithoutAcknowledgement(supply, gate, jdbc, child);
                jdbc.rejected(old, "RECOVERY_LEASE_OUTSTANDING");
                jdbc.rejected(old, "RECOVERY_LEASE_FENCED");
                assertThatThrownBy(() -> RecoveryEffectGate.enabled(supply.authority()))
                    .hasMessage("RECOVERY_LEASE_FENCED"); // Same JVM retains its irreversible local signal.
                EffectRealEnvironment.record("REAL|sameJvmNewAuthority|code=RECOVERY_LEASE_FENCED");
                successor(supply, jdbc);
                Files.writeString(evidence.resolve("lost-commit-summary.tsv"),
                    "table\tbaseline\tbeforeCommit\tafterCommit\tchildExit\toldDelegateDelta\tfreshExit\n"
                    + jdbc.table + "\t1\t1\t2\t70\t0\t1\n", StandardOpenOption.CREATE_NEW);
            }
        } finally { authority.retire(); }
    }

    private static void committedWithoutAcknowledgement(FenceTestSupply supply, RecoveryEffectGate gate,
            FenceRealJdbc jdbc, FenceChild child) throws Exception {
        child.await("PREPARED|uncommitted=2|delegateInsert=1");
        assertThat(jdbc.count()).isEqualTo(1); // Separate observer cannot see the pending row.
        Path process = owner(supply, child);
        assertThat(locked(process)).isTrue();
        gate.check();
        EffectRealEnvironment.record("REAL|prepared|observer=" + jdbc.count() + "|ownerLocked=true|gate=accepted");
        child.send("go");
        assertThat(child.exit()).isEqualTo(70);
        assertThat(jdbc.count()).isEqualTo(2); // Driver commit succeeded before the acknowledgement exception.
        afterHalt(supply, child, process);
        EffectRealEnvironment.record("REAL|lostCommit|observer=" + jdbc.count() + "|ownerLocked=false|exit=70");
    }

    private static void baseline(FenceRealJdbc jdbc) throws Exception {
        jdbc.connection.setAutoCommit(false);
        jdbc.connection.createStatement().executeUpdate(jdbc.insert(1));
        assertThat(jdbc.count()).isZero();
        jdbc.connection.commit();
        assertThat(jdbc.calls.count("Connection.commit")).isEqualTo(1);
        assertThat(jdbc.count()).isEqualTo(1);
        EffectRealEnvironment.record("REAL|normalCommit|before=0|observer=" + jdbc.count()
            + "|driverCommits=" + jdbc.calls.count("Connection.commit"));
    }

    private static Path owner(FenceTestSupply supply, FenceChild child) throws Exception {
        String id = Files.readString(child.directory.resolve("operation.txt"));
        var lease = RecoveryControlHistory.read(supply.roots().root().resolve("leases/" + id + ".json"));
        Path process = supply.roots().root().resolve("processes/" + lease.path("processIdentity").asText() + ".json");
        assertThat(RecoveryControlHistory.read(process).path("pid").asLong()).isEqualTo(child.process.pid());
        return process;
    }

    private static boolean locked(Path process) throws Exception {
        try (var channel = FileChannel.open(process, StandardOpenOption.READ, StandardOpenOption.WRITE);
                var probe = channel.tryLock(1L << 30, 1, false)) { return probe == null; }
    }

    private static void afterHalt(FenceTestSupply supply, FenceChild child, Path process) throws Exception {
        assertThat(locked(process)).isFalse();
        assertThat(child.directory.resolve("commit-returned.marker")).doesNotExist();
        assertThat(Files.readString(child.directory.resolve("stderr.txt"))).contains(
            "FENCE_F1_PRIME_CONFIRMED", "FENCE_F2_AUDIT_CONFIRMED", "FENCE_F3_RELEASE_CONFIRMED", "FENCE_F4_WRITERS_RETAINED");
        String id = Files.readString(child.directory.resolve("operation.txt"));
        var unknown = RecoveryControlHistory.read(supply.roots().root().resolve("leases/" + id + ".unknown.json"));
        assertThat(unknown.path("operationId").asText()).isEqualTo(id);
        assertThat(unknown.path("event").asText()).isEqualTo("RECOVERY_LEASE_FENCED");
        assertThat(unknown.path("processIdentity").asText()).isEqualTo(process.getFileName().toString().replace(".json", ""));
        preserve(supply.roots().root(), supply.base().resolve("originals-before-observation"));
    }

    private static void preserve(Path source, Path target) throws Exception {
        try (var files = Files.walk(source)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                Path copy = target.resolve(source.relativize(path));
                Files.createDirectories(copy.getParent());
                Files.copy(path, copy);
            }
        }
    }

    private static void successor(FenceTestSupply supply, FenceRealJdbc jdbc) throws Exception {
        try (var fresh = new FenceChild(supply, "effect", jdbc.table)) {
            assertThat(fresh.exit()).isEqualTo(1);
            assertThat(Files.readString(fresh.directory.resolve("stderr.txt"))).contains("RECOVERY_LEASE_OUTSTANDING");
            assertThat(fresh.status()).doesNotContain("EFFECT|");
            assertThat(jdbc.count()).isEqualTo(2);
        }
    }
}
