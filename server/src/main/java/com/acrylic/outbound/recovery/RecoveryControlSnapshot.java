package com.acrylic.outbound.recovery;

import java.util.UUID;
import java.util.List;
import java.time.Instant;

/** Immutable, closed-state view of the latest external control record. */
public record RecoveryControlSnapshot(
        UUID installationId,
        String revision,
        UUID recoverySessionId,
        UUID epochId,
        String phase,
        String snapshotId,
        String manifestSha256,
        String recordSha256,
        String previousRecordSha256,
        List<String> affectedWarehouseIds,
        String evidenceSha256,
        Instant possibleLossFrom,
        Instant possibleLossThrough) {

    public RecoveryControlSnapshot {
        affectedWarehouseIds = List.copyOf(affectedWarehouseIds);
    }

    public boolean ordinaryWritesAllowed() {
        return false;
    }

    public boolean localDeletionAllowed() {
        return false;
    }
}
