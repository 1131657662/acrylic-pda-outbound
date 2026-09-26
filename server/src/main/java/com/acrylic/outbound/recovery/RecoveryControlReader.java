package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.Path;

/** Explicit read-only consumer. Does not initialize, repair or grant write admission. */
public final class RecoveryControlReader {
    private final RecoveryControlLocation configured;

    public RecoveryControlReader(RecoveryControlLocation location) {
        this.configured = location;
    }

    public RecoveryControlSnapshot read(Path trustedCheckpoint) {
        try {
            var location = configured.checked();
            Path trusted = location.checkpoint(trustedCheckpoint);
            return RecoveryControlLock.locked(location.root(), () -> RecoveryControlHistory.load(location, trusted));
        } catch (IOException error) {
            throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error);
        }
    }
}
