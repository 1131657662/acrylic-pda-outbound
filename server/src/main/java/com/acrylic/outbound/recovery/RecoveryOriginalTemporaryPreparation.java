package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit independent custody pin; reads original DB preparation and ACK bytes without a DB connection or private key. */
public final class RecoveryOriginalTemporaryPreparation {
    private final RecoveryControlLocation configured;
    private final Path checkpoint;
    private final RecoverySourceTemporaryActivation activation;

    public RecoveryOriginalTemporaryPreparation(RecoveryControlLocation configured, Path checkpoint, RecoveryReceiptKeys keys) {
        this.configured = Objects.requireNonNull(configured); this.checkpoint = Objects.requireNonNull(checkpoint);
        activation = new RecoverySourceTemporaryActivation(Objects.requireNonNull(keys));
    }

    public Proof read(Request request, RecoveryPinnedSource source) {
        require(request != null && source != null && source.identity().sourceKind().equals("TEMP"), "RECOVERY_TEMP_ORIGINAL_REQUEST");
        require(request.independentSha256() != null && request.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(request.independentSha256()), "RECOVERY_TEMP_ORIGINAL_PIN");
        try {
            var location = configured.checked(); var reader = new RecoveryControlReader(location); var before = reader.read(checkpoint);
            require(source.control().equals(before) && Set.of("RECOVERING", "VERIFYING", "RELEASE_READY").contains(before.phase()),
                "RECOVERY_TEMP_ORIGINAL_CONTROL");
            Path root = outside(request.materialRoot(), location); Path file = root.resolve(request.independentSha256() + ".json");
            byte[] bytes = readFile(file, RecoveryControlJson.MAX_BYTES);
            require(sha256(bytes).equals(request.independentSha256()), "RECOVERY_TEMP_ORIGINAL_PIN_MISMATCH");
            JsonNode value = readCanonical(bytes); verify(value, request, source);
            Path origin = root.resolve(text(value, "originSha256") + ".bin"); byte[] original = readFile(origin, RecoverySourceManifest.MAX_OBJECT_BYTES);
            require(sha256(original).equals(text(value, "originSha256")) && Arrays.equals(original, source.original("origin")),
                "RECOVERY_TEMP_ORIGINAL_ORIGIN");
            var proof = activation.verify(source);
            require(before.equals(reader.read(checkpoint)) && Arrays.equals(bytes, readFile(file, RecoveryControlJson.MAX_BYTES))
                && Arrays.equals(original, readFile(origin, RecoverySourceManifest.MAX_OBJECT_BYTES)), "RECOVERY_TEMP_ORIGINAL_CHANGED");
            return new Proof(proof, request.independentSha256(), instant(value, "observedAt"));
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (java.io.IOException unavailable) { throw new RecoveryControlException("RECOVERY_TEMP_ORIGINAL_UNAVAILABLE", unavailable); }
        catch (RuntimeException malformed) { throw new RecoveryControlException("RECOVERY_TEMP_ORIGINAL_INVALID", malformed); }
    }

    private void verify(JsonNode value, Request request, RecoveryPinnedSource source) {
        fields(value, "formatVersion observationKind mysqlServerUuid databaseSchema sourceRecoveryEpoch observedAt source originSha256"); version(value);
        var database = request.database(); hashField(value, "originSha256");
        require("ORIGINAL_TEMP_PREPARATION".equals(text(value, "observationKind")), "RECOVERY_TEMP_ORIGINAL_KIND");
        require(database.mysqlServerUuid().equals(uuid(value, "mysqlServerUuid")) && database.schema().equals(text(value, "databaseSchema"))
            && database.sourceRecoveryEpoch().equals(text(value, "sourceRecoveryEpoch"))
            && database.sourceRecoveryEpoch().equals(source.identity().sourceRecoveryEpoch()), "RECOVERY_TEMP_ORIGINAL_DATABASE");
        require(!instant(value, "observedAt").isAfter(request.notAfter()), "RECOVERY_TEMP_ORIGINAL_TIME");
        require(source.identity().equals(RecoverySourceIdentity.read(value.path("source"))), "RECOVERY_TEMP_ORIGINAL_SOURCE");
    }

    private Path outside(Path materials, RecoveryControlLocation location) throws java.io.IOException {
        Path root = RecoveryControlPaths.checked(materials);
        require(!RecoveryControlPaths.overlap(root, location.root()), "RECOVERY_SOURCE_INSIDE_AUTHORITY");
        for (Path restored : location.restoreRoots())
            require(!RecoveryControlPaths.overlap(root, restored), "RECOVERY_SOURCE_INSIDE_RESTORE_SET");
        require(!RecoveryControlPaths.overlap(root, location.checkpoint(checkpoint)), "RECOVERY_SOURCE_INSIDE_CHECKPOINT"); return root;
    }

    private byte[] readFile(Path path, long maximum) throws java.io.IOException {
        try (var input = Files.newInputStream(RecoveryControlPaths.file(path), LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(maximum + 1));
            require(bytes.length > 0 && bytes.length <= maximum, "RECOVERY_TEMP_ORIGINAL_SIZE"); return bytes;
        }
    }

    public record Request(Path materialRoot, String independentSha256, RecoveryDeviceRegistrationCapture.Database database, Instant notAfter) {
        public Request { require(materialRoot != null && database != null && notAfter != null, "RECOVERY_TEMP_ORIGINAL_REQUEST"); }
    }
    public record Proof(RecoverySourceTemporaryActivation.Proof activation, String originalSha256, Instant observedAt) {
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
