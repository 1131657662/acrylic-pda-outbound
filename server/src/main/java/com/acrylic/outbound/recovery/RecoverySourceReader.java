package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit offline read seam. No Spring bean, HTTP endpoint, repair, cached trust or automatic pin. */
public final class RecoverySourceReader {
    private final RecoveryControlLocation configured;
    private final Path checkpoint;

    public RecoverySourceReader(RecoveryControlLocation configured, Path checkpoint) {
        this.configured = Objects.requireNonNull(configured); this.checkpoint = Objects.requireNonNull(checkpoint);
    }

    public RecoveryPinnedSource read(Path materialRoot, String independentManifestSha256, RecoverySourceIdentity expected) {
        require(expected != null, "RECOVERY_SOURCE_IDENTITY");
        require(independentManifestSha256 != null && independentManifestSha256.matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(independentManifestSha256), "RECOVERY_SOURCE_PIN_REQUIRED");
        try { return checked(materialRoot, independentManifestSha256, expected); }
        catch (IOException unavailable) { throw new RecoveryControlException("RECOVERY_SOURCE_UNAVAILABLE", unavailable); }
    }

    private RecoveryPinnedSource checked(Path materialRoot, String pin, RecoverySourceIdentity expected) throws IOException {
        var location = configured.checked();
        Path root = outside(location, materialRoot);
        var reader = new RecoveryControlReader(location);
        var before = reader.read(checkpoint);
        require(Set.of("RECOVERING", "VERIFYING", "RELEASE_READY").contains(before.phase()), "RECOVERY_SOURCE_PHASE");
        byte[] bytes = readFile(root.resolve(pin + ".json"), RecoveryControlJson.MAX_BYTES);
        require(pin.equals(sha256(bytes)), "RECOVERY_SOURCE_PIN_MISMATCH");
        var manifest = readCanonical(bytes);
        RecoverySourceManifest.requireBinding(manifest, before, expected);
        var originals = new HashMap<String, byte[]>();
        for (String kind : RecoverySourceManifest.OBJECTS) {
            var descriptor = manifest.path("objects").path(kind);
            String hash = text(descriptor, "sha256");
            byte[] original = readFile(root.resolve(hash + ".bin"), RecoverySourceManifest.MAX_OBJECT_BYTES);
            require(original.length == revision(descriptor, "byteCount") && hash.equals(sha256(original)),
                "RECOVERY_SOURCE_OBJECT_MISMATCH");
            originals.put(kind, original);
        }
        require(before.equals(reader.read(checkpoint)), "RECOVERY_SOURCE_CONTROL_CHANGED");
        return new RecoveryPinnedSource(expected, before, pin, originals);
    }

    private Path outside(RecoveryControlLocation location, Path materialRoot) throws IOException {
        Path root = RecoveryControlPaths.checked(materialRoot);
        require(!RecoveryControlPaths.overlap(root, location.root()), "RECOVERY_SOURCE_INSIDE_AUTHORITY");
        for (Path restored : location.restoreRoots())
            require(!RecoveryControlPaths.overlap(root, restored), "RECOVERY_SOURCE_INSIDE_RESTORE_SET");
        require(!RecoveryControlPaths.overlap(root, location.checkpoint(checkpoint)), "RECOVERY_SOURCE_INSIDE_CHECKPOINT");
        return root;
    }

    private byte[] readFile(Path path, long maximum) throws IOException {
        Path file = RecoveryControlPaths.file(path);
        try (var input = java.nio.file.Files.newInputStream(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(maximum + 1));
            require(bytes.length > 0 && bytes.length <= maximum, "RECOVERY_SOURCE_OBJECT_SIZE");
            return bytes;
        }
    }
}
