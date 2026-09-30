package com.acrylic.outbound.recovery;

import java.io.IOException;

/**
 * W-2-d (ruling 2026-09-30, layer 1): a <b>read-only</b> window onto the recovery lease tree, so operations can see
 * whether orphan lease records exist without waiting for a protected call to fail.
 *
 * <p>What this is not: it is not a scan, it does not retire anything, and it does not grant any capability. The
 * refusal decision stays with {@link RecoveryFenceAuthority#checkEffect()} exactly as before; the numbers here only
 * describe what the next scan would look at. {@link RecoveryLeaseScan} keeps the authoritative decision, and if the
 * two ever disagreed the refusal would still follow the scan.
 *
 * <p>Why there is no timer: orphans are retired <b>inside the same {@code check} that finds them</b>, so the refusal
 * is already synchronous. A timer would only reduce the time between the owner dying and the next scan noticing,
 * which is a recovery-latency question (W) tied to the RPO/RTO policy - deliberately left off
 * ({@code outbound.recovery.lease.scan.enabled=false} in the design).
 */
public final class RecoveryLeaseVisibility {
    private RecoveryLeaseVisibility() { }

    /**
     * Counts what a scan would find right now, under the coordinator lock. Read-only: no file is created, rewritten
     * or deleted, and no state is cached.
     *
     * @param roots the explicit P3/P5 authority whose lease tree is observed
     * @return the snapshot; {@link RecoveryLeaseScan.Counts#outstanding()} tells whether the next scan would refuse
     * @throws IOException when the tree or the coordinator lock cannot be read at all
     */
    public static Snapshot counts(RecoveryLeaseRoots roots) throws IOException {
        var counts = RecoveryLeaseScan.counts(roots);
        RecoveryFenceAudit.event("RECOVERY_LEASE_VISIBILITY",
            "indexed=" + counts.indexedEntries() + "|indexMismatches=" + counts.indexMismatches()
                + "|orphans=" + counts.orphanRecords() + "|retirable=" + counts.retirableOrphans()
                + "|refused=" + counts.refusedOrphans());
        return new Snapshot(counts.indexedEntries(), counts.indexedMissingRecords(), counts.indexMismatches(),
            counts.orphanRecords(), counts.retirableOrphans(), counts.refusedOrphans());
    }

    /** The four counter families from the ruling, plus the two context numbers they are computed from. */
    public record Snapshot(int indexedEntries, int indexedMissingRecords, int indexMismatches, int orphansObserved,
                           int orphansRetired, int orphansRefused) {
        /** {@code orphansRefused} is the one that keeps a refusal standing; the other families are context. */
        public boolean outstanding() { return indexMismatches > 0 || orphansObserved > 0; }
    }
}
