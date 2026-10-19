package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real files/authority; interrupted primitive stages are explicit crash-window models. */
class RecoveryLeaseRetirementTest {
    @TempDir Path parent;

    @Test void ownerRetiresOneRecordAndPreservesOtherLiveOperationsAndProcessEvidence() throws Exception {
        var supply = FenceTestSupply.create(parent); var authority = supply.authority(); authority.validateStartup();
        UUID first = authority.registerOperation(), second = authority.registerOperation();
        byte[] owner;
        Path process;
        try (var files = Files.list(supply.roots().root().resolve("processes"))) { process = files.findFirst().orElseThrow(); }
        owner = Files.readAllBytes(process);
        assertThat(authority.retireOperation(first)).isTrue();
        assertThat(record(supply, first)).doesNotExist(); assertThat(record(supply, second)).exists();
        assertThat(Files.readAllBytes(process)).isEqualTo(owner);
        assertThat(authority.retireOperation(second)).isTrue();
        authority.checkEffect();
        assertThat(index(supply).path("entries")).isEmpty();
        assertThatThrownBy(() -> authority.retireOperation(first)).hasMessage("RECOVERY_LEASE_OUTSTANDING");
        authority.retire();
    }

    @Test void coordinatorBusyDoesNotFenceAuthorityAndCanBeRetried() throws Exception {
        var supply = FenceTestSupply.create(parent); var authority = supply.authority(); authority.validateStartup();
        UUID operation = authority.registerOperation();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var holder = pool.submit(() -> RecoveryControlLock.locked(supply.roots().authorityRoot(), () -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new java.io.IOException("lock holder timeout");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted);
                }
                return null;
            }));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> authority.retireOperation(operation)).hasMessage("CONTROL_COORDINATOR_BUSY");
            release.countDown(); holder.get(10, TimeUnit.SECONDS);
        }
        assertThat(authority.retireOperation(operation)).isTrue();
        authority.retire();
    }

    @Test void leaseChannelRetriesAcrossMultipleCoordinatorBusyWindows() throws Exception {
        var supply = FenceTestSupply.create(parent); var authority = supply.authority(); authority.validateStartup();
        UUID operation = authority.registerOperation();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor();
             var timer = Executors.newSingleThreadScheduledExecutor()) {
            var holder = pool.submit(() -> RecoveryControlLock.locked(supply.roots().authorityRoot(), () -> {
                entered.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new java.io.IOException("lock holder timeout");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted);
                }
                return null;
            }));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            timer.schedule(release::countDown, 4, TimeUnit.SECONDS);
            var provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(authority);
            var channel = new RecoveryAuthorityLeaseChannel(provider);
            assertThat(channel.retire(operation)).isTrue();
            holder.get(10, TimeUnit.SECONDS);
        }
        authority.retire();
    }

    @Test void lifecycleRetirementDefersUntilTheLastOperationIsRetired() throws Exception {
        var supply = FenceTestSupply.create(parent); var authority = supply.authority(); authority.validateStartup();
        UUID operation = authority.registerOperation();
        authority.retire();
        assertThat(record(supply, operation)).exists();
        assertThat(authority.retireOperation(operation)).isTrue();
        assertThat(record(supply, operation)).doesNotExist();
        authority.retire();
    }

    @Test void aForeignAuthorityCannotRetireTheOwnersRecord() throws Exception {
        var supply = FenceTestSupply.create(parent); var owner = supply.authority(); owner.validateStartup();
        UUID id = owner.registerOperation(); var other = supply.authority(); other.validateStartup();
        assertThatThrownBy(() -> other.retireOperation(id)).hasMessage("RECOVERY_LEASE_OUTSTANDING");
        assertThat(record(supply, id)).exists();
        assertThat(owner.retireOperation(id)).isTrue(); owner.retire(); other.retire();
    }

    @Test void interruptedRenameRemainsRefusedWithoutRepair() throws Exception { interrupted(false); }
    @Test void interruptedIndexUpdateRemainsRefusedWithoutRepair() throws Exception { interrupted(true); }

    @Test void actualIndexReplaceFailurePreservesTheBlockingMarker() throws Exception {
        var supply = FenceTestSupply.create(parent); var authority = supply.authority(); authority.validateStartup();
        UUID id = authority.registerOperation(); Path record = record(supply, id);
        RecoveryControlLock.locked(supply.roots().authorityRoot(), () -> {
            RecoveryLeaseRetirement.stage(record);
            Files.createDirectory(record.resolveSibling("index.json.pending"));
            assertThatThrownBy(() -> RecoveryLeaseRetirement.removeIndexEntry(supply.roots(), id)).isInstanceOf(java.io.IOException.class);
            return null;
        });
        assertThat(record.resolveSibling(id + ".json.retiring")).exists();
        assertThat(index(supply).path("entries").toString()).contains(id.toString());
        assertThatThrownBy(authority::checkEffect).hasMessageContaining("RECOVERY_LEASE_OUTSTANDING");
        authority.retire();
    }

    private void interrupted(boolean updateIndex) throws Exception {
        var supply = FenceTestSupply.create(parent); var authority = supply.authority(); authority.validateStartup();
        UUID id = authority.registerOperation(); Path record = record(supply, id);
        RecoveryControlLock.locked(supply.roots().authorityRoot(), () -> {
            RecoveryLeaseRetirement.stage(record);
            if (updateIndex) RecoveryLeaseRetirement.removeIndexEntry(supply.roots(), id);
            return null;
        });
        byte[] before = Files.readAllBytes(supply.roots().root().resolve("leases/index.json"));
        assertThatThrownBy(authority::checkEffect).hasMessageContaining("RECOVERY_LEASE_OUTSTANDING");
        assertThat(record.resolveSibling(id + ".json.retiring")).exists();
        assertThat(Files.readAllBytes(supply.roots().root().resolve("leases/index.json"))).isEqualTo(before);
        authority.retire();
    }

    private static Path record(FenceTestSupply supply, UUID id) { return supply.roots().root().resolve("leases/" + id + ".json"); }
    private static com.fasterxml.jackson.databind.JsonNode index(FenceTestSupply supply) throws Exception {
        return RecoveryControlHistory.read(supply.roots().root().resolve("leases/index.json"));
    }
}
