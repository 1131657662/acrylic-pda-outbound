package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Each owner is observed separately. A successful scan does NOT eliminate window W. */
final class RecoveryLeaseScan {
    private static final Pattern AUDIT_MARKER = Pattern.compile("([0-9a-f-]{36})[.]unknown[.]json([.]pending)?");
    private static final Pattern LEASE_RECORD = Pattern.compile("([0-9a-f-]{36})[.]json");
    private RecoveryLeaseScan() { }

    static void check(RecoveryLeaseRoots roots) throws IOException {
        RecoveryControlLock.requireOwner(roots.authorityRoot());
        var indexed = new HashSet<String>();
        try {
            var index = readIndex(roots);
            for (JsonNode entry : index.path("entries")) {
                String operation = entry.asText();
                require(operation.matches("[0-9a-f-]{36}"), "RECOVERY_LEASE_OUTSTANDING");
                require(indexed.add(operation), "RECOVERY_LEASE_OUTSTANDING");
                // Published but absent: the record was deleted after publication, and that evidence is
                // never repaired by retiring the index entry.
                require(Files.exists(roots.root().resolve("leases").resolve(operation + ".json"), LinkOption.NOFOLLOW_LINKS),
                    "RECOVERY_LEASE_OUTSTANDING");
            }
        } catch (IOException | RuntimeException error) {
            RecoveryFenceAudit.event("RECOVERY_LEASE_OUTSTANDING", "index|" + error.getMessage());
            throw new RecoveryControlException("RECOVERY_LEASE_OUTSTANDING", error);
        }
        var orphans = new ArrayList<Path>();
        try (var entries = Files.list(roots.root().resolve("leases"))) {
            for (Path path : entries.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                try {
                    if (auditMarkerBesideItsLease(path)) continue; // Not a blocking basis only while its lease survives.
                    if (path.getFileName().toString().equals("index.json")) continue;
                    var record = LEASE_RECORD.matcher(path.getFileName().toString());
                    // A record that the anchored index does not list was not published by this installation.
                    if (record.matches()) require(indexed.contains(record.group(1)), "RECOVERY_LEASE_OUTSTANDING");
                    checkRecord(path, roots);
                } catch (IOException | RuntimeException error) {
                    // Design §5.3/§5.1: a record whose owner is gone (unparseable, pending, missing or
                    // unlockable owner) is an orphan: it is retired here — deleting the lease record and
                    // its index entry, never a process record — and the caller still refuses. A failed
                    // retirement propagates and fences through the authority's guarded wrapper.
                    if (retirable(path, error)) {
                        orphans.add(path);
                        RecoveryFenceAudit.event("RECOVERY_LEASE_ORPHAN", path + "|" + error.getMessage());
                        continue;
                    }
                    RecoveryFenceAudit.event("RECOVERY_LEASE_OUTSTANDING", path + "|" + error.getMessage());
                    throw new RecoveryControlException("RECOVERY_LEASE_OUTSTANDING", error);
                }
            }
        }
        if (!orphans.isEmpty()) {
            retireOrphans(roots, orphans);
            throw new RecoveryControlException("RECOVERY_LEASE_OUTSTANDING",
                new IOException("retired " + orphans.size() + " orphan lease record(s)"));
        }
    }

    /** Only a lease record that exists and was judged an orphan may be retired. */
    private static boolean retirable(Path path, Throwable error) {
        var name = path.getFileName().toString();
        return !name.equals("index.json") && !AUDIT_MARKER.matcher(name).matches()
            && (LEASE_RECORD.matcher(name).matches() || name.endsWith(".json.pending"))
            && error instanceof RecoveryControlException orphan
            && "RECOVERY_LEASE_OUTSTANDING".equals(orphan.code());
    }

    /**
     * Retirement of orphan lease records: the record is removed and the anchored index is rewritten
     * without it, so the next admission sees a consistent tree. Process records are never touched —
     * a dead process's record stays as the evidence that it existed.
     */
    static void retireOrphans(RecoveryLeaseRoots roots, List<Path> orphans) throws IOException {
        RecoveryControlLock.requireOwner(roots.authorityRoot());
        var retired = new HashSet<String>();
        for (Path orphan : orphans) {
            String name = orphan.getFileName().toString();
            var operation = LEASE_RECORD.matcher(name);
            require(operation.matches() || name.endsWith(".json.pending"), "RECOVERY_LEASE_OUTSTANDING");
            String id = operation.matches() ? operation.group(1) : name.substring(0, name.length() - ".json.pending".length());
            require(id.matches("[0-9a-f-]{36}"), "RECOVERY_LEASE_OUTSTANDING");
            Files.deleteIfExists(orphan);
            retired.add(id);
        }
        var index = readIndex(roots);
        var kept = new ArrayList<JsonNode>();
        for (JsonNode entry : index.path("entries")) if (!retired.contains(entry.asText())) kept.add(entry);
        index.putArray("entries").addAll(kept);
        index.remove("indexTag");
        index.put("indexTag", hmac(roots.anchor(), index));
        Path pending = roots.root().resolve("leases").resolve("index.json.pending");
        Files.writeString(pending, canonical(index), StandardOpenOption.CREATE_NEW);
        Files.move(pending, pending.resolveSibling("index.json"), StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
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

    private static com.fasterxml.jackson.databind.node.ObjectNode readIndex(RecoveryLeaseRoots roots) throws IOException {
        var value = RecoveryControlHistory.read(roots.root().resolve("leases").resolve("index.json"));
        fields(value, "formatVersion installationId entries indexTag");
        version(value);
        require(text(value, "installationId").equals(roots.installation()), "RECOVERY_LEASE_OUTSTANDING");
        require(value.path("entries").isArray(), "RECOVERY_LEASE_OUTSTANDING");
        anchorTag(roots.anchor(), value, "indexTag");
        return (com.fasterxml.jackson.databind.node.ObjectNode) value;
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
        var index = readIndex(roots);
        ((ArrayNode) index.path("entries")).add(operation.toString());
        index.remove("indexTag");
        index.put("indexTag", hmac(roots.anchor(), index));
        Path pendingIndex = roots.root().resolve("leases").resolve("index.json.pending");
        Files.writeString(pendingIndex, canonical(index), StandardOpenOption.CREATE_NEW);
        Files.move(pendingIndex, pendingIndex.resolveSibling("index.json"), StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
        return operation;
    }
}
