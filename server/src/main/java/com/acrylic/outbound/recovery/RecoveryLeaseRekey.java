package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Offline, stop-the-world P5 anchor rotation. Secrets are supplied by the caller and never written. */
public final class RecoveryLeaseRekey {
    private static final long LOCK_WAIT_MILLIS = 10_000L;

    public record Result(String oldAnchorId, String newAnchorId, String markerSha256,
                         int processRecords, int leaseRecords, String evidenceDirectory, String inventorySha256) { }

    private RecoveryLeaseRekey() { }

    /** Rekeys retained process evidence and the empty lease index under a held maintenance window. */
    public static Result rekey(RecoveryLeaseRoots roots, byte[] newAnchor) throws IOException {
        require(newAnchor != null && newAnchor.length == 32, "CONTROL_ANCHOR_REQUIRED");
        byte[] oldAnchor = roots.anchor();
        require(!MessageDigest.isEqual(oldAnchor, newAnchor), "RECOVERY_REKEY_SAME_ANCHOR");
        return RecoveryControlLock.lockedWaiting(roots.authorityRoot(), LOCK_WAIT_MILLIS,
            () -> rotate(roots, oldAnchor, newAnchor));
    }

    private static Result rotate(RecoveryLeaseRoots roots, byte[] oldAnchor, byte[] newAnchor) throws IOException {
        roots.verify();
        Path root = roots.root();
        var index = readEmptyIndex(root.resolve("leases").resolve("index.json"), roots, oldAnchor);
        var processPaths = processPaths(root.resolve("processes"));
        require(processPaths.size() <= RecoveryRekeyInventory.MAX_RECORDS - 2, "RECOVERY_REKEY_INVENTORY_LIMIT");
        var rewritten = new LinkedHashMap<Path, JsonNode>();
        for (Path path : processPaths) rewritten.put(path, rewriteProcess(path, roots, oldAnchor, newAnchor));
        var marker = rewriteMarker(root.resolve("lease-root.json"), newAnchor);
        var targets = new ArrayList<Path>(rewritten.keySet());
        targets.add(root.resolve("leases").resolve("index.json"));
        targets.add(root.resolve("lease-root.json"));
        Path backup = Files.createTempDirectory(root.getParent(), ".rekey-backup-");
        boolean replacementsStarted = false;
        try {
            backup(targets, root, backup);
            String inventory = RecoveryRekeyInventory.seal(new RecoveryRekeyInventory.Input(roots, oldAnchor, newAnchor), backup, targets);
            replacementsStarted = true;
            for (var entry : rewritten.entrySet()) replace(entry.getKey(), entry.getValue());
            replace(root.resolve("leases").resolve("index.json"), indexWithAnchor(index, newAnchor));
            replace(root.resolve("lease-root.json"), marker);
            return new Result(sha256(oldAnchor), sha256(newAnchor), hash(marker), processPaths.size(), 0, backup.toString(), inventory);
        } catch (IOException | RuntimeException failure) {
            if (replacementsStarted) restore(targets, root, backup, failure);
            failure.addSuppressed(new IOException("RECOVERY_REKEY_BACKUP_RETAINED: " + backup));
            throw failure;
        }
    }

    private static ObjectNode readEmptyIndex(Path path, RecoveryLeaseRoots roots, byte[] anchor) throws IOException {
        var index = (ObjectNode) RecoveryControlHistory.read(path);
        fields(index, "formatVersion installationId entries indexTag"); version(index);
        require(text(index, "installationId").equals(roots.installation()), "RECOVERY_REKEY_IDENTITY");
        anchorTag(anchor, index, "indexTag");
        require(index.path("entries").isArray() && index.path("entries").isEmpty(), "RECOVERY_REKEY_ACTIVE_LEASE");
        try (var files = Files.list(path.getParent())) {
            for (Path file : files.toList())
                require(file.getFileName().toString().equals("index.json"), "RECOVERY_REKEY_ACTIVE_LEASE");
        }
        return index;
    }

    private static List<Path> processPaths(Path directory) throws IOException {
        var paths = new ArrayList<Path>();
        try (var files = Files.list(directory)) {
            for (Path path : files.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                require(path.getFileName().toString().matches("[0-9a-f-]{36}\\.json"), "RECOVERY_REKEY_PROCESS_LAYOUT");
                paths.add(path);
            }
        }
        return paths;
    }

    private static JsonNode rewriteProcess(Path path, RecoveryLeaseRoots roots, byte[] oldAnchor, byte[] newAnchor)
            throws IOException {
        var value = RecoveryControlHistory.read(path);
        fields(value, "formatVersion installationId processIdentity identitySha256 pid jvmVersion createdAt anchorTag");
        version(value); anchorTag(oldAnchor, value);
        require(text(value, "installationId").equals(roots.installation())
            && text(value, "identitySha256").equals(roots.identityHash()), "RECOVERY_REKEY_IDENTITY");
        require(!RecoveryLeaseProcess.alive(path, RecoveryWriterProtocol.fileIdentity(path), hash(value), roots),
            "RECOVERY_REKEY_ACTIVE_WRITER");
        var rewritten = (ObjectNode) value.deepCopy();
        rewritten.remove("anchorTag"); rewritten.put("anchorTag", hmac(newAnchor, rewritten));
        return rewritten;
    }

    private static JsonNode rewriteMarker(Path path, byte[] newAnchor) throws IOException {
        var marker = (ObjectNode) RecoveryControlHistory.read(path);
        fields(marker, "formatVersion installationId authorityRoot leaseRoot identitySha256 anchorId fenceFileIdentity coordinatorFileIdentity bindingSha256");
        version(marker); marker.put("anchorId", sha256(newAnchor)); marker.remove("bindingSha256");
        marker.put("bindingSha256", hash(marker)); return marker;
    }

    private static ObjectNode indexWithAnchor(ObjectNode original, byte[] anchor) {
        var index = original.deepCopy(); index.remove("indexTag"); index.put("indexTag", hmac(anchor, index)); return index;
    }

    private static void backup(List<Path> targets, Path root, Path backup) throws IOException {
        for (Path target : targets) {
            Path relative = root.relativize(target), copy = backup.resolve(relative);
            Files.createDirectories(copy.getParent()); Files.copy(target, copy, LinkOption.NOFOLLOW_LINKS);
        }
    }

    private static void replace(Path target, JsonNode value) throws IOException {
        Path pending = target.resolveSibling(target.getFileName() + ".rekey.pending");
        Files.writeString(pending, canonical(value), StandardOpenOption.CREATE_NEW);
        try { Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        finally { Files.deleteIfExists(pending); }
    }

    private static void restore(List<Path> targets, Path root, Path backup, Throwable failure) {
        for (Path target : targets) {
            try { Files.copy(backup.resolve(root.relativize(target)), target,
                StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS); }
            catch (IOException rollback) { failure.addSuppressed(rollback); }
        }
    }

}
