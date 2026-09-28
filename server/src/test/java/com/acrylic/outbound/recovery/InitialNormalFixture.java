package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

/** Synthetic original identities used only in tests; created once and then retained byte for byte. */
final class InitialNormalFixture {
    static final UUID INSTALLATION = UUID.fromString("721e3f13-2841-4b09-b56c-0ea1e2201111");
    final Path root;
    final Path restore;
    final Path trusted;
    final byte[] original;
    final InitialNormalStore store;

    InitialNormalFixture(Path base) throws Exception {
        root = Files.createDirectory(base.resolve("initial-authority"));
        restore = Files.createDirectory(base.resolve("restore"));
        Files.write(root.resolve("coordinator.lock"), new byte[]{'0'}, StandardOpenOption.CREATE_NEW);
        original = RecoveryControlJson.encode(request());
        trusted = Files.write(base.resolve("trusted-original.json"), original, StandardOpenOption.CREATE_NEW);
        store = new InitialNormalStore(new RecoveryControlLocation(root, INSTALLATION, List.of(restore)), trusted);
    }

    static ObjectNode request() {
        return JsonNodeFactory.instance.objectNode().put("formatVersion", 1).put("kind", "INITIAL_NORMAL")
            .put("installationId", INSTALLATION.toString()).put("epochId", "a67cad57-afbc-45dd-9e12-a85fe0201111")
            .put("numericEpoch", "1").put("mysqlServerUuid", "b18ee383-a479-4020-a7b1-0d5045801111")
            .put("databaseSchema", "acrylic_initial_test").put("commandId", "074c2ca1-7e03-476b-826b-650b0e201111")
            .put("sourceEvidenceSha256", "a".repeat(64)).put("operatorId", "test-original-operator")
            .put("requestedAt", "2026-09-28T00:00:00Z");
    }

    void complete() {
        store.locked(() -> { store.completeLocked(InitialNormalEvidence.parse(original).sha256()); return null; });
    }
}
