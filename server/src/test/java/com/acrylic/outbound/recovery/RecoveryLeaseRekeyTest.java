package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RecoveryLeaseRekeyTest {
    @TempDir Path parent;

    @Test void rekeysRetainedProcessEvidenceWithoutPersistingEitherSecret() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var authority = supply.authority(); authority.validateStartup(); authority.retire();
        byte[] next = new byte[32]; Arrays.fill(next, (byte) 7);

        var result = RecoveryLeaseRekey.rekey(supply.roots(), next);
        assertThat(result.processRecords()).isEqualTo(1);
        assertThat(result.leaseRecords()).isZero();
        assertThat(result.oldAnchorId()).isNotEqualTo(result.newAnchorId());
        assertThat(Files.readString(supply.roots().root().resolve("lease-root.json")))
            .doesNotContain(RecoveryControlFormat.hex(next));

        var binding = binding(supply);
        var rotated = new RecoveryLeaseRoots(binding, result.markerSha256(), next);
        assertThat(rotated.verify()).isNotNull();
        assertThatThrownBy(() -> new RecoveryLeaseRoots(binding, result.markerSha256(), supply.roots().anchor()).verify())
            .hasMessage("CONTROL_ANCHOR_MISMATCH");
    }

    @Test void refusesRotationWhileAWriterIsActive() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var authority = supply.authority(); authority.validateStartup();
        byte[] next = new byte[32]; Arrays.fill(next, (byte) 8);
        try {
            assertThatThrownBy(() -> RecoveryLeaseRekey.rekey(supply.roots(), next))
                .hasMessage("RECOVERY_REKEY_ACTIVE_WRITER");
        } finally {
            authority.retire();
        }
    }

    @Test void refusesRotationWithAnIndexedLease() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var authority = supply.authority(); authority.validateStartup(); authority.registerOperation();
        byte[] next = new byte[32]; Arrays.fill(next, (byte) 9);
        try {
            assertThatThrownBy(() -> RecoveryLeaseRekey.rekey(supply.roots(), next))
                .hasMessage("RECOVERY_REKEY_ACTIVE_LEASE");
        } finally {
            authority.retire();
        }
    }

    @Test void restoresAllTargetsWhenACommitReplacementFails() throws Exception {
        var supply = FenceTestSupply.create(parent);
        var authority = supply.authority(); authority.validateStartup(); authority.retire();
        Path leaseRoot = supply.roots().root();
        Path process = Files.list(leaseRoot.resolve("processes")).findFirst().orElseThrow();
        Path index = leaseRoot.resolve("leases").resolve("index.json");
        Path marker = leaseRoot.resolve("lease-root.json");
        String processBefore = Files.readString(process);
        String indexBefore = Files.readString(index);
        String markerBefore = Files.readString(marker);
        Path pending = marker.resolveSibling(marker.getFileName() + ".rekey.pending");
        Files.writeString(pending, "occupied");
        byte[] next = new byte[32]; Arrays.fill(next, (byte) 6);

        assertThatThrownBy(() -> RecoveryLeaseRekey.rekey(supply.roots(), next))
            .isInstanceOf(FileAlreadyExistsException.class);

        assertThat(Files.readString(process)).isEqualTo(processBefore);
        assertThat(Files.readString(index)).isEqualTo(indexBefore);
        assertThat(Files.readString(marker)).isEqualTo(markerBefore);
        assertThat(Files.readString(pending)).isEqualTo("occupied");
    }

    private static RecoveryLeaseRoots.Binding binding(FenceTestSupply supply) throws Exception {
        Path authority = supply.roots().authorityRoot();
        JsonNode identity = RecoveryControlHistory.read(authority.resolve("identity.json"));
        Path business = supply.base().resolve("business").toRealPath();
        var location = new RecoveryControlLocation(authority, UUID.fromString(supply.roots().installation()), List.of(business));
        return new RecoveryLeaseRoots.Binding(location, supply.roots().root(), supply.base().resolve("closed"),
            RecoveryControlFormat.hash(identity));
    }
}
