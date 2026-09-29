package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/**
 * The control lock, one per root and ordered by layer. Two properties matter:
 *
 * <ul>
 *   <li><b>per root</b>: holding the authority root's lock no longer blocks a different root, so a
 *       thread can nest the fixed layer order {@code P3 -> P2 -> P5} (authority, initial-normal
 *       store, lease-root process file). A same-root acquisition is refused as misuse
 *       ({@code CONTROL_COORDINATOR_REENTRANT} when waiting, {@code CONTROL_COORDINATOR_BUSY} when
 *       immediate), and taking a layer that is not strictly higher than every lock this thread
 *       already holds is refused with {@code CONTROL_LOCK_ORDER_VIOLATION}.</li>
 *   <li><b>bounded wait</b>: {@link #lockedWaiting} waits for the root instead of failing at once,
 *       so concurrent admissions serialize instead of being refused. The wait is bounded
 *       ({@code 1..10000} ms), the cross-process file lock is retried within the same budget, and a
 *       budget that runs out is still a refusal ({@code CONTROL_COORDINATOR_BUSY}).</li>
 * </ul>
 *
 * <p>{@link #locked} keeps its immediate-failure semantics.
 */
final class RecoveryControlLock {
    /** Layer of the authority (P3) coordinator lock; the default for every existing caller. */
    static final int AUTHORITY_LAYER = 1;
    /** Layer of the initial-normal store (P2) coordinator lock, taken inside the authority layer. */
    static final int STORE_LAYER = 2;
    /** Layer of the lease-root process file (P5), transient, taken inside the store layer. */
    static final int LEASE_PROCESS_LAYER = 3;
    private static final long MAX_WAIT_MILLIS = 10_000L;
    private static final long POLL_NANOS = 1_000_000L;
    private static final Map<String, Semaphore> GATES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Map<String, Integer>> HELD = ThreadLocal.withInitial(LinkedHashMap::new);

    private RecoveryControlLock() { }

    @FunctionalInterface interface Read<T> { T run() throws IOException; }

    /** Immediate failure: the authority layer, exactly as before. */
    static <T> T locked(Path root, Read<T> read) throws IOException {
        return locked(AUTHORITY_LAYER, root, read);
    }

    static <T> T locked(int layer, Path root, Read<T> read) throws IOException {
        require(layer > 0, "CONTROL_LOCK_LAYER_RANGE");
        return acquire(layer, root, 0L, read);
    }

    /** Bounded wait on the authority layer. */
    static <T> T lockedWaiting(Path root, long timeoutMillis, Read<T> read) throws IOException {
        return lockedWaiting(AUTHORITY_LAYER, root, timeoutMillis, read);
    }

    static <T> T lockedWaiting(int layer, Path root, long timeoutMillis, Read<T> read) throws IOException {
        require(layer > 0, "CONTROL_LOCK_LAYER_RANGE");
        require(timeoutMillis >= 1 && timeoutMillis <= MAX_WAIT_MILLIS, "CONTROL_LOCK_TIMEOUT_RANGE");
        return acquire(layer, root, timeoutMillis, read);
    }

    /** The calling thread holds this root's lock; used by every component inside the critical section. */
    static void requireOwner(Path root) throws IOException {
        require(HELD.get().containsKey(key(root)), "CONTROL_COORDINATOR_REQUIRED");
    }

    /** The roots and layers the calling thread holds; observability for tests and diagnostics. */
    static Map<String, Integer> heldByCurrentThread() {
        return Map.copyOf(HELD.get());
    }

    private static <T> T acquire(int layer, Path root, long waitMillis, Read<T> read) throws IOException {
        Path path = RecoveryControlPaths.file(root.resolve("coordinator.lock"));
        String key = path.toString();
        Map<String, Integer> held = HELD.get();
        require(!held.containsKey(key), waitMillis > 0 ? "CONTROL_COORDINATOR_REENTRANT" : "CONTROL_COORDINATOR_BUSY");
        require(held.values().stream().allMatch(outer -> layer > outer), "CONTROL_LOCK_ORDER_VIOLATION");
        require(Files.size(path) == 1, "CONTROL_LOCK_INVALID");
        Semaphore gate = GATES.computeIfAbsent(key, ignored -> new Semaphore(1));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);
        require(enter(gate, waitMillis, deadline), "CONTROL_COORDINATOR_BUSY");
        try {
            try (var channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                try (var fileLock = awaitFileLock(channel, waitMillis, deadline)) {
                    held.put(key, layer);
                    try { return read.run(); }
                    finally { held.remove(key); if (held.isEmpty()) HELD.remove(); }
                }
            }
        } finally {
            gate.release();
        }
    }

    private static boolean enter(Semaphore gate, long waitMillis, long deadline) {
        if (waitMillis == 0) return gate.tryAcquire();
        try {
            return gate.tryAcquire(Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RecoveryControlException("CONTROL_COORDINATOR_BUSY", interrupted);
        }
    }

    private static FileLock awaitFileLock(FileChannel channel, long waitMillis, long deadline) throws IOException {
        while (true) {
            try {
                FileLock held = channel.tryLock(0, 1, false);
                if (held != null) return held;
            } catch (OverlappingFileLockException busy) {
                // Another channel of this JVM holds it; that is contention, not a different outcome.
            }
            require(waitMillis > 0 && System.nanoTime() < deadline, "CONTROL_COORDINATOR_BUSY");
            LockSupport.parkNanos(POLL_NANOS);
        }
    }

    private static String key(Path root) throws IOException {
        return RecoveryControlPaths.file(root.resolve("coordinator.lock")).toString();
    }
}
