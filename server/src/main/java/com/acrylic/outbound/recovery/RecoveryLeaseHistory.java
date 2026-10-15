package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Startup evidence validation; never repairs or removes retained files. */
final class RecoveryLeaseHistory {
    private RecoveryLeaseHistory() { }

    static void check(RecoveryLeaseRoots roots) throws IOException {
        RecoveryControlLock.requireOwner(roots.authorityRoot());
        try (var paths = Files.list(roots.root())) {
            for (Path path : paths.toList())
                require(Set.of("lease-root.json", "fenced.lock", "processes", "leases").contains(path.getFileName().toString()),
                    "RECOVERY_REKEY_INCOMPLETE");
        }
        try (var paths = Files.list(roots.root().resolve("processes"))) {
            for (Path path : paths.toList()) {
                require(path.getFileName().toString().matches("[0-9a-f-]{36}\\.json"), "RECOVERY_REKEY_INCOMPLETE");
                RecoveryLeaseProcess.verifyRetained(path, roots);
            }
        }
    }
}
