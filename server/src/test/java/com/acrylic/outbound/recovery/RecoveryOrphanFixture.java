package com.acrylic.outbound.recovery;

import java.util.UUID;

/** File/OS fixture: release an owner's actual lifecycle lock with a published lease outstanding.
 * This models owner death; graceful authority.retire() deliberately waits for active operations.
 * It is a unit fixture, not a real process-crash or database acceptance result.
 */
final class RecoveryOrphanFixture {
    private RecoveryOrphanFixture() { }

    static UUID publishFromDeadOwner(FenceTestSupply supply) throws Exception {
        return RecoveryControlLock.locked(supply.roots().authorityRoot(), () -> {
            var process = RecoveryLeaseProcess.open(supply.roots());
            try {
                return RecoveryLeaseScan.publish(supply.roots(), process);
            } finally {
                try { process.releaseLock(); }
                finally { process.closeChannel(); }
            }
        });
    }
}
