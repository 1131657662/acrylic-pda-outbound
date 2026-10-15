package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Process records are immutable; lifecycle locks occupy a byte beyond the JSON payload. */
final class RecoveryLeaseProcess {
    static final long LOCK_BYTE = 1L << 30;
    private static final Map<Path, RecoveryLeaseProcess> LOCAL = new HashMap<>();
    final UUID id;
    final Path path;
    final String nativeIdentity;
    final JsonNode record;
    private final FileChannel channel;
    private final FileLock lock;

    private RecoveryLeaseProcess(UUID id, Path path, String identity, JsonNode record, FileChannel channel, FileLock lock) {
        this.id = id; this.path = path; this.nativeIdentity = identity; this.record = record;
        this.channel = channel; this.lock = lock;
    }

    static synchronized RecoveryLeaseProcess open(RecoveryLeaseRoots roots) throws IOException {
        RecoveryControlLock.requireOwner(roots.authorityRoot());
        UUID id = UUID.randomUUID();
        Path path = roots.root().resolve("processes").resolve(id + ".json");
        var value = JsonNodeFactory.instance.objectNode().put("formatVersion", 1)
            .put("installationId", roots.installation()).put("processIdentity", id.toString())
            .put("identitySha256", roots.identityHash()).put("pid", ProcessHandle.current().pid())
            .put("jvmVersion", System.getProperty("java.version")).put("createdAt", now());
        value.put("anchorTag", RecoveryControlFormat.hmac(roots.anchor(), value));
        Files.writeString(path, canonical(value), StandardOpenOption.CREATE_NEW);
        String identity = RecoveryWriterProtocol.fileIdentity(path);
        var channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            require(identity.equals(RecoveryWriterProtocol.fileIdentity(path)), "RECOVERY_LEASE_OUTSTANDING");
            var lock = channel.tryLock(LOCK_BYTE, 1, false);
            require(lock != null, "RECOVERY_LEASE_OUTSTANDING");
            var process = new RecoveryLeaseProcess(id, path, identity, value, channel, lock);
            LOCAL.put(path, process); return process;
        } catch (IOException | RuntimeException error) { channel.close(); throw error; }
    }

    static synchronized boolean alive(Path path, String identity, String digest, RecoveryLeaseRoots roots) throws IOException {
        RecoveryControlPaths.file(path);
        require(identity.equals(RecoveryWriterProtocol.fileIdentity(path)), "RECOVERY_LEASE_OUTSTANDING");
        var local = LOCAL.get(path);
        // A record whose lifecycle lock this JVM already released belongs to a process that retired:
        // it is gone, so its leases are orphans. Reading a released descriptor would only report the
        // closed channel, which is not an answer about liveness.
        if (local != null && !local.lock.isValid()) return false;
        JsonNode value = local == null ? RecoveryControlHistory.read(path) : local.readHeld();
        validate(value, path, digest, roots);
        if (local != null) {
            // The cache is keyed by path: a file replaced at that path is a different file whose
            // lifecycle lock nobody holds. Never vouch for it from the descriptor we locked earlier.
            require(local.nativeIdentity.equals(identity), "RECOVERY_LEASE_OUTSTANDING");
            return local.lock.isValid(); // Never open/close a second local descriptor.
        }
        try (var channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            require(identity.equals(RecoveryWriterProtocol.fileIdentity(path)), "RECOVERY_LEASE_OUTSTANDING");
            try (var probe = channel.tryLock(LOCK_BYTE, 1, false)) { return probe == null; }
            catch (OverlappingFileLockException unknown) {
                throw new RecoveryControlException("RECOVERY_LEASE_OUTSTANDING", unknown);
            }
        }
    }

    private static void validate(JsonNode value, Path path, String digest, RecoveryLeaseRoots roots) {
        fields(value, "formatVersion installationId processIdentity identitySha256 pid jvmVersion createdAt anchorTag"); version(value);
        RecoveryControlFormat.anchorTag(roots.anchor(), value);
        require(hash(value).equals(digest) && text(value, "installationId").equals(roots.installation())
            && text(value, "identitySha256").equals(roots.identityHash())
            && path.getFileName().toString().equals(uuid(value, "processIdentity") + ".json"), "RECOVERY_LEASE_OUTSTANDING");
        require(value.path("pid").canConvertToLong() && value.path("pid").asLong() > 0, "RECOVERY_LEASE_OUTSTANDING");
        text(value, "jvmVersion"); instant(value, "createdAt");
    }

    static synchronized void verifyRetained(Path path, RecoveryLeaseRoots roots) throws IOException {
        RecoveryControlPaths.file(path);
        var local = LOCAL.get(path);
        boolean held = local != null && local.lock.isValid();
        if (held) require(local.nativeIdentity.equals(RecoveryWriterProtocol.fileIdentity(path)), "RECOVERY_LEASE_OUTSTANDING");
        // Closing a second descriptor can release POSIX lifecycle locks held by this JVM.
        var value = held ? local.readHeld() : RecoveryControlHistory.read(path);
        validate(value, path, hash(value), roots);
    }

    private JsonNode readHeld() throws IOException {
        var buffer = ByteBuffer.allocate(RecoveryControlJson.MAX_BYTES + 1);
        int count = channel.read(buffer, 0);
        require(count >= 0, "RECOVERY_LEASE_OUTSTANDING");
        return readCanonical(Arrays.copyOf(buffer.array(), count));
    }

    void releaseLock() throws IOException { lock.release(); }
    void closeChannel() throws IOException { channel.close(); }
    static String now() { return Instant.now().truncatedTo(ChronoUnit.MICROS).toString(); }
}
