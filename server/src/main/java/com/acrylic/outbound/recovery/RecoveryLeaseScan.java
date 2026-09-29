package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Each owner is observed separately. A successful scan does NOT eliminate window W. */
final class RecoveryLeaseScan {
    private static final Pattern AUDIT_MARKER = Pattern.compile("([0-9a-f-]{36})[.]unknown[.]json([.]pending)?");
    private RecoveryLeaseScan() { }

    static void check(RecoveryLeaseRoots roots) throws IOException {
        RecoveryControlLock.requireOwner(roots.authorityRoot());
        try (var entries = Files.list(roots.root().resolve("leases"))) {
            for (Path path : entries.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                try {
                    if (auditMarkerBesideItsLease(path)) continue; // Not a blocking basis only while its lease survives.
                    checkRecord(path, roots);
                } catch (IOException | RuntimeException error) {
                    RecoveryFenceAudit.event("RECOVERY_LEASE_OUTSTANDING", path + "|" + error.getMessage());
                    throw new RecoveryControlException("RECOVERY_LEASE_OUTSTANDING", error);
                }
            }
        }
    }

    /**
     * A lease renamed (or reshaped) into the audit file name must never be dismissed by its name alone:
     * the marker is ignorable only when the lease it annotates is still present and the content is
     * the audit shape written on an UNKNOWN outcome, not a lease record.
     */
    private static boolean auditMarkerBesideItsLease(Path path) throws IOException {
        var match = AUDIT_MARKER.matcher(path.getFileName().toString());
        if (!match.matches()) return false;
        Path lease = path.resolveSibling(match.group(1) + ".json");
        if (!Files.exists(lease, LinkOption.NOFOLLOW_LINKS)) return false;
        var value = RecoveryControlHistory.read(path);
        return value.has("operationId") && value.has("event") && value.has("at")
            && !value.has("processFileIdentity") && !value.has("processSha256");
    }

    private static void checkRecord(Path path, RecoveryLeaseRoots roots) throws IOException {
        require(!path.getFileName().toString().endsWith(".pending"), "RECOVERY_LEASE_OUTSTANDING");
        var value = RecoveryControlHistory.read(path);
        fields(value, "formatVersion installationId operationId processIdentity processFileIdentity processSha256 createdAt anchorTag");
        version(value); instant(value, "createdAt");
        // An owner reference that was rewritten (forged or re-pointed) cannot carry a valid anchor tag.
        anchorTag(roots.anchor(), value);
        require(text(value, "installationId").equals(roots.installation())
            && path.getFileName().toString().equals(uuid(value, "operationId") + ".json"), "RECOVERY_LEASE_OUTSTANDING");
        Path owner = roots.root().resolve("processes").resolve(uuid(value, "processIdentity") + ".json");
        boolean alive = RecoveryLeaseProcess.alive(owner, text(value, "processFileIdentity"), text(value, "processSha256"), roots);
        require(alive, "RECOVERY_LEASE_OUTSTANDING");
    }

    static UUID publish(RecoveryLeaseRoots roots, RecoveryLeaseProcess process) throws IOException {
        RecoveryControlLock.requireOwner(roots.authorityRoot());
        UUID operation = UUID.randomUUID();
        var value = JsonNodeFactory.instance.objectNode().put("formatVersion", 1)
            .put("installationId", roots.installation()).put("operationId", operation.toString())
            .put("processIdentity", process.id.toString()).put("processFileIdentity", process.nativeIdentity)
            .put("processSha256", hash(process.record)).put("createdAt", RecoveryLeaseProcess.now());
        value.put("anchorTag", hmac(roots.anchor(), value));
        Path pending = roots.root().resolve("leases").resolve(operation + ".json.pending");
        Files.writeString(pending, canonical(value), StandardOpenOption.CREATE_NEW);
        Files.move(pending, pending.resolveSibling(operation + ".json"), StandardCopyOption.ATOMIC_MOVE);
        return operation;
    }
}
