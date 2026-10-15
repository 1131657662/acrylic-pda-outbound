package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Authenticates the complete pre-replacement backup set, never an authorization to restart. */
final class RecoveryRekeyInventory {
    static final String FILE = "rekey-inventory.json";
    static final int MAX_RECORDS = 9998;
    private RecoveryRekeyInventory() { }

    record Input(RecoveryLeaseRoots roots, byte[] oldAnchor, byte[] newAnchor) { }

    static List<Path> verified(Input input, Path backup, String pinnedHash) throws IOException {
        var value = RecoveryControlHistory.read(backup.resolve(FILE));
        fields(value, "formatVersion operation installationId authorityRoot leaseRoot identitySha256 oldAnchorId newAnchorId markerSha256 recordCount processRecords recordsSha256 totalBytes inventoryTag");
        version(value); anchorTag(input.oldAnchor(), value, "inventoryTag");
        var roots = input.roots();
        require(hash(value).equals(pinnedHash) && text(value, "operation").equals("REKEY_P5_INVENTORY")
            && text(value, "installationId").equals(roots.installation())
            && text(value, "authorityRoot").equals(roots.authorityRoot().toString())
            && text(value, "leaseRoot").equals(roots.root().toString())
            && text(value, "identitySha256").equals(roots.identityHash())
            && text(value, "oldAnchorId").equals(sha256(input.oldAnchor()))
            && text(value, "newAnchorId").equals(sha256(input.newAnchor()))
            && text(value, "markerSha256").equals(hash(RecoveryControlHistory.read(backup.resolve("lease-root.json")))),
            "RECOVERY_RESTORE_INVENTORY_BINDING");
        var targets = targets(roots.root(), backup); var summary = summarize(roots.root(), backup, targets);
        require(value.path("recordCount").isInt() && value.path("recordCount").asInt() == targets.size()
            && value.path("processRecords").isInt() && value.path("processRecords").asInt() == targets.size() - 2
            && value.path("totalBytes").isIntegralNumber() && value.path("totalBytes").asLong() == summary.bytes()
            && text(value, "recordsSha256").equals(summary.hash()), "RECOVERY_RESTORE_INVENTORY_SET");
        return targets;
    }

    private static List<Path> targets(Path root, Path backup) throws IOException {
        try (var paths = Files.list(backup)) {
            for (Path p : paths.toList()) require(Set.of("processes", "leases", "lease-root.json", FILE)
                .contains(p.getFileName().toString()), "RECOVERY_RESTORE_BACKUP_LAYOUT");
        }
        var result = new ArrayList<Path>();
        Path processes = RecoveryControlPaths.checked(backup.resolve("processes"));
        if (Files.exists(processes)) try (var files = Files.list(processes)) {
            for (Path p : files.sorted().toList()) {
                require(p.getFileName().toString().matches("[0-9a-f-]{36}\\.json") && result.size() < MAX_RECORDS - 2,
                    "RECOVERY_RESTORE_BACKUP_LAYOUT");
                result.add(root.resolve("processes").resolve(p.getFileName()));
            }
        }
        Path leases = RecoveryControlPaths.checked(backup.resolve("leases"));
        try (var files = Files.list(leases)) {
            require(files.map(p -> p.getFileName().toString()).toList().equals(List.of("index.json")),
                "RECOVERY_RESTORE_BACKUP_LAYOUT");
        }
        result.add(root.resolve("leases/index.json")); result.add(root.resolve("lease-root.json"));
        return result;
    }

    static String seal(Input input, Path backup, List<Path> targets) throws IOException {
        var roots = input.roots();
        RecoveryControlLock.requireOwner(roots.authorityRoot());
        require(targets.size() >= 2 && targets.size() <= MAX_RECORDS, "RECOVERY_REKEY_INVENTORY_LIMIT");
        var manifest = JsonNodeFactory.instance.objectNode().put("formatVersion", 1)
            .put("operation", "REKEY_P5_INVENTORY").put("installationId", roots.installation())
            .put("authorityRoot", roots.authorityRoot().toString()).put("leaseRoot", roots.root().toString())
            .put("identitySha256", roots.identityHash()).put("oldAnchorId", sha256(input.oldAnchor()))
            .put("newAnchorId", sha256(input.newAnchor()))
            .put("markerSha256", hash(RecoveryControlHistory.read(backup.resolve("lease-root.json"))))
            .put("recordCount", targets.size()).put("processRecords", targets.size() - 2);
        var summary = summarize(roots.root(), backup, targets);
        manifest.put("recordsSha256", summary.hash()).put("totalBytes", summary.bytes());
        manifest.put("inventoryTag", hmac(input.oldAnchor(), manifest));
        Files.writeString(backup.resolve(FILE), canonical(manifest), StandardOpenOption.CREATE_NEW);
        return hash(manifest);
    }

    private record Summary(String hash, long bytes) { }

    private static Summary summarize(Path root, Path backup, List<Path> targets) throws IOException {
        final MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        var names = targets.stream().map(root::relativize)
            .map(p -> p.toString().replace('\\', '/')).sorted().toList();
        require(new HashSet<>(names).size() == names.size(), "RECOVERY_REKEY_INVENTORY_DUPLICATE");
        long total = 0;
        for (String name : names) {
            Path path = RecoveryControlPaths.file(backup.resolve(name));
            byte[] bytes;
            try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                bytes = stream.readNBytes(RecoveryControlJson.MAX_BYTES + 1);
            }
            readCanonical(bytes); total += bytes.length;
            var entry = JsonNodeFactory.instance.objectNode().put("bytes", bytes.length)
                .put("path", name).put("sha256", sha256(bytes));
            digest.update(RecoveryControlJson.encode(entry));
        }
        return new Summary(HexFormat.of().formatHex(digest.digest()), total);
    }
}
