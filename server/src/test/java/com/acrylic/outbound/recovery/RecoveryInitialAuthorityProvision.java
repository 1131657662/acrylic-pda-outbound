package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * The explicit supplier of the v2 initial-authority shape and the P5 tree (design rows 6–8).
 *
 * <p>It is deliberately offline and test/ops scoped: the anchor secret is supplied out of band and
 * only its identity ends up inside the writable tree, while custody and rotation of that secret stay
 * undecided (user ruling 2026-09-29). Promotion rewrites {@code identity.json} in place from the
 * legacy v1 shape to the explicit {@code INITIAL_NORMAL_INSTALL} shape; nothing else in the control
 * authority changes, so the bootstrap hash and the closed chain stay valid.
 */
public final class RecoveryInitialAuthorityProvision {
    private RecoveryInitialAuthorityProvision() { }

    /** A provisioned installation: the v2 identity hash and the {@code lease-root.json} digest. */
    public record Provisioned(String identitySha256, String markerSha256, byte[] anchor) { }

    /**
     * Promotes a v1 authority to the v2 initial-normal shape and provisions the P5 tree beside it.
     * The authority must not carry a kind yet: an authority that already declares one is never
     * silently reshaped.
     */
    public static Provisioned provision(Path authorityRoot, Path leaseRoot, Path closedAuthority) throws IOException {
        var identityPath = authorityRoot.resolve("identity.json");
        var identity = RecoveryControlHistory.read(identityPath);
        if (!identity.isObject() || identity.path("formatVersion").asInt() != 1) {
            throw new RecoveryControlException("RECOVERY_INITIAL_AUTHORITY_REQUIRED", null);
        }
        require(identity.path("authorityKind").isMissingNode(), "RECOVERY_INITIAL_AUTHORITY_REQUIRED");
        ObjectNode promoted = identity.deepCopy();
        promoted.put("formatVersion", 2)
            .put("authorityKind", RecoveryInitialAuthority.INITIAL_NORMAL_INSTALL)
            .put("closedAuthority", closedAuthority.toAbsolutePath().normalize().toString())
            .put("leaseRoot", leaseRoot.toAbsolutePath().normalize().toString());
        byte[] anchor = new byte[32];
        new SecureRandom().nextBytes(anchor);
        return promote(authorityRoot, promoted, leaseRoot, closedAuthority, anchor);
    }

    /** Deterministic variant: the caller supplies the anchor, as a restart of the same run would. */
    public static Provisioned provision(Path authorityRoot, Path leaseRoot, Path closedAuthority, byte[] anchor)
            throws IOException {
        var identity = RecoveryControlHistory.read(authorityRoot.resolve("identity.json"));
        if (!identity.isObject() || identity.path("formatVersion").asInt() != 1) {
            throw new RecoveryControlException("RECOVERY_INITIAL_AUTHORITY_REQUIRED", null);
        }
        require(identity.path("authorityKind").isMissingNode(), "RECOVERY_INITIAL_AUTHORITY_REQUIRED");
        ObjectNode promoted = identity.deepCopy();
        promoted.put("formatVersion", 2)
            .put("authorityKind", RecoveryInitialAuthority.INITIAL_NORMAL_INSTALL)
            .put("closedAuthority", closedAuthority.toAbsolutePath().normalize().toString())
            .put("leaseRoot", leaseRoot.toAbsolutePath().normalize().toString());
        return promote(authorityRoot, promoted, leaseRoot, closedAuthority, anchor.clone());
    }

    private static Provisioned promote(Path authorityRoot, ObjectNode promoted, Path leaseRoot, Path closedAuthority,
            byte[] anchor) throws IOException {
        require(!Files.exists(leaseRoot, LinkOption.NOFOLLOW_LINKS), "RECOVERY_LEASE_ROOT_UNBOUND");
        var location = location(authorityRoot, promoted);
        RecoveryControlFixture.write(authorityRoot.resolve("identity.json"), promoted);
        String identitySha256 = RecoveryControlFormat.hash(promoted);
        var binding = new RecoveryLeaseRoots.Binding(location, leaseRoot.toAbsolutePath().normalize(),
            closedAuthority.toAbsolutePath().normalize(), identitySha256);
        String markerSha256 = RecoveryLeaseRoots.provision(binding, anchor);
        return new Provisioned(identitySha256, markerSha256, anchor);
    }

    private static RecoveryControlLocation location(Path authorityRoot, ObjectNode identity) {
        var restored = new java.util.ArrayList<Path>();
        identity.path("restoreRoots").forEach(root -> restored.add(Path.of(root.asText())));
        return new RecoveryControlLocation(authorityRoot.toAbsolutePath().normalize(),
            UUID.fromString(identity.path("installationId").asText()), restored);
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new RecoveryControlException(code, null);
    }
}
