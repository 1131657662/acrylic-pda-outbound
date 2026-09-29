package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryFenceIntegrityIT {
    static Path evidence;
    @BeforeAll static void environment() throws Exception {
        RecoveryFenceCrossProcessIT.environment(); evidence = RecoveryFenceCrossProcessIT.evidence;
    }
    static Stream<Arguments> identityCases() {
        return IntStream.range(0, 3).boxed().flatMap(i -> Stream.of("FENCE", "COORDINATOR")
            .flatMap(role -> Stream.of("MISSING", "REPLACED").map(kind -> Arguments.of(i, role, kind))));
    }
    @ParameterizedTest @MethodSource("identityCases")
    void nativeIdentityRefusalIsDistinctAndNeverRecreates(int repeat, String role, String kind) throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var gate = RecoveryEffectGate.enabled(supply.authority());
        try (var jdbc = new FenceRealJdbc(gate); var statement = jdbc.connection.createStatement()) {
            Path file = role.equals("FENCE") ? supply.roots().root().resolve("fenced.lock")
                : supply.roots().authorityRoot().resolve("coordinator.lock");
            String identity = RecoveryWriterProtocol.fileIdentity(file);
            Files.move(file, supply.base().resolve(role + "-original.lock"));
            if (kind.equals("REPLACED")) {
                Files.writeString(file, "0", StandardOpenOption.CREATE_NEW);
                assertThat(RecoveryWriterProtocol.fileIdentity(file)).isNotEqualTo(identity);
            }
            jdbc.rejected(statement, "CONTROL_" + role + "_LOCK_" + kind);
            if (kind.equals("MISSING")) assertThat(file).doesNotExist();
            assertThatThrownBy(() -> RecoveryEffectGate.enabled(supply.authority()))
                .hasMessage("CONTROL_" + role + "_LOCK_" + kind);
        }
    }
    static Stream<Arguments> orphanCases() {
        return IntStream.range(0, 3).boxed().flatMap(i -> Stream.of("pending", "malformed", "ownerMissing", "ownerMalformed", "ownerReplaced")
            .map(kind -> Arguments.of(i, kind)));
    }
    @ParameterizedTest @MethodSource("orphanCases")
    void eachInvalidRecordIsAnOrphan(int repeat, String kind) throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var authority = supply.authority(); var gate = RecoveryEffectGate.enabled(authority);
        UUID operation = authority.registerOperation();
        Path lease = supply.roots().root().resolve("leases").resolve(operation + ".json");
        try (var jdbc = new FenceRealJdbc(gate); var statement = jdbc.connection.createStatement()) {
            var record = RecoveryControlHistory.read(lease);
            Path owner = supply.roots().root().resolve("processes").resolve(record.path("processIdentity").asText() + ".json");
            switch (kind) {
                case "pending" -> Files.move(lease, lease.resolveSibling(operation + ".json.pending"));
                case "malformed" -> Files.writeString(lease, "{");
                case "ownerMissing" -> Files.move(owner, supply.base().resolve("retained-process.json"));
                case "ownerMalformed" -> Files.writeString(owner, "{");
                case "ownerReplaced" -> {
                    Files.move(owner, supply.base().resolve("retained-process.json"));
                    Files.copy(supply.base().resolve("retained-process.json"), owner);
                }
                default -> throw new AssertionError(kind);
            }
            jdbc.rejected(statement, "RECOVERY_LEASE_OUTSTANDING");
            assertThatThrownBy(() -> RecoveryEffectGate.enabled(supply.authority())).hasMessage("RECOVERY_LEASE_FENCED");
        }
    }
    @RepeatedTest(3) void ownLiveRecordsAllowEffectsAndAuditAloneIsNotABasis() throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var authority = supply.authority(); var gate = RecoveryEffectGate.enabled(authority);
        UUID first = authority.registerOperation(); authority.registerOperation();
        // An audit marker is ignorable only while the lease it annotates survives, and only in the shape
        // RecoveryFenceAuthority.writeUnknown writes (B1-2 adversarial round tightened the former name-only skip).
        var marker = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
            .put("operationId", first.toString()).put("event", "RECOVERY_LEASE_FENCED")
            .put("at", RecoveryLeaseProcess.now());
        Files.writeString(supply.roots().root().resolve("leases").resolve(first + ".unknown.json"),
            RecoveryControlFormat.canonical(marker));
        try (var jdbc = new FenceRealJdbc(gate); var statement = jdbc.connection.createStatement()) {
            statement.executeUpdate(jdbc.insert(1)); assertThat(jdbc.count()).isEqualTo(1);
            EffectRealEnvironment.record("REAL|own-live-records|delegate=1|observer=1|audit-not-a-blocking-basis");
            // Tightened expectation: a marker with no lease beside it is not ignorable any more.
            Files.writeString(supply.roots().root().resolve("leases").resolve(UUID.randomUUID() + ".unknown.json"), "audit only");
            jdbc.rejected(statement, "RECOVERY_LEASE_OUTSTANDING");
        }
    }
    @RepeatedTest(3) void preexistingResultSetAndSourceReadRemainGated() throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var gate = RecoveryEffectGate.enabled(supply.authority());
        try (var jdbc = new FenceRealJdbc(gate); var statement = jdbc.connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            statement.executeUpdate(jdbc.insert(1));
            try (var rows = statement.executeQuery("SELECT id,val FROM " + jdbc.table);
                    var child = new FenceChild(supply, "signal")) {
                assertThat(rows.next()).isTrue(); child.await("LOCKED");
                int mutations = jdbc.calls.count("ResultSet.updateString");
                assertThatThrownBy(() -> rows.updateString(2, "after-fence")).hasMessage("RECOVERY_LEASE_FENCED");
                assertThat(jdbc.calls.count("ResultSet.updateString")).isEqualTo(mutations);
                int reads = jdbc.calls.count("Statement.executeQuery");
                assertThatThrownBy(() -> statement.executeQuery("SELECT id FROM " + jdbc.table)).hasMessage("RECOVERY_LEASE_FENCED");
                assertThat(jdbc.calls.count("Statement.executeQuery")).isEqualTo(reads);
                assertThat(jdbc.count()).isEqualTo(1);
                EffectRealEnvironment.record("REAL|old-resultset-source-read|delegateDelta=0|observerDelta=0|SOURCE_READ-refused");
            }
        }
    }
    @RepeatedTest(3) void twoThreadsAndMultipleAuthoritiesShareMemoryFenceBeforeFileAccess() throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var first = RecoveryEffectGate.enabled(supply.authority());
        var second = RecoveryEffectGate.enabled(supply.authority());
        try (var jdbc = new FenceRealJdbc(second); var statement = jdbc.connection.createStatement();
                var pool = Executors.newFixedThreadPool(2)) {
            first.fence();
            Files.move(supply.roots().root().resolve("fenced.lock"), supply.base().resolve("retained-fence.lock"));
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 2; i++) tasks.add(pool.submit(() -> {
                assertThatThrownBy(() -> statement.executeUpdate(jdbc.insert(4))).hasMessage("RECOVERY_LEASE_FENCED");
            }));
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
            assertThat(jdbc.calls.count("Statement.executeUpdate")).isZero(); assertThat(jdbc.count()).isZero();
            EffectRealEnvironment.record("REAL|two-threads-memory-fence|delegate=0|observer=0|file-removed-after-F1");
        }
    }
}
