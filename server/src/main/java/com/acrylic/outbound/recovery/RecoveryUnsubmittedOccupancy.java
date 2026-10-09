package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Independently pinned unsubmitted occupancy, bound to the same frozen payload, terminal and original signed E2A. */
public final class RecoveryUnsubmittedOccupancy {
    private final RecoveryControlLocation configured;
    private final Path checkpoint;
    private final RecoverySourceOccupancy occupancy;

    public RecoveryUnsubmittedOccupancy(RecoveryControlLocation configured, Path checkpoint, RecoveryReceiptKeys keys) {
        this.configured = Objects.requireNonNull(configured); this.checkpoint = Objects.requireNonNull(checkpoint);
        occupancy = new RecoverySourceOccupancy(Objects.requireNonNull(keys));
    }

    public Proof read(Request request, RecoveryPinnedSource source, RecoveryOriginalEvidence.Request evidence) {
        require(request != null && source != null && evidence != null, "RECOVERY_UNSUBMITTED_REQUEST");
        require(request.independentSha256() != null && request.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(request.independentSha256()), "RECOVERY_UNSUBMITTED_PIN");
        try {
            var location = configured.checked(); var reader = new RecoveryControlReader(location); var before = reader.read(checkpoint);
            require(source.control().equals(before) && Set.of("RECOVERING", "VERIFYING", "RELEASE_READY").contains(before.phase()),
                "RECOVERY_UNSUBMITTED_CONTROL");
            Path file = outside(request.materialRoot(), location).resolve(request.independentSha256() + ".json");
            byte[] bytes = readFile(file); var value = readCanonical(bytes);
            require(sha256(bytes).equals(request.independentSha256()), "RECOVERY_UNSUBMITTED_PIN_MISMATCH"); verify(value, request, source);
            var expected = RecoveryUnsubmittedEvidence.bind(source.identity(), source.original("payload"), evidence);
            RecoveryUnsubmittedEvidence.verify(value.path("evidenceSet"), expected);
            var proof = occupancy.verifyUnsubmittedTemporary(source, value.path("occupancy"), evidence);
            require(proof.sourceEvidenceSetId().equals(uuid(value.path("evidenceSet"), "id")), "RECOVERY_UNSUBMITTED_SET_BINDING");
            require(before.equals(reader.read(checkpoint)) && Arrays.equals(bytes, readFile(file)), "RECOVERY_UNSUBMITTED_CHANGED");
            return new Proof(proof, request.independentSha256(), expected.proof().setHash(), instant(value, "observedAt"));
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (java.io.IOException unavailable) { throw new RecoveryControlException("RECOVERY_UNSUBMITTED_UNAVAILABLE", unavailable); }
        catch (RuntimeException malformed) { throw new RecoveryControlException("RECOVERY_UNSUBMITTED_INVALID", malformed); }
    }

    private void verify(JsonNode value, Request request, RecoveryPinnedSource source) {
        fields(value, "formatVersion observationKind mysqlServerUuid databaseSchema sourceRecoveryEpoch observedAt source payloadSha256 terminalReceipt occupancy evidenceSet");
        version(value); var database = request.database();
        require(text(value, "observationKind").equals("ORIGINAL_UNSUBMITTED_TEMP_OCCUPANCY"), "RECOVERY_UNSUBMITTED_KIND");
        require(database.mysqlServerUuid().equals(uuid(value, "mysqlServerUuid")) && database.schema().equals(text(value, "databaseSchema"))
            && database.sourceRecoveryEpoch().equals(text(value, "sourceRecoveryEpoch"))
            && database.sourceRecoveryEpoch().equals(source.identity().sourceRecoveryEpoch()), "RECOVERY_UNSUBMITTED_DATABASE");
        require(!instant(value, "observedAt").isAfter(request.notAfter()), "RECOVERY_UNSUBMITTED_TIME");
        require(source.identity().equals(RecoverySourceIdentity.read(value.path("source"))), "RECOVERY_UNSUBMITTED_SOURCE");
        require(text(value, "payloadSha256").equals(SubmissionJson.sha(SubmissionJson.parse(source.original("payload")))), "RECOVERY_UNSUBMITTED_PAYLOAD");
        require(Arrays.equals(source.original("result"), text(value, "terminalReceipt").getBytes(StandardCharsets.UTF_8)), "RECOVERY_UNSUBMITTED_TERMINAL");
        require(value.path("occupancy").equals(SubmissionJson.parse(source.original("occupancy"))), "RECOVERY_UNSUBMITTED_SOURCE_OCCUPANCY");
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
            require(bytes.length > 0 && bytes.length <= RecoveryControlJson.MAX_BYTES, "RECOVERY_UNSUBMITTED_SIZE"); return bytes;
        }
    }

    public record Request(Path materialRoot, String independentSha256, RecoveryDeviceRegistrationCapture.Database database, Instant notAfter) {
        public Request { require(materialRoot != null && database != null && notAfter != null, "RECOVERY_UNSUBMITTED_REQUEST"); }
    }
    public record Proof(RecoverySourceOccupancy.Proof occupancy, String originalSha256, String originalSetSha256, Instant observedAt) {
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
