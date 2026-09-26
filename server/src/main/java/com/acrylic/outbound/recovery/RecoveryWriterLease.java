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

    private RecoveryWriterLease(String identity, Held held) { this.identity = identity; this.held = held; }

    static synchronized RecoveryWriterLease acquire(Path root) throws IOException {
        RecoveryControlLock.requireOwner(root);
        RecoveryWriterProtocol.validate(root, true);
        RecoveryControlIntents.requireAdmission(root);
        Path path = RecoveryControlPaths.file(root.resolve("writers.lock"));
        String key = key(path);
        Held held = HELD.get(key);
        if (held != null) {
            require(held.lock.isValid() && held.key.equals(key), "CONTROL_WRITER_LOCK_REPLACED");
            require(!held.poisoned, "CONTROL_WRITERS_ACTIVE");
            held.references++;
            return new RecoveryWriterLease(key, held);
        }
        held = open(path, key); HELD.put(key, held);
        return new RecoveryWriterLease(key, held);
    }

    private static Held open(Path path, String key) throws IOException {
        var channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            require(channel.size() == 1 && key.equals(key(path)), "CONTROL_WRITER_LOCK_REPLACED");
            var lock = channel.tryLock(0, 1, true);
            require(lock != null, "CONTROL_WRITERS_ACTIVE");
            return new Held(channel, lock, key);
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
            closed = true;
            if (held.poisoned) return;
            if (--held.references != 0) return;
            // Leave a poisoned entry if release/close cannot be confirmed; never admit a second descriptor.
            try { held.lock.release(); } finally { held.channel.close(); }
            HELD.remove(identity);
        }
    }

    void poison() { synchronized (RecoveryWriterLease.class) { held.poisoned = true; } }

    private static final class Held {
        final FileChannel channel;
        final FileLock lock;
        final String key;
        int references = 1;
        boolean poisoned;
        Held(FileChannel channel, FileLock lock, String key) {
            this.channel = channel; this.lock = lock; this.key = key;
        }
    }
}
