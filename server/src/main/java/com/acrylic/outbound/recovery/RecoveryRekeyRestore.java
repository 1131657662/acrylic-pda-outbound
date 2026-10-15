package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.Arrays;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit rollback of a signed, fully known offline rotation; never starts or unfences services. */
final class RecoveryRekeyRestore {
    record Result(boolean alreadyOriginal, String markerSha256, String evidenceDirectory, int recordCount) { }
    private RecoveryRekeyRestore() { }

    static Result restore(RecoveryRekeyRestorePlan.Input input, RecoveryRekeySecrets secrets) throws IOException {
        return RecoveryControlLock.lockedWaiting(input.request().binding().location().root(), 10000,
            () -> coordinated(input, secrets));
    }

    private static Result coordinated(RecoveryRekeyRestorePlan.Input input, RecoveryRekeySecrets secrets) throws IOException {
        var plan = RecoveryRekeyRestorePlan.load(input, secrets); var roots = plan.roots();
        Path fence = roots.root().resolve("fenced.lock");
        try (var channel = FileChannel.open(fence, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            try (var lock = channel.tryLock(0, 1, false)) {
                require(lock != null, "RECOVERY_RESTORE_FENCED");
                RecoveryLeaseRoots.checkFile(fence, text(plan.marker(), "fenceFileIdentity"), "FENCE");
                if (plan.checkCurrent(secrets.newAnchor)) return new Result(true, hash(plan.marker()), "", plan.entries().size());
                Path evidence = snapshot(plan, channel);
                try {
                    // Recheck each known input after snapshot, still under both original native locks.
                    plan.checkCurrent(secrets.newAnchor);
                    for (var entry : plan.entries()) replace(entry);
                    roots.verify();
                    for (var entry : plan.entries()) require(RecoveryControlHistory.read(entry.path()).equals(entry.original()),
                        "RECOVERY_RESTORE_READBACK");
                    return new Result(false, hash(plan.marker()), evidence.toString(), plan.entries().size());
                } catch (IOException | RuntimeException failure) {
                    throw new RecoveryRekeyRestoreFailure(evidence, failure);
                }
            } catch (OverlappingFileLockException busy) { throw new RecoveryControlException("RECOVERY_RESTORE_FENCED", busy); }
        }
    }

    private static Path snapshot(RecoveryRekeyRestorePlan plan, FileChannel fence) throws IOException {
        Path root = plan.roots().root(), evidence = Files.createTempDirectory(root.getParent(), ".rekey-restore-before-");
        for (var entry : plan.entries()) {
            Path target = evidence.resolve(root.relativize(entry.path())); Files.createDirectories(target.getParent());
            Files.copy(entry.path(), target, LinkOption.NOFOLLOW_LINKS);
            require(RecoveryControlHistory.read(target).equals(RecoveryControlHistory.read(entry.path())), "RECOVERY_RESTORE_SNAPSHOT");
        }
        var buffer = ByteBuffer.allocate(1); require(fence.read(buffer, 0) == 1, "RECOVERY_RESTORE_FENCE_READ");
        require(Arrays.equals(buffer.array(), new byte[]{48}), "RECOVERY_RESTORE_UNKNOWN_FENCE");
        Files.write(evidence.resolve("fenced.lock"), buffer.array(), StandardOpenOption.CREATE_NEW);
        return evidence;
    }

    private static void replace(RecoveryRekeyRestorePlan.Entry entry) throws IOException {
        Path pending = entry.path().resolveSibling(entry.path().getFileName() + ".restore.pending");
        Files.writeString(pending, canonical(entry.original()), StandardOpenOption.CREATE_NEW);
        // A failed rename or process kill retains pending and snapshot; there is no automatic retry/rollback.
        Files.move(pending, entry.path(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
