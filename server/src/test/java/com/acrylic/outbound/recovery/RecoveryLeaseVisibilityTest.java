package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W-2-d layer 1 (ruling 2026-09-30): the read-only visibility snapshot must describe the same tree the scan judges,
 * must never mutate it, and must never turn a refusal into an allowance.
 *
 * <p>The scan stays the authority: {@link RecoveryFenceAuthority#validateStartup()} is still expected to refuse in
 * exactly the cases it refused before, whether or not anyone looked at the counters first.
 */
class RecoveryLeaseVisibilityTest {
    @TempDir Path parent;

    @Test void aLiveOwnersRecordIsNotAnOrphanAndStaysUntouched() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var authority = supply.authority();
        authority.validateStartup();
        // The owner stays alive on purpose: retirement is what makes a record an orphan, so a live owner is the
        // only shape that reports nothing outstanding.
        authority.registerOperation();
        Path leases = supply.roots().root().resolve("leases");
        var before = listing(leases);

        var snapshot = RecoveryLeaseVisibility.counts(supply.roots());

        assertThat(snapshot.outstanding()).as("a live owner's record is not an orphan").isFalse();
        assertThat(snapshot.orphansObserved()).isZero();
        assertThat(snapshot.orphansRefused()).isZero();
        assertThat(snapshot.indexMismatches()).isZero();
        assertThat(listing(leases)).as("the snapshot never creates, rewrites or deletes a file")
            .containsExactlyInAnyOrderElementsOf(before);
    }

    @Test void aDeadOwnersRecordIsCountedAsOrphanWithoutBeingRetired() throws Exception {
        var supply = FenceTestSupply.create(parent);
        UUID operation = RecoveryOrphanOwnerProcess.leaveOrphan(supply);
        Path record = supply.roots().root().resolve("leases").resolve(operation + ".json");
        Path leases = supply.roots().root().resolve("leases");
        var before = listing(leases);

        var snapshot = RecoveryLeaseVisibility.counts(supply.roots());

        assertThat(snapshot.orphansObserved()).as("the dead owner's lease is an orphan").isEqualTo(1);
        assertThat(snapshot.orphansRetired()).as("counts observes, it does not retire").isEqualTo(1);
        assertThat(snapshot.orphansRefused()).isZero();
        assertThat(Files.exists(record)).as("the record is still on disk after counting").isTrue();
        assertThat(listing(leases)).containsExactlyInAnyOrderElementsOf(before);
        // The authority still refuses, exactly as before the counters existed.
        assertThatThrownBy(() -> new RecoveryFenceAuthority(supply.roots()).validateStartup())
            .hasMessageContaining("RECOVERY_LEASE_OUTSTANDING");
    }

    @Test void aRecordDeletedAfterPublicationIsCountedAsAnIndexMismatch() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var authority = supply.authority();
        authority.validateStartup();
        UUID operation = authority.registerOperation();
        authority.retire();
        Files.delete(supply.roots().root().resolve("leases").resolve(operation + ".json"));

        var snapshot = RecoveryLeaseVisibility.counts(supply.roots());

        assertThat(snapshot.indexMismatches()).as("published but absent is a disagreement").isEqualTo(1);
        assertThat(snapshot.outstanding()).isTrue();
        assertThatThrownBy(() -> new RecoveryFenceAuthority(supply.roots()).validateStartup())
            .as("and the scan still refuses it").hasMessageContaining("RECOVERY_LEASE_OUTSTANDING");
    }

    @Test void aHalfWrittenPendingRecordIsCountedAsAnOrphan() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var authority = supply.authority();
        authority.validateStartup();
        Path pending = supply.roots().root().resolve("leases").resolve(UUID.randomUUID() + ".json.pending");
        Files.writeString(pending, "{\"formatVersion\":1}");

        var snapshot = RecoveryLeaseVisibility.counts(supply.roots());

        assertThat(snapshot.orphansObserved()).as("a half-written record is an orphan").isEqualTo(1);
        assertThat(Files.exists(pending)).as("and it is left in place by the counter").isTrue();
    }

    private static java.util.List<String> listing(Path directory) throws Exception {
        try (var entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
