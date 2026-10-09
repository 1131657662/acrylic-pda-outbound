package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Composes original unsubmitted TEMP materials; historical permission provenance and actual restoration remain separate. */
public final class RecoveryInitialUnsubmittedSource {
    private final RecoverySourceReader sources;
    private final RecoveryUnsubmittedOccupancy occupancy;
    private final RecoveryReceiptKeys keys;

    public RecoveryInitialUnsubmittedSource(RecoverySourceReader sources, RecoveryUnsubmittedOccupancy occupancy, RecoveryReceiptKeys keys) {
        this.sources = Objects.requireNonNull(sources); this.occupancy = Objects.requireNonNull(occupancy); this.keys = Objects.requireNonNull(keys);
    }

    public Proof read(Request request) {
        require(request != null, "RECOVERY_INITIAL_UNSUBMITTED_REQUEST");
        var original = request.original();
        require(original.expected().sourceKind().equals("TEMP") && original.expected().workRound() == 1 && original.auditPrefix().isEmpty(),
            "RECOVERY_INITIAL_UNSUBMITTED_SOURCE");
        var source = sources.read(original.materialRoot(), original.independentManifestSha256(), original.expected());
        try {
            var proof = verify(source, request);
            var after = sources.read(original.materialRoot(), original.independentManifestSha256(), original.expected());
            require(source.control().equals(after.control()), "RECOVERY_INITIAL_CONTROL_CHANGED");
            for (String kind : RecoverySourceManifest.OBJECTS)
                require(Arrays.equals(source.original(kind), after.original(kind)), "RECOVERY_INITIAL_SOURCE_CHANGED");
            var finalOccupancy = occupancy.read(request.occupancy(), after, request.evidence());
            require(proof.originalOccupancySha256().equals(finalOccupancy.originalSha256())
                && proof.originalSetSha256().equals(finalOccupancy.originalSetSha256()), "RECOVERY_INITIAL_UNSUBMITTED_CHANGED");
            return proof;
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_INITIAL_UNSUBMITTED_INVALID", invalid); }
    }

    private Proof verify(RecoveryPinnedSource source, Request request) {
        var activation = new RecoverySourceTemporaryActivation(keys).verify(source);
        var claims = new RecoverySourceAuthority(keys).verify(source);
        new RecoverySourceQualifications(keys).verify(source, Map.of(uuid(claims, "jti"), source));
        var events = RecoveryNativeTemporaryAudit.verify(source, request.evidence());
        RecoveryInitialRecords.verify(source, events);
        var permission = new RecoverySourcePermission(keys).verify(source);
        var originalOccupancy = occupancy.read(request.occupancy(), source, request.evidence());
        String payloadHash = SubmissionJson.sha(SubmissionJson.parse(source.original("payload")));
        require(payloadHash.equals(activation.baseline().preparation().payloadSha256()) && payloadHash.equals(permission.payloadSha256())
            && payloadHash.equals(originalOccupancy.occupancy().payloadSha256()), "RECOVERY_INITIAL_PAYLOAD");
        var originals = new HashMap<String,String>();
        for (String kind : RecoverySourceManifest.OBJECTS) originals.put(kind, sha256(source.original(kind)));
        return new Proof(source.identity(), source.control(), source.manifestSha256(), payloadHash, originals,
            originalOccupancy.originalSha256(), originalOccupancy.originalSetSha256(), events.size(),
            originalOccupancy.occupancy().rows().size(), activation.activatedAt());
    }

    public record Request(RecoveryInitialSource.Request original, RecoveryOriginalEvidence.Request evidence,
            RecoveryUnsubmittedOccupancy.Request occupancy) {
        public Request { require(original != null && evidence != null && occupancy != null, "RECOVERY_INITIAL_UNSUBMITTED_REQUEST"); }
    }
    public record Proof(RecoverySourceIdentity identity, RecoveryControlSnapshot control, String manifestSha256, String payloadSha256,
            Map<String,String> originalSha256, String originalOccupancySha256, String originalSetSha256,
            long auditEventCount, long retainedRowCount, Instant activatedAt) {
        public Proof { originalSha256 = Map.copyOf(originalSha256); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
