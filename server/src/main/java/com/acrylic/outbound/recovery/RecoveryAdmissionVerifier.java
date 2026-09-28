package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Read-only runtime check that the external authority and the database admission agree. It is not a
 * bean, never writes, never registers and never initializes NORMAL; a missing or contradictory side
 * is reported as not admitted so callers stay closed.
 */
public final class RecoveryAdmissionVerifier {
    /** Snapshot check only. C2 operation leases and production entry wiring remain separate. */
    public static Result verifyInitial(RecoveryAdmissionEvidence evidence, InitialNormalStore store, Path closedAuthority) {
        return InitialNormalVerification.verify(evidence, store, closedAuthority);
    }
    private final RecoveryAdmissionEvidence evidence;
    private final Supplier<RecoveryControlSnapshot> authority;
    private final Supplier<RecoveryIntentView> intent;

    public RecoveryAdmissionVerifier(RecoveryAdmissionEvidence evidence,
            Supplier<RecoveryControlSnapshot> authority, Supplier<RecoveryIntentView> intent) {
        this.evidence = Objects.requireNonNull(evidence);
        this.authority = Objects.requireNonNull(authority);
        this.intent = Objects.requireNonNull(intent);
    }

    /** Reads the durable external intent a second time, so the check compares rather than trusts. */
    public interface RecoveryIntentView { long revision(); String recordSha256(); String intentSha256(); }

    public Result verify() {
        List<String> gaps = new ArrayList<>();
        RecoveryControlSnapshot control;
        try {
            control = authority.get();
        } catch (RuntimeException error) {
            gaps.add("AUTHORITY_UNAVAILABLE");
            return Result.denied(gaps, null);
        }
        RecoveryIntentView durable;
        try {
            durable = intent.get();
        } catch (RuntimeException error) {
            gaps.add("DURABLE_INTENT_UNAVAILABLE");
            return Result.denied(gaps, control.installationId().toString());
        }
        if (durable == null) gaps.add("DURABLE_INTENT_MISSING");
        else {
            if (durable.revision() != Long.parseLong(control.revision())) gaps.add("DURABLE_INTENT_REVISION");
            if (!durable.recordSha256().equals(control.recordSha256())) gaps.add("DURABLE_INTENT_HASH");
        }
        RecoveryAdmissionEvidence.Binding binding;
        RecoveryAdmissionEvidence.Admission admission;
        RecoveryAdmissionEvidence.Projection projection;
        RecoveryAdmissionEvidence.State state;
        try {
            binding = evidence.binding();
            admission = evidence.admission();
            projection = evidence.projection();
            state = evidence.state();
        } catch (Exception error) {
            gaps.add("DATABASE_EVIDENCE_UNAVAILABLE");
            return Result.denied(gaps, control.installationId().toString());
        }
        if (binding == null) gaps.add("DATABASE_BINDING_MISSING");
        else if (!binding.installation().equals(control.installationId())) gaps.add("DATABASE_INSTALLATION");
        if (admission == null) gaps.add("NORMAL_ADMISSION_MISSING");
        if (projection == null) gaps.add("PROJECTION_MISSING");
        else {
            if (!"RELEASE_READY".equals(projection.phase())) gaps.add("PROJECTION_PHASE");
            if (!projection.hash().equals(control.recordSha256())) gaps.add("PROJECTION_HASH");
            if (projection.revision() != Long.parseLong(control.revision())) gaps.add("PROJECTION_REVISION");
        }
        if (state == null || !"NORMAL".equals(state.mode())) gaps.add("SERVER_MODE");
        if (admission != null && projection != null) {
            if (admission.authorityRevision() != projection.revision()
                || !admission.recordSha256().equals(projection.hash())) gaps.add("ADMISSION_FROZEN");
            if (!admission.recordSha256().equals(control.recordSha256())) gaps.add("ADMISSION_AUTHORITY");
        }
        if (durable != null && admission != null && !admission.intentSha256().equals(durable.intentSha256()))
            gaps.add("ADMISSION_INTENT");
        if (state != null && projection != null && state.numeric() != projection.numeric()) gaps.add("SERVER_NUMERIC");
        String installation = binding == null ? null : binding.installation().toString();
        if (!gaps.isEmpty()) return Result.denied(gaps, installation);
        return new Result(true, List.of(), installation, control.revision(), projection.numeric(),
            projection.hash(), false, false);
    }

    /** Closes a configured-authority verifier over real database rows. Never registers anything. */
    public static RecoveryAdmissionVerifier of(RecoveryAdmissionEvidence evidence,
            RecoveryControlReader reader, Path checkpoint, RecoveryControlLocation location, long baseline) {
        Supplier<RecoveryControlSnapshot> authority = () -> reader.read(checkpoint);
        Supplier<RecoveryIntentView> intent = () -> {
            try {
                var loaded = RecoveryNormalIntent.load(location, location.root(), baseline).intent();
                if (loaded == null) return null;
                return new RecoveryIntentView() {
                    @Override public long revision() { return loaded.revision(); }
                    @Override public String recordSha256() { return loaded.recordSha256(); }
                    @Override public String intentSha256() { return loaded.intentSha256(); }
                };
            } catch (java.io.IOException error) {
                throw new RecoveryControlException("CONTROL_NORMAL_INTENT_UNREADABLE", error);
            }
        };
        return new RecoveryAdmissionVerifier(evidence, authority, intent);
    }

    public record Result(boolean admitted, List<String> gaps, String installationId, String controlRevision,
        long numericEpoch, String recordSha256, boolean ordinaryWritesAllowed, boolean localDeletionAllowed) {
        public Result { gaps = List.copyOf(gaps); }
        static Result denied(List<String> gaps, String installation) {
            return new Result(false, gaps, installation, null, 0, null, false, false);
        }
    }
}
