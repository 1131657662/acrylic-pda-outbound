package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Design rows 10 and 12 in the request-root path: every admitted root publishes exactly one P5 lease
 * record (and a publication that fails fails the admission), while a record whose owner is gone is
 * retired — deleted together with its index entry — by the next scan, which still refuses.
 */
class RecoveryLeasePublicationTest {
    @TempDir Path parent;

    @Test void anAdmittedRootPublishesExactlyOneLeaseRecord() throws Exception {
        var published = new ArrayList<UUID>();
        var registry = registry(published);
        var root = registry.admit(token(registry));

        assertThat(published).as("one record per admitted root").hasSize(1);
        assertThat(root.leaseId()).isEqualTo(published.getFirst());
        assertThat(registry.liveRoots()).isEqualTo(1);
        // Closing the root does not remove the record: lease records only grow until a later process's
        // orphan scan retires the ones whose owner is gone.
        root.close();
        assertThat(published).hasSize(1);
        assertThat(root.leaseId()).isEqualTo(published.getFirst());
    }

    @Test void aRefusedPublicationFailsTheAdmissionAndLeavesNoRoot() throws Exception {
        var fixture = control();
        var registry = new RecoveryOperationRegistry(fixture.location(), fixture.checkpoint, () -> {
            throw new java.sql.SQLException("RECOVERY_LEASE_FENCED");
        });

        assertThatThrownBy(() -> registry.admit(token(registry)))
            .as("a root nobody can observe in P5 must not serve").hasMessageContaining("RECOVERY_LEASE_FENCED");
        assertThat(registry.liveRoots()).as("the failed admission left no live root").isZero();
        assertThat(registry.bound(Thread.currentThread())).isNull();
    }

    @Test void withoutAChannelNothingIsPublished() throws Exception {
        var fixture = control();
        var registry = new RecoveryOperationRegistry(fixture.location(), fixture.checkpoint);
        var root = registry.admit(token(registry));

        assertThat(root.leaseId()).as("no cross-process layer, no P5 record").isNull();
        root.close();
    }

    @Test void anOrphanLeaseRecordIsRetiredByTheScanThatRefusesIt() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var first = supply.authority();
        first.validateStartup();
        UUID operation = first.registerOperation();
        first.retire();
        Path record = supply.roots().root().resolve("leases").resolve(operation + ".json");
        assertThat(Files.exists(record)).isTrue();
        assertThat(indexed(supply)).as("the anchored index lists the published record").contains(operation.toString());

        var second = new RecoveryFenceAuthority(supply.roots());
        assertThatThrownBy(second::validateStartup).as("a dead owner is an orphan")
            .hasMessageContaining("RECOVERY_LEASE_OUTSTANDING");

        assertThat(Files.exists(record)).as("retirement deletes the orphan record").isFalse();
        assertThat(indexed(supply)).as("and drops its index entry").doesNotContain(operation.toString());
        assertThat(Files.list(supply.roots().root().resolve("processes")).count())
            .as("process records are evidence and are never retired").isEqualTo(1);
    }

    /**
     * The tree a later process will find is clean after retirement. Whether that later process then
     * starts is a <b>cross-process</b> property: this JVM's fence signal is shared per fence file and
     * a local fence is never reset, so a second authority in the same JVM is refused for that reason
     * (the real second process is covered by the child-JVM ITs).
     */
    @Test void theTreeIsCleanForTheNextProcessAfterRetirement() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var first = supply.authority();
        first.validateStartup();
        UUID operation = first.registerOperation();
        first.retire();
        var second = new RecoveryFenceAuthority(supply.roots());
        assertThatThrownBy(second::validateStartup).hasMessageContaining("RECOVERY_LEASE_OUTSTANDING");
        assertThat(Files.exists(supply.roots().root().resolve("leases").resolve(operation + ".json"))).isFalse();
        assertThat(indexed(supply)).isEmpty();

        var later = new RecoveryFenceAuthority(supply.roots());
        assertThatThrownBy(later::validateStartup)
            .as("this JVM already fenced its shared signal; only a fresh process may start")
            .hasMessageContaining("RECOVERY_LEASE_FENCED");
    }

    @Test void aRecordDeletedAfterPublicationIsRefusedWithoutRepairingTheIndex() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var first = supply.authority();
        first.validateStartup();
        UUID operation = first.registerOperation();
        first.retire();
        Files.delete(supply.roots().root().resolve("leases").resolve(operation + ".json"));

        var second = new RecoveryFenceAuthority(supply.roots());
        assertThatThrownBy(second::validateStartup).hasMessageContaining("RECOVERY_LEASE_OUTSTANDING");
        assertThat(indexed(supply)).as("deleted-record evidence is never erased")
            .contains(operation.toString());
    }

    @Test void aLeftoverPendingRecordIsRetiredAsAnOrphan() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var first = supply.authority();
        first.validateStartup();
        Path pending = supply.roots().root().resolve("leases").resolve(UUID.randomUUID() + ".json.pending");
        Files.writeString(pending, "{\"formatVersion\":1}");

        var second = new RecoveryFenceAuthority(supply.roots());
        assertThatThrownBy(second::validateStartup).hasMessageContaining("RECOVERY_LEASE_OUTSTANDING");
        assertThat(Files.exists(pending)).as("a half-written record is retired, never adopted").isFalse();
    }

    private static List<String> indexed(FenceTestSupply supply) throws Exception {
        var value = RecoveryControlHistory.read(supply.roots().root().resolve("leases").resolve("index.json"));
        var entries = new ArrayList<String>();
        value.path("entries").forEach(entry -> entries.add(entry.asText()));
        return entries;
    }

    private static RecoveryControlFixture control() throws Exception {
        return RecoveryWriterLeaseTest.protocol();
    }

    private static RecoveryOperationRegistry registry(List<UUID> published) throws Exception {
        var fixture = control();
        return new RecoveryOperationRegistry(fixture.location(), fixture.checkpoint, () -> {
            UUID id = UUID.randomUUID();
            published.add(id);
            return id;
        });
    }

    private static RecoveryOperationToken token(RecoveryOperationRegistry registry) {
        return new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
            registry.startup().installationId(), registry.startup().recordSha256(), null);
    }
}
