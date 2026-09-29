package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.file.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit offline provision and pinned P3/P5 identity. Startup never repairs a missing file. */
public final class RecoveryLeaseRoots {
    public record Binding(RecoveryControlLocation location, Path leaseRoot, Path closedAuthority,
                          String identitySha256) { }
    private final Binding binding;
    private final String markerSha256;
    private final byte[] anchor;

    public RecoveryLeaseRoots(Binding binding, String markerSha256, byte[] anchor) {
        this.binding = binding;
        this.markerSha256 = markerSha256;
        this.anchor = anchor.clone();
    }
    Path root() { return binding.leaseRoot(); }
    Path authorityRoot() { return binding.location().root(); }
    String installation() { return binding.location().installation().toString(); }
    String identityHash() { return binding.identitySha256(); }
    /** Explicitly supplied secret; never persisted inside the writable P5/P3 trees. */
    byte[] anchor() { return anchor.clone(); }

    /** Only an explicit supplier may call this, on a previously absent P5 directory. */
    public static String provision(Binding binding, byte[] anchor) throws IOException {
        var roots = new RecoveryLeaseRoots(binding, "", anchor);
        roots.identity();
        Files.createDirectory(roots.root());
        Files.createDirectory(roots.root().resolve("processes"));
        Files.createDirectory(roots.root().resolve("leases"));
        Files.write(roots.root().resolve("fenced.lock"), new byte[]{48}, StandardOpenOption.CREATE_NEW);
        var marker = JsonNodeFactory.instance.objectNode().put("formatVersion", 1)
            .put("installationId", roots.installation()).put("authorityRoot", roots.authorityRoot().toString())
            .put("leaseRoot", roots.root().toString()).put("identitySha256", roots.identityHash())
            .put("anchorId", sha256(anchor))
            .put("fenceFileIdentity", RecoveryWriterProtocol.fileIdentity(roots.root().resolve("fenced.lock")))
            .put("coordinatorFileIdentity", RecoveryWriterProtocol.fileIdentity(roots.authorityRoot().resolve("coordinator.lock")));
        marker.put("bindingSha256", hash(marker));
        Files.writeString(roots.root().resolve("lease-root.json"), canonical(marker), StandardOpenOption.CREATE_NEW);
        return hash(marker);
    }

    JsonNode verify() throws IOException {
        identity();
        var marker = RecoveryControlHistory.read(root().resolve("lease-root.json"));
        fields(marker, "formatVersion installationId authorityRoot leaseRoot identitySha256 anchorId fenceFileIdentity coordinatorFileIdentity bindingSha256");
        version(marker);
        require(hash(marker).equals(markerSha256), "RECOVERY_LEASE_ROOT_UNBOUND");
        var body = marker.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)body).remove("bindingSha256");
        require(hash(body).equals(text(marker, "bindingSha256")), "RECOVERY_LEASE_ROOT_UNBOUND");
        require(installation().equals(text(marker, "installationId"))
            && authorityRoot().toString().equals(text(marker, "authorityRoot"))
            && root().toString().equals(text(marker, "leaseRoot"))
            && identityHash().equals(text(marker, "identitySha256")), "RECOVERY_LEASE_ROOT_UNBOUND");
        require(sha256(anchor).equals(text(marker, "anchorId")), "CONTROL_ANCHOR_MISMATCH");
        checkFile(root().resolve("fenced.lock"), text(marker, "fenceFileIdentity"), "FENCE");
        checkFile(authorityRoot().resolve("coordinator.lock"), text(marker, "coordinatorFileIdentity"), "COORDINATOR");
        for (String name : new String[]{"processes", "leases"})
            require(Files.isDirectory(RecoveryControlPaths.checked(root().resolve(name))), "RECOVERY_LEASE_ROOT_UNBOUND");
        return marker;
    }

    private void identity() throws IOException {
        var location = binding.location().checked();
        require(location.root().equals(authorityRoot()), "RECOVERY_LEASE_ROOT_UNBOUND");
        Path lease = RecoveryControlPaths.checked(root()), closed = RecoveryControlPaths.checked(binding.closedAuthority());
        require(lease.equals(root()) && closed.equals(binding.closedAuthority())
            && !RecoveryControlPaths.overlap(lease, authorityRoot())
            && !RecoveryControlPaths.overlap(lease, closed), "RECOVERY_LEASE_ROOT_UNBOUND");
        for (Path restored : location.restoreRoots())
            require(!RecoveryControlPaths.overlap(lease, restored), "RECOVERY_LEASE_ROOT_UNBOUND");
        var value = RecoveryControlHistory.read(authorityRoot().resolve("identity.json"));
        fields(value, "formatVersion authorityKind installationId restoreRoots closedAuthority leaseRoot initialCommandSha256");
        require(value.path("formatVersion").isInt() && value.path("formatVersion").asInt() == 2, "RECOVERY_INITIAL_AUTHORITY_REQUIRED");
        require(text(value, "authorityKind").equals("INITIAL_NORMAL_INSTALL"), "CONTROL_AUTHORITY_KIND_FORBIDDEN");
        require(hash(value).equals(identityHash()), "CONTROL_AUTHORITY_IDENTITY_CHANGED");
        require(uuid(value, "installationId").equals(location.installation()), "CONTROL_INSTALLATION_MISMATCH");
        require(text(value, "leaseRoot").equals(lease.toString())
            && text(value, "closedAuthority").equals(closed.toString()), "RECOVERY_LEASE_ROOT_UNBOUND");
        var restore = value.path("restoreRoots");
        require(restore.isArray() && restore.size() == location.restoreRoots().size(), "CONTROL_RESTORE_SET_MISMATCH");
        for (int i = 0; i < restore.size(); i++)
            require(restore.get(i).asText().equals(location.restoreRoots().get(i).toString()), "CONTROL_RESTORE_SET_MISMATCH");
        hashField(value, "initialCommandSha256");
    }

    static void checkFile(Path path, String identity, String role) throws IOException {
        RecoveryFenceAudit.require(Files.exists(path, LinkOption.NOFOLLOW_LINKS), "CONTROL_" + role + "_LOCK_MISSING", path);
        RecoveryControlPaths.file(path);
        RecoveryFenceAudit.require(RecoveryWriterProtocol.fileIdentity(path).equals(identity),
            "CONTROL_" + role + "_LOCK_REPLACED", path);
        RecoveryFenceAudit.require(Files.size(path) == 1, "CONTROL_" + role + "_LOCK_INVALID", path);
    }
}
