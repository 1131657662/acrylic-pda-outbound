package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real cross-language lease harness; never adds application capability or fault switches. */
public final class RecoveryWriterProcess {
    public static void main(String[] args) throws Exception {
        try { execute(Path.of(args[0]), args[1]); }
        catch (RecoveryControlException error) { System.out.println(error.code()); System.exit(2); }
    }

    private static void execute(Path configFile, String mode) throws Exception {
        var config = RecoveryControlFormat.readCanonical(Files.readAllBytes(configFile));
        Path root = Path.of(config.path("root").textValue());
        var roots = new ArrayList<Path>(); config.path("restoreRoots").forEach(p -> roots.add(Path.of(p.textValue())));
        var location = new RecoveryControlLocation(root, UUID.fromString(config.path("installationId").textValue()), roots).checked();
        Path trusted = location.checkpoint(Path.of(config.path("checkpoint").textValue()));
        try (var first = acquire(location, trusted)) {
            if (mode.equals("refs") || mode.equals("threaded")) partialClose(location, trusted, mode);
            // Ordinary reads open coordinator/JSON descriptors only, never the lease inode.
            for (int i = 0; i < 3; i++) new RecoveryControlReader(location).read(trusted);
            System.out.println("LOCKED"); System.out.flush();
            if (!mode.equals("probe")) System.in.read();
            if (mode.equals("relocated")) {
                relocated(configFile, location, trusted);
                System.out.println("LOCKED"); System.out.flush(); System.in.read();
            }
            if (mode.equals("replace")) {
                try (var another = acquire(location, trusted)) { throw new AssertionError("Replacement admitted"); }
            }
        }
    }

    private static void relocated(Path configFile, RecoveryControlLocation location, Path trusted) throws Exception {
        var updated = RecoveryControlFormat.readCanonical(Files.readAllBytes(configFile));
        var moved = new RecoveryControlLocation(Path.of(updated.path("root").textValue()),
            location.installation(), location.restoreRoots()).checked();
        try (var temporary = acquire(moved, trusted)) { new RecoveryControlReader(moved).read(trusted); }
    }

    private static RecoveryWriterLease acquire(RecoveryControlLocation location, Path trusted) throws java.io.IOException {
        return RecoveryControlLock.locked(location.root(), () -> {
            RecoveryControlHistory.load(location, trusted, RecoveryControlPurpose.NORMAL_ADMISSION);
            return RecoveryWriterLease.acquire(location.root());
        });
    }

    private static void partialClose(RecoveryControlLocation location, Path trusted, String mode) throws Exception {
        var second = acquire(location, trusted);
        if (mode.equals("threaded")) {
            try (var pool = Executors.newSingleThreadExecutor()) {
                pool.submit(() -> { second.close(); return null; }).get(10, TimeUnit.SECONDS);
            }
        } else { second.close(); }
        second.close(); // A duplicate close must not release first's kernel lease.
    }
}
