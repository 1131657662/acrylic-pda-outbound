package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.*;
import java.util.*;

/** Synthetic pinned installation inputs; all Authority decisions use product disk/OS code. */
record FenceTestSupply(Path base, Path config, RecoveryLeaseRoots roots) {
    static FenceTestSupply create(Path parent) throws Exception {
        Path base = Files.createTempDirectory(parent, "fence-");
        Path authority = Files.createDirectory(base.resolve("authority")).toRealPath();
        Path business = Files.createDirectory(base.resolve("business")).toRealPath();
        Path leases = base.resolve("p5");
        Path closed = base.resolve("closed");
        UUID installation = UUID.randomUUID();
        var value = JsonNodeFactory.instance.objectNode().put("formatVersion", 2)
            .put("authorityKind", "INITIAL_NORMAL_INSTALL").put("installationId", installation.toString())
            .put("closedAuthority", closed.toString()).put("leaseRoot", leases.toString())
            .put("initialCommandSha256", "a".repeat(64));
        value.putArray("restoreRoots").add(business.toString());
        Files.writeString(authority.resolve("identity.json"), RecoveryControlFormat.canonical(value), StandardOpenOption.CREATE_NEW);
        Files.writeString(authority.resolve("coordinator.lock"), "0", StandardOpenOption.CREATE_NEW);
        var location = new RecoveryControlLocation(authority, installation, List.of(business));
        var binding = new RecoveryLeaseRoots.Binding(location, leases, closed, RecoveryControlFormat.hash(value));
        String markerHash = RecoveryLeaseRoots.provision(binding);
        var config = value.deepCopy().put("root", authority.toString()).put("identitySha256", binding.identitySha256())
            .put("markerSha256", markerHash);
        Path file = base.resolve("trusted-config.json");
        Files.writeString(file, RecoveryControlFormat.canonical(config), StandardOpenOption.CREATE_NEW);
        return new FenceTestSupply(base, file, new RecoveryLeaseRoots(binding, markerHash));
    }

    static FenceTestSupply read(Path config) throws Exception {
        var value = RecoveryControlFormat.readCanonical(Files.readAllBytes(config));
        var restored = new ArrayList<Path>(); value.path("restoreRoots").forEach(p -> restored.add(Path.of(p.asText())));
        var location = new RecoveryControlLocation(Path.of(value.path("root").asText()),
            UUID.fromString(value.path("installationId").asText()), restored);
        var binding = new RecoveryLeaseRoots.Binding(location, Path.of(value.path("leaseRoot").asText()),
            Path.of(value.path("closedAuthority").asText()), value.path("identitySha256").asText());
        return new FenceTestSupply(config.getParent(), config, new RecoveryLeaseRoots(binding, value.path("markerSha256").asText()));
    }

    RecoveryFenceAuthority authority() { return new RecoveryFenceAuthority(roots); }
}
