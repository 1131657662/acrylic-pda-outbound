package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
import static com.acrylic.outbound.recovery.FenceAdversarialEvidence.*;

/** Security requirements deliberately remain red when the baseline admits an attack. */
class RecoveryFenceAdversarialDurableIT {
    static Path evidence;
    @BeforeAll static void environment() throws Exception {
        RecoveryFenceCrossProcessIT.environment(); evidence = RecoveryFenceCrossProcessIT.evidence;
    }
    static Stream<Arguments> mutations() {
        return IntStream.range(0, 3).boxed().flatMap(i -> Stream.of("truncate", "extend", "rewrite", "move")
            .map(kind -> Arguments.of(i, kind)));
    }
    @ParameterizedTest @MethodSource("mutations")
    void foreignHeldProcessMutationMustRefuse(int repeat, String kind) throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var gate = RecoveryEffectGate.enabled(supply.authority());
        try (var jdbc = new FenceRealJdbc(gate); var old = jdbc.connection.createStatement();
                var child = new FenceChild(supply, "adversarial-owner")) {
            Path lease = lease(supply, childOperation(child)), owner = owner(supply, lease);
            assertThat(lockAvailable(owner)).isFalse();
            switch (kind) {
                case "truncate" -> Files.writeString(owner, "");
                case "extend" -> Files.writeString(owner, "x", StandardOpenOption.APPEND);
                case "rewrite" -> { var record = read(owner); record.put("pid", 1); write(owner, record); }
                case "move" -> Files.move(owner, supply.base().resolve("retained-owner.json"));
                default -> throw new AssertionError(kind);
            }
            Path held = kind.equals("move") ? supply.base().resolve("retained-owner.json") : owner;
            assertThat(lockAvailable(held)).as("1GiB lifecycle lock survives payload mutation").isFalse();
            EffectRealEnvironment.record("REAL|payload-mutation|kind=" + kind + "|lifecycleLockStillHeld=true");
            requireRefusal(jdbc, old, "process-" + kind + "-" + repeat);
        }
    }
    @RepeatedTest(3) void replacedLocalOwnerWithReboundLeaseMustRefuse() throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var authority = supply.authority(); var gate = RecoveryEffectGate.enabled(authority);
        Path lease = lease(supply, authority.registerOperation()), owner = owner(supply, lease);
        try (var jdbc = new FenceRealJdbc(gate); var old = jdbc.connection.createStatement()) {
            String oldIdentity = RecoveryWriterProtocol.fileIdentity(owner);
            Path retained = supply.base().resolve("retained-owner.json");
            Files.move(owner, retained); Files.copy(retained, owner);
            String newIdentity = RecoveryWriterProtocol.fileIdentity(owner);
            assertThat(newIdentity).isNotEqualTo(oldIdentity); assertThat(lockAvailable(owner)).isTrue();
            var record = read(lease); record.put("processFileIdentity", newIdentity); write(lease, record);
            EffectRealEnvironment.record("REAL|local-rebinding|old=" + oldIdentity + "|new=" + newIdentity + "|newLockAvailable=true");
            requireRefusal(jdbc, old, "local-owner-identity-rebinding");
        }
    }
    static Stream<Arguments> disguises() {
        return IntStream.range(0, 3).boxed().flatMap(i -> Stream.of("unknown", "unknown-pending")
            .map(kind -> Arguments.of(i, kind)));
    }
    @ParameterizedTest @MethodSource("disguises")
    void deadOwnerCannotBeHiddenByAuditNameOrLiveOwnerReference(int repeat, String kind) throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var authority = supply.authority();
        var gate = RecoveryEffectGate.enabled(authority);
        // A second live child would contend for the exclusive coordinator lock while registering;
        // the parent is already a real live owner, so use its lease as the "live" reference.
        Path liveLease = lease(supply, authority.registerOperation());
        try (var jdbc = new FenceRealJdbc(gate); var old = jdbc.connection.createStatement();
                var dead = new FenceChild(supply, "adversarial-owner")) {
            Path deadLease = lease(supply, childOperation(dead));
            Path deadOwner = owner(supply, deadLease);
            dead.send("stop"); assertThat(dead.exit()).isEqualTo(70);
            assertThat(lockAvailable(deadOwner)).isTrue();
            Files.copy(deadLease, supply.base().resolve("original-orphan-lease.json"));
            disguise(deadLease, liveLease, kind);
            EffectRealEnvironment.record("REAL|orphan-disguise|kind=" + kind + "|deadPid=" + dead.process.pid()
                + "|deadLockAvailable=true|liveOwnerRecordExists=" + Files.exists(owner(supply, liveLease))
                + "|liveOwnerIsThisJvm=true");
            requireRefusal(jdbc, old, "orphan-" + kind + "-" + repeat);
        }
    }
    /**
     * H3 closure (B1-2 follow-up): with the explicitly supplied anchor in force, a lease whose owner
     * reference was rewritten to a live process can no longer masquerade as a live owner. The
     * delete-the-record variant additionally needs the anchored operation index described in
     * [P5 认证锚点补充规格](R09_C2_B1_2_ANCHOR_SPEC_2026-09-29.md) and stays single-listed until then.
     */
    @RepeatedTest(3) void forgedLeaseRepointingMustNowBeRefusedByTheAnchor() throws Exception {
        var supply = FenceTestSupply.create(evidence);
        var authority = supply.authority();
        var gate = RecoveryEffectGate.enabled(authority);
        Path liveLease = lease(supply, authority.registerOperation());
        try (var jdbc = new FenceRealJdbc(gate); var old = jdbc.connection.createStatement();
                var dead = new FenceChild(supply, "adversarial-owner")) {
            Path deadLease = lease(supply, childOperation(dead));
            dead.send("stop"); assertThat(dead.exit()).isEqualTo(70);
            assertThat(lockAvailable(owner(supply, deadLease))).isTrue();
            disguise(deadLease, liveLease, "live-owner");
            EffectRealEnvironment.record("REAL|anchor-closure|forged-lease-repointing|liveOwnerIsThisJvm=true");
            requireRefusal(jdbc, old, "orphan-live-owner-anchored");
        }
    }
    private static void disguise(Path dead, Path live, String kind) throws Exception {
        if (kind.startsWith("unknown")) {
            String name = dead.getFileName().toString().replace(".json", ".unknown.json");
            Files.move(dead, dead.resolveSibling(name + (kind.endsWith("pending") ? ".pending" : "")));
            return;
        }
        var record = read(dead); var other = read(live);
        for (String field : new String[]{"processIdentity", "processFileIdentity", "processSha256"})
            record.set(field, other.get(field));
        write(dead, record);
    }
}
