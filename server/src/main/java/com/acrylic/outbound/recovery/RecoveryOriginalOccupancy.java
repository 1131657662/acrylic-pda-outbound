package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Independently pinned original occupancy; offline use never queries a restored database or supplies new authority. */
public final class RecoveryOriginalOccupancy {
    private final RecoveryControlLocation configured;
    private final Path checkpoint;
    private final RecoverySourceOccupancy occupancy;

    public RecoveryOriginalOccupancy(RecoveryControlLocation configured, Path checkpoint, RecoveryReceiptKeys keys) {
        this.configured = Objects.requireNonNull(configured); this.checkpoint = Objects.requireNonNull(checkpoint);
        occupancy = new RecoverySourceOccupancy(Objects.requireNonNull(keys));
    }

    public Proof read(Request request, RecoveryPinnedSource source) {
        require(request != null && source != null, "RECOVERY_ORIGINAL_OCCUPANCY_REQUEST");
        require(request.independentSha256() != null && request.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(request.independentSha256()), "RECOVERY_ORIGINAL_OCCUPANCY_PIN");
        try {
            var location = configured.checked(); var reader = new RecoveryControlReader(location); var before = reader.read(checkpoint);
            require(source.control().equals(before) && Set.of("RECOVERING", "VERIFYING", "RELEASE_READY").contains(before.phase()),
                "RECOVERY_ORIGINAL_OCCUPANCY_CONTROL");
            Path root = outside(request.materialRoot(), location); Path file = root.resolve(request.independentSha256() + ".json");
            byte[] bytes = readFile(file); var value = readCanonical(bytes);
            require(sha256(bytes).equals(request.independentSha256()), "RECOVERY_ORIGINAL_OCCUPANCY_PIN_MISMATCH");
            verify(value, request, source);
            var proof = occupancy.verify(source, value.path("occupancy"));
            require(before.equals(reader.read(checkpoint)) && Arrays.equals(bytes, readFile(file)), "RECOVERY_ORIGINAL_OCCUPANCY_CHANGED");
            return new Proof(proof, request.independentSha256(), instant(value, "observedAt"));
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (java.io.IOException unavailable) { throw new RecoveryControlException("RECOVERY_ORIGINAL_OCCUPANCY_UNAVAILABLE", unavailable); }
        catch (RuntimeException malformed) { throw new RecoveryControlException("RECOVERY_ORIGINAL_OCCUPANCY_INVALID", malformed); }
    }

    private void verify(com.fasterxml.jackson.databind.JsonNode value, Request request, RecoveryPinnedSource source) {
        fields(value, "formatVersion observationKind mysqlServerUuid databaseSchema sourceRecoveryEpoch observedAt source payloadSha256 terminalReceipt occupancy");
        version(value); var database = request.database();
        require("ORIGINAL_SUBMISSION_OCCUPANCY".equals(text(value, "observationKind")), "RECOVERY_ORIGINAL_OCCUPANCY_KIND");
        require(database.mysqlServerUuid().equals(uuid(value, "mysqlServerUuid")) && database.schema().equals(text(value, "databaseSchema"))
            && database.sourceRecoveryEpoch().equals(text(value, "sourceRecoveryEpoch"))
            && database.sourceRecoveryEpoch().equals(source.identity().sourceRecoveryEpoch()), "RECOVERY_ORIGINAL_OCCUPANCY_DATABASE");
        require(!instant(value, "observedAt").isAfter(request.notAfter()), "RECOVERY_ORIGINAL_OCCUPANCY_TIME");
        require(source.identity().equals(RecoverySourceIdentity.read(value.path("source"))), "RECOVERY_ORIGINAL_OCCUPANCY_SOURCE");
        require(SubmissionJson.sha(SubmissionJson.parse(source.original("payload"))).equals(text(value, "payloadSha256")),
            "RECOVERY_ORIGINAL_OCCUPANCY_PAYLOAD");
        require(Arrays.equals(source.original("result"), text(value, "terminalReceipt").getBytes(StandardCharsets.UTF_8)),
            "RECOVERY_ORIGINAL_OCCUPANCY_TERMINAL");
    }

    private Path outside(Path materials, RecoveryControlLocation location) throws java.io.IOException {
        Path root = RecoveryControlPaths.checked(materials);
        require(!RecoveryControlPaths.overlap(root, location.root()), "RECOVERY_SOURCE_INSIDE_AUTHORITY");
        for (Path restored : location.restoreRoots())
            require(!RecoveryControlPaths.overlap(root, restored), "RECOVERY_SOURCE_INSIDE_RESTORE_SET");
        require(!RecoveryControlPaths.overlap(root, location.checkpoint(checkpoint)), "RECOVERY_SOURCE_INSIDE_CHECKPOINT"); return root;
    }

    private byte[] readFile(Path path) throws java.io.IOException {
        try (var input = Files.newInputStream(RecoveryControlPaths.file(path), LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(RecoveryControlJson.MAX_BYTES + 1);
            require(bytes.length > 0 && bytes.length <= RecoveryControlJson.MAX_BYTES, "RECOVERY_ORIGINAL_OCCUPANCY_SIZE"); return bytes;
        }
    }

    public record Request(Path materialRoot, String independentSha256, RecoveryDeviceRegistrationCapture.Database database, Instant notAfter) {
        public Request { require(materialRoot != null && database != null && notAfter != null, "RECOVERY_ORIGINAL_OCCUPANCY_REQUEST"); }
    }
    public record Proof(RecoverySourceOccupancy.Proof occupancy, String originalSha256, Instant observedAt) {
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
