package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.Path;

/** Post-snapshot uncertainty is never reported as a clean, write-free input refusal. */
final class RecoveryRekeyRestoreFailure extends IOException {
    final Path evidence;
    RecoveryRekeyRestoreFailure(Path evidence, Throwable cause) {
        super("RECOVERY_RESTORE_EVIDENCE_RETAINED", cause); this.evidence = evidence;
    }
}
