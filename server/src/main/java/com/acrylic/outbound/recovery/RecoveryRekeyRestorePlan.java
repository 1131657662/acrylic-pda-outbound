package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** All validation is read-only; no unrecognized or later process may be overwritten. */
record RecoveryRekeyRestorePlan(RecoveryRekeyRestorePlan.Input input, RecoveryLeaseRoots roots,
                               List<RecoveryRekeyRestorePlan.Entry> entries) {
    record Input(RecoveryRekeyRequest request, Path backup, String inventoryHash) { }
    record Entry(Path path, JsonNode original, JsonNode rotated) { }

    static RecoveryRekeyRestorePlan load(Input input, RecoveryRekeySecrets secrets) throws IOException {
        var request = input.request();
        var roots = new RecoveryLeaseRoots(request.binding(), request.pins().markerSha256(), secrets.oldAnchor);
        roots.identity();
        Path backup = RecoveryControlPaths.checked(input.backup());
        require(backup.equals(input.backup()) && backup.getParent().equals(roots.root().getParent())
            && backup.getFileName().toString().startsWith(".rekey-backup-"), "RECOVERY_RESTORE_BACKUP_PATH");
        var targets = RecoveryRekeyInventory.verified(new RecoveryRekeyInventory.Input(roots, secrets.oldAnchor, secrets.newAnchor),
            backup, input.inventoryHash());
        var entries = new ArrayList<Entry>();
        for (Path path : targets) {
            JsonNode original = RecoveryControlHistory.read(backup.resolve(roots.root().relativize(path)));
            entries.add(new Entry(path, original, rotated(original, path, secrets.newAnchor)));
        }
        var plan = new RecoveryRekeyRestorePlan(input, roots, List.copyOf(entries));
        plan.identity(); plan.layout(); plan.validateOriginals(); return plan;
    }

    private static JsonNode rotated(JsonNode original, Path path, byte[] next) {
        var value = (ObjectNode) original.deepCopy(); String name = path.getFileName().toString();
        String field = name.equals("lease-root.json") ? "bindingSha256" : (name.equals("index.json") ? "indexTag" : "anchorTag");
        value.remove(field);
        if (field.equals("bindingSha256")) { value.put("anchorId", sha256(next)); value.put(field, hash(value)); }
        else value.put(field, hmac(next, value));
        return value;
    }

    JsonNode marker() { return entries.getLast().original(); }

    private void identity() throws IOException {
        var marker = marker();
        fields(marker, "formatVersion installationId authorityRoot leaseRoot identitySha256 anchorId fenceFileIdentity coordinatorFileIdentity bindingSha256");
        version(marker);
        var body = (ObjectNode) marker.deepCopy(); body.remove("bindingSha256");
        require(hash(marker).equals(input.request().pins().markerSha256()) && hash(body).equals(text(marker, "bindingSha256"))
            && text(marker, "installationId").equals(roots.installation())
            && text(marker, "authorityRoot").equals(roots.authorityRoot().toString())
            && text(marker, "leaseRoot").equals(roots.root().toString())
            && text(marker, "identitySha256").equals(roots.identityHash())
            && text(marker, "anchorId").equals(input.request().pins().oldAnchorId()), "RECOVERY_RESTORE_ORIGINAL_MARKER");
        RecoveryLeaseRoots.checkFile(roots.authorityRoot().resolve("coordinator.lock"), text(marker, "coordinatorFileIdentity"), "COORDINATOR");
        RecoveryLeaseRoots.checkFile(roots.root().resolve("fenced.lock"), text(marker, "fenceFileIdentity"), "FENCE");
        require(Arrays.equals(Files.readAllBytes(roots.root().resolve("fenced.lock")), new byte[]{48}), "RECOVERY_RESTORE_UNKNOWN_FENCE");
        require(hash(RecoveryControlHistory.read(roots.authorityRoot().resolve("identity.json"))).equals(roots.identityHash()),
            "RECOVERY_RESTORE_IDENTITY");
    }

    private void validateOriginals() throws IOException {
        var index = entries.get(entries.size() - 2).original();
        fields(index, "formatVersion installationId entries indexTag"); version(index); anchorTag(roots.anchor(), index, "indexTag");
        require(text(index, "installationId").equals(roots.installation()) && index.path("entries").isArray()
            && index.path("entries").isEmpty(), "RECOVERY_RESTORE_ACTIVE_LEASE");
        for (Entry entry : entries) if (entry.path().getParent().getFileName().toString().equals("processes"))
            RecoveryLeaseProcess.verifyRetained(input.backup().resolve(roots.root().relativize(entry.path())), roots);
    }

    private void layout() throws IOException {
        var expected = new HashSet<Path>(); entries.forEach(e -> expected.add(e.path())); expected.add(roots.root().resolve("fenced.lock"));
        try (var paths = Files.walk(RecoveryControlPaths.checked(roots.root()))) {
            for (Path path : paths.toList()) {
                RecoveryControlPaths.checked(path);
                if (Files.isDirectory(path)) require(Set.of(roots.root(), roots.root().resolve("processes"), roots.root().resolve("leases"))
                    .contains(path), "RECOVERY_RESTORE_UNKNOWN_LAYOUT");
                else require(expected.remove(path), "RECOVERY_RESTORE_UNKNOWN_LAYOUT");
            }
        }
        require(expected.isEmpty(), "RECOVERY_RESTORE_MISSING_RECORD");
    }

    boolean checkCurrent(byte[] newAnchor) throws IOException {
        boolean allOld = true;
        for (Entry entry : entries) {
            var value = RecoveryControlHistory.read(entry.path()); boolean old = value.equals(entry.original());
            require(old || value.equals(entry.rotated()), "RECOVERY_RESTORE_UNKNOWN_CONTENT"); allOld &= old;
            if (entry.path().getParent().getFileName().toString().equals("processes")) {
                var signedRoots = old ? roots : new RecoveryLeaseRoots(input.request().binding(), "", newAnchor);
                require(!RecoveryLeaseProcess.alive(entry.path(), RecoveryWriterProtocol.fileIdentity(entry.path()), hash(value), signedRoots),
                    "RECOVERY_RESTORE_ACTIVE_WRITER");
            }
        }
        return allOld;
    }
}
