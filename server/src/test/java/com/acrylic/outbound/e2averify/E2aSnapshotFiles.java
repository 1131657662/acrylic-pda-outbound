package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/** Captures immutable storage bytes and rejects links/path escape. */
public final class E2aSnapshotFiles {
    private E2aSnapshotFiles() { }

    public static ObjectNode capture(Path root, Path workspace) throws Exception {
        Path base = root.toAbsolutePath().normalize();
        Path work = workspace.toAbsolutePath().normalize();
        if (!base.startsWith(work) || Files.isSymbolicLink(base)) throw new IllegalArgumentException("Storage escapes workspace");
        Path realBase = base.toRealPath();
        Path realWork = work.toRealPath();
        if (!realBase.startsWith(realWork)) throw new IllegalArgumentException("Storage resolves outside workspace");
        var result = JsonNodeFactory.instance.objectNode();
        if (!Files.exists(base)) return result;
        try (var paths = Files.walk(base)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                if (Files.isSymbolicLink(path)) throw new IllegalStateException("Storage link");
                Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(base)) throw new IllegalArgumentException("Storage path escape");
                Path realPath = normalized.toRealPath();
                if (!realPath.startsWith(realBase)) throw new IllegalArgumentException("Storage link escapes root");
                byte[] bytes = Files.readAllBytes(normalized);
                var entry = result.putObject(base.relativize(normalized).toString().replace('\\', '/'));
                entry.put("bytes", bytes.length);
                entry.put("sha256", hex(bytes));
            }
        }
        return result;
    }

    private static String hex(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
