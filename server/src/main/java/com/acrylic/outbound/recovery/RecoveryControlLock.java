package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.Semaphore;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** One JVM opener prevents any second descriptor close from dropping POSIX locks. */
final class RecoveryControlLock {
    private static final Semaphore LOCAL = new Semaphore(1);
    private static final ThreadLocal<Path> OWNER = new ThreadLocal<>();
    private RecoveryControlLock() { }
    @FunctionalInterface interface Read<T> { T run() throws IOException; }

    static <T> T locked(Path root, Read<T> read) throws IOException {
        require(LOCAL.tryAcquire(), "CONTROL_COORDINATOR_BUSY");
        try { return acquire(root, read); }
        finally { LOCAL.release(); }
    }

    private static <T> T acquire(Path root, Read<T> read) throws IOException {
        Path path = RecoveryControlPaths.file(root.resolve("coordinator.lock"));
        require(Files.size(path) == 1, "CONTROL_LOCK_INVALID");
        try (var channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            try (var held = channel.tryLock(0, 1, false)) {
                require(held != null, "CONTROL_COORDINATOR_BUSY");
                OWNER.set(root.toRealPath());
                try { return read.run(); }
                finally { OWNER.remove(); }
            } catch (OverlappingFileLockException busy) {
                throw new RecoveryControlException("CONTROL_COORDINATOR_BUSY", busy);
            }
        }
    }

    static void requireOwner(Path root) throws IOException {
        require(root.toRealPath().equals(OWNER.get()), "CONTROL_COORDINATOR_REQUIRED");
    }
}
