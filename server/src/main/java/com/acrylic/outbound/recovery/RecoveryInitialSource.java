package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Same pinned first-round source, rechecked together. No database write, signature or work permission. */
public final class RecoveryInitialSource {
    private final RecoverySourceReader sources;
    private final RecoveryReceiptKeys keys;

    public RecoveryInitialSource(RecoverySourceReader sources, RecoveryReceiptKeys keys) {
        this.sources = Objects.requireNonNull(sources); this.keys = Objects.requireNonNull(keys);
    }

    public Proof read(Request request) {
        return read(request, null);
    }

    /** Native TEMP freezes carry events only; the original signed E2A supplies full device coverage. */
    public Proof readTemporary(Request request, RecoveryOriginalEvidence.Request evidence) {
        require(request != null && request.expected().sourceKind().equals("TEMP") && request.auditPrefix().isEmpty()
            && evidence != null, "RECOVERY_INITIAL_NATIVE_REQUEST");
        return read(request, evidence);
    }

    private Proof read(Request request, RecoveryOriginalEvidence.Request evidence) {
        require(request != null && request.expected().workRound() == 1, "RECOVERY_INITIAL_ROUND");
        var source = sources.read(request.materialRoot(), request.independentManifestSha256(), request.expected());
        try {
            var proof = verify(source, request.auditPrefix(), evidence);
            var after = sources.read(request.materialRoot(), request.independentManifestSha256(), request.expected());
            require(source.control().equals(after.control()), "RECOVERY_INITIAL_CONTROL_CHANGED");
            for (String kind : RecoverySourceManifest.OBJECTS)
                require(Arrays.equals(source.original(kind), after.original(kind)), "RECOVERY_INITIAL_SOURCE_CHANGED");
            return proof;
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_INITIAL_INVALID", invalid); }
    }

    private Proof verify(RecoveryPinnedSource source, List<RecoveryAuditArchiveProof.Segment> prefix,
            RecoveryOriginalEvidence.Request evidence) {
        var activation = activation(source);
        var claims = new RecoverySourceAuthority(keys).verify(source);
        new RecoverySourceQualifications(keys).verify(source, Map.of(uuid(claims, "jti"), source));
        var terminal = new RecoverySourceTerminal(new RecoveryReceiptVerifier(keys)).verify(source);
        require("ABORTED".equals(text(terminal, "status")), "RECOVERY_INITIAL_TERMINAL");
        var identity = source.identity();
        var events = evidence == null ? new RecoverySourceAudit(keys).verify(source.original("payload"),
            new RecoverySourceAudit.Context(identity, Map.of(identity.qualificationId(), identity), prefix))
            : RecoveryNativeTemporaryAudit.verify(source, evidence);
        RecoveryInitialRecords.verify(source, events);
        var permission = new RecoverySourcePermission(keys).verify(source);
        var occupancy = new RecoverySourceOccupancy(keys).verify(source);
        String payloadHash = SubmissionJson.sha(SubmissionJson.parse(source.original("payload")));
        require(payloadHash.equals(activation.preparation().payloadSha256()) && payloadHash.equals(permission.payloadSha256())
            && payloadHash.equals(occupancy.payloadSha256()), "RECOVERY_INITIAL_PAYLOAD");
        var originals = new HashMap<String, String>();
        for (String kind : RecoverySourceManifest.OBJECTS) originals.put(kind, sha256(source.original(kind)));
        return new Proof(identity, source.control(), source.manifestSha256(), payloadHash, Map.copyOf(originals),
            events.size(), occupancy.rows().size(), activation.activatedAt());
    }

    private Activation activation(RecoveryPinnedSource source) {
        if (source.identity().sourceKind().equals("FORMAL")) {
            var proof = new RecoverySourceFormalActivation(keys).verify(source);
            return new Activation(proof.preparation(), proof.activatedAt());
        }
        var proof = new RecoverySourceTemporaryActivation(keys).verify(source);
        return new Activation(proof.baseline().preparation(), proof.activatedAt());
    }

    public record Request(Path materialRoot, String independentManifestSha256, RecoverySourceIdentity expected,
            List<RecoveryAuditArchiveProof.Segment> auditPrefix) {
        public Request {
            require(materialRoot != null && expected != null && auditPrefix != null && auditPrefix.size() <= 1024,
                "RECOVERY_INITIAL_REQUEST");
            auditPrefix = List.copyOf(auditPrefix);
        }
    }

    public record Proof(RecoverySourceIdentity identity, RecoveryControlSnapshot control, String manifestSha256,
            String payloadSha256, Map<String, String> originalSha256, long auditEventCount, long retainedRowCount, Instant activatedAt) {
        public Proof { originalSha256 = Map.copyOf(originalSha256); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
    private record Activation(RecoverySourcePreparation.Proof preparation, Instant activatedAt) { }
}
