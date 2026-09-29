package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** One descriptor per native identity for this JVM. Shared fencing lives until process exit. */
final class RecoveryFenceLock {
    private static final Map<String, RecoveryFenceLock> SIGNALS = new HashMap<>();
    private FileChannel channel;
    private FileLock shared;
    private volatile boolean fenced;

    static synchronized RecoveryFenceLock forRoots(RecoveryLeaseRoots roots) throws IOException {
        String identity = text(roots.verify(), "fenceFileIdentity");
        return SIGNALS.computeIfAbsent(identity, ignored -> new RecoveryFenceLock());
    }
    void fenceLocal() { fenced = true; }
    void requireLocal() { require(!fenced, "RECOVERY_LEASE_FENCED"); }

    synchronized void probe(RecoveryLeaseRoots roots) throws IOException {
        requireLocal();
        FileChannel file = channel(roots);
        try (var lock = file.tryLock(0, 1, false)) {
            require(lock != null, "RECOVERY_LEASE_FENCED");
            roots.verify();
        } catch (OverlappingFileLockException error) {
            throw new RecoveryControlException("RECOVERY_LEASE_FENCED", error);
        }
    }

    synchronized void hold(RecoveryLeaseRoots roots) throws IOException {
        fenceLocal();
        FileChannel file = channel(roots);
        if (shared != null) { require(shared.isValid(), "CONTROL_FENCE_LOCK_UNCONFIRMED"); return; }
        for (int attempt = 0; attempt < 20; attempt++) {
            roots.verify();
            try { shared = file.tryLock(0, 1, true); }
            catch (OverlappingFileLockException error) {
                throw new RecoveryControlException("CONTROL_FENCE_LOCK_UNCONFIRMED", error);
            }
            if (shared != null) { roots.verify(); return; }
            try { Thread.sleep(10); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
        }
        throw new RecoveryControlException("CONTROL_FENCE_LOCK_UNCONFIRMED");
    }

    private FileChannel channel(RecoveryLeaseRoots roots) throws IOException {
        roots.verify();
        if (channel == null) {
            channel = FileChannel.open(roots.root().resolve("fenced.lock"),
                StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            roots.verify();
        }
        require(channel.isOpen(), "CONTROL_FENCE_LOCK_UNCONFIRMED");
        return channel;
    }
}
