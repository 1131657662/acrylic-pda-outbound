package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Normal P5 retirement under P3. Every interrupted intermediate tree remains refusing. */
final class RecoveryLeaseRetirement {
    private RecoveryLeaseRetirement() { }

    static Path decide(RecoveryLeaseRoots roots, RecoveryLeaseProcess process, UUID operation) throws IOException {
        RecoveryControlLock.requireOwner(roots.authorityRoot());
        Path record = roots.root().resolve("leases").resolve(operation + ".json");
        var value = RecoveryControlHistory.read(record);
        anchorTag(roots.anchor(), value);
        require(operation.toString().equals(text(value, "operationId"))
            && process.id.toString().equals(text(value, "processIdentity"))
            && process.nativeIdentity.equals(text(value, "processFileIdentity"))
            && hash(process.record).equals(text(value, "processSha256")), "RECOVERY_LEASE_OUTSTANDING");
        require(RecoveryLeaseProcess.alive(process.path, process.nativeIdentity, hash(process.record), roots),
            "RECOVERY_LEASE_OUTSTANDING");
        String digest = hash(value);
        stage(record);
        require(hash(RecoveryControlHistory.read(marker(record))).equals(digest), "RECOVERY_LEASE_OUTSTANDING");
        removeIndexEntry(roots, operation);
        require(Files.notExists(record, LinkOption.NOFOLLOW_LINKS), "RECOVERY_LEASE_OUTSTANDING");
        return record;
    }

    // A .retiring record is deliberately not a .pending orphan: no scan may erase this disagreement.
    static void stage(Path record) throws IOException {
        require(Files.notExists(marker(record), LinkOption.NOFOLLOW_LINKS), "RECOVERY_LEASE_OUTSTANDING");
        Files.move(record, marker(record), StandardCopyOption.ATOMIC_MOVE);
        require(Files.notExists(record, LinkOption.NOFOLLOW_LINKS), "RECOVERY_LEASE_OUTSTANDING");
    }

    static void removeIndexEntry(RecoveryLeaseRoots roots, UUID operation) throws IOException {
        RecoveryControlLock.requireOwner(roots.authorityRoot());
        var index = RecoveryLeaseScan.readIndex(roots);
        var kept = index.arrayNode();
        int removed = 0;
        for (var entry : index.path("entries")) {
            if (operation.toString().equals(entry.asText())) removed++;
            else kept.add(entry);
        }
        require(removed == 1, "RECOVERY_LEASE_OUTSTANDING");
        index.set("entries", kept);
        index.remove("indexTag");
        index.put("indexTag", hmac(roots.anchor(), index));
        Path pending = roots.root().resolve("leases/index.json.pending");
        try (var channel = FileChannel.open(pending, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var bytes = ByteBuffer.wrap(canonical(index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        Files.move(pending, pending.resolveSibling("index.json"), StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
        require(RecoveryLeaseScan.readIndex(roots).equals(index), "RECOVERY_LEASE_OUTSTANDING");
    }

    static void finish(Path record) throws IOException {
        Files.delete(marker(record));
        require(Files.notExists(record, LinkOption.NOFOLLOW_LINKS)
            && Files.notExists(marker(record), LinkOption.NOFOLLOW_LINKS), "RECOVERY_LEASE_OUTSTANDING");
    }

    private static Path marker(Path record) { return record.resolveSibling(record.getFileName() + ".retiring"); }
}
