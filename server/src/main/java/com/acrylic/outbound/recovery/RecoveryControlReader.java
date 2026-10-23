package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.Path;

/** Explicit read-only consumer. Does not initialize, repair or grant write admission. */
public final class RecoveryControlReader {
    private final RecoveryControlLocation configured;
    private final long coordinatorWaitMillis;

    public RecoveryControlReader(RecoveryControlLocation location) {
        this(location, 0);
    }

    /** Explicit runtime capability; existing offline readers retain immediate refusal. */
    RecoveryControlReader(RecoveryControlLocation location, long coordinatorWaitMillis) {
        RecoveryControlFormat.require(coordinatorWaitMillis >= 0 && coordinatorWaitMillis <= 10_000,
            "CONTROL_LOCK_TIMEOUT_RANGE");
        this.configured = location;
        this.coordinatorWaitMillis = coordinatorWaitMillis;
    }

    public RecoveryControlSnapshot read(Path trustedCheckpoint) {
        try {
            var location = configured.checked();
            Path trusted = location.checkpoint(trustedCheckpoint);
            RecoveryControlLock.Read<RecoveryControlSnapshot> read = () ->
                RecoveryControlHistory.load(location, trusted, RecoveryControlPurpose.NORMAL_ADMISSION);
            return coordinatorWaitMillis == 0 ? RecoveryControlLock.locked(location.root(), read)
                : RecoveryControlLock.lockedWaiting(location.root(), coordinatorWaitMillis, read);
        } catch (IOException error) {
            throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error);
        }
    }
}
