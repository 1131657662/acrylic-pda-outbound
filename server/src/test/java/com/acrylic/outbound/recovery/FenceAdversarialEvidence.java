package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Attack scheduling is MODEL; every admission uses the real Authority and driver. */
final class FenceAdversarialEvidence {
    private FenceAdversarialEvidence() { }
    static ObjectNode read(Path path) throws Exception {
        return (ObjectNode)RecoveryControlHistory.read(path);
    }
    static void write(Path path, ObjectNode value) throws Exception {
        Files.writeString(path, RecoveryControlFormat.canonical(value));
    }
    static Path lease(FenceTestSupply supply, UUID operation) {
        return supply.roots().root().resolve("leases").resolve(operation + ".json");
    }
    static Path owner(FenceTestSupply supply, Path lease) throws Exception {
        return supply.roots().root().resolve("processes")
            .resolve(read(lease).path("processIdentity").asText() + ".json");
    }
    static UUID childOperation(FenceChild child) throws Exception {
        child.await("OWNER|operation=");
        String line = child.status().lines().filter(s -> s.startsWith("OWNER|operation=")).findFirst().orElseThrow();
        return UUID.fromString(line.substring("OWNER|operation=".length()));
    }
    static boolean lockAvailable(Path path) throws Exception {
        try (var channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
                var lock = channel.tryLock(RecoveryLeaseProcess.LOCK_BYTE, 1, false)) {
            return lock != null;
        }
    }
    static void requireRefusal(FenceRealJdbc jdbc, java.sql.Statement old, String attack) throws Exception {
        int delegates = jdbc.calls.count("Statement.executeUpdate"), committed = jdbc.count();
        SQLException denial = null;
        try { old.executeUpdate(jdbc.insert(71)); } catch (SQLException error) { denial = error; }
        int delta = jdbc.calls.count("Statement.executeUpdate") - delegates, rows = jdbc.count() - committed;
        EffectRealEnvironment.record("REAL|adversarial|attack=" + attack + "|authority=RecoveryFenceAuthority"
            + "|delegateDelta=" + delta + "|observerDelta=" + rows + "|code="
            + (denial == null ? "ADMITTED_SECURITY_GAP" : denial.getMessage()) + "|table=" + jdbc.table);
        assertThat((Throwable)denial).as(attack + " must fail closed; delegate=" + delta + " observer=" + rows).isNotNull();
        assertThat(delta).isZero(); assertThat(rows).isZero();
    }
}
