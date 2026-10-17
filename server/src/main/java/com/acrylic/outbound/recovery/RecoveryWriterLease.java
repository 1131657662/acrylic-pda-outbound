package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** One open descriptor per JVM/file identity; each admitted writer owns an independent reference. */
final class RecoveryWriterLease implements AutoCloseable {
    private static final Map<String, Held> HELD = new HashMap<>();
    private final String identity;
    private final Held held;
    private boolean closed;
    private boolean referenceDropped;

    private RecoveryWriterLease(String identity, Held held) { this.identity = identity; this.held = held; }

    static synchronized RecoveryWriterLease acquire(Path root) throws IOException {
        return acquire(root, true);
    }

    static synchronized RecoveryWriterLease acquireClosed(Path root) throws IOException {
        return acquire(root, false);
    }

    private static RecoveryWriterLease acquire(Path root, boolean shared) throws IOException {
        RecoveryControlLock.requireOwner(root);
        RecoveryWriterProtocol.validate(root, true);
        RecoveryControlIntents.requireAdmission(root);
        Path path = RecoveryControlPaths.file(root.resolve("writers.lock"));
        String key = key(path);
        Held held = HELD.get(key);
        if (held != null) {
            require(shared && held.shared, "CONTROL_WRITERS_ACTIVE");
            require(held.lock.isValid() && held.key.equals(key), "CONTROL_WRITER_LOCK_REPLACED");
            require(!held.poisoned, "CONTROL_WRITERS_ACTIVE");
            held.references++;
            return new RecoveryWriterLease(key, held);
        }
        held = open(path, key, shared); HELD.put(key, held);
        return new RecoveryWriterLease(key, held);
    }

    private static Held open(Path path, String key, boolean shared) throws IOException {
        var channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            require(channel.size() == 1 && key.equals(key(path)), "CONTROL_WRITER_LOCK_REPLACED");
            var lock = channel.tryLock(0, 1, shared);
            require(lock != null, "CONTROL_WRITERS_ACTIVE");
            return new Held(channel, lock, key, shared);
        } catch (IOException | RuntimeException error) {
            channel.close(); throw error;
        }
    }

    private static String key(Path path) throws IOException {
        return RecoveryWriterProtocol.fileIdentity(path);
    }

    @Override public void close() throws IOException {
        synchronized (RecoveryWriterLease.class) {
            if (closed) return;
            if (held.poisoned) return;
            if (!referenceDropped) { held.references--; referenceDropped = true; }
            if (held.references != 0) { closed = true; return; }
            // Keep the descriptor registered until release and channel close are both confirmed.
            if (held.lock.isValid()) held.lock.release();
            held.channel.close();
            require(!held.lock.isValid() && !held.channel.isOpen(), "CONTROL_WRITER_RELEASE_UNCONFIRMED");
            HELD.remove(identity);
            closed = true;
        }
    }

    /**
     * One independent reference to the same held descriptor, so a root that outlives a single
     * connection can keep the shared writer lease while each borrowed connection releases its own.
     * Refused once this handle was released or the descriptor was poisoned.
     */
    RecoveryWriterLease reference() {
        synchronized (RecoveryWriterLease.class) {
            require(!closed && !referenceDropped && held.lock.isValid() && HELD.get(identity) == held,
                "CONTROL_WRITER_LOCK_REPLACED");
            require(!held.poisoned, "CONTROL_WRITERS_ACTIVE");
            held.references++;
            return new RecoveryWriterLease(identity, held);
        }
    }

    void poison() { synchronized (RecoveryWriterLease.class) { held.poisoned = true; } }

    boolean confirmed() {
        synchronized (RecoveryWriterLease.class) {
            return !closed && !held.poisoned && held.lock.isValid() && HELD.get(identity) == held;
        }
    }

    private static final class Held {
        final FileChannel channel;
        final FileLock lock;
        final String key;
        int references = 1;
        boolean poisoned;
        final boolean shared;
        Held(FileChannel channel, FileLock lock, String key, boolean shared) {
            this.channel = channel; this.lock = lock; this.key = key; this.shared = shared;
        }
    }
}
