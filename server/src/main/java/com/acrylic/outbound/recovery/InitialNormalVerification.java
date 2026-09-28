package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Independent initial evidence never releases a closed recovery chain or grants capabilities. */
final class InitialNormalVerification {
    private InitialNormalVerification() { }

    static RecoveryAdmissionVerifier.Result verify(RecoveryAdmissionEvidence evidence, InitialNormalStore store, Path closed) {
        try {
            return store.locked(() -> {
                store.requireUnclosed(closed);
                var external = store.inspectLocked();
                require(external.committed(), "INITIAL_NORMAL_COMPLETION_MISSING");
                match(external.request(), read(evidence), true);
                store.requireUnclosed(closed);
                var request = external.request();
                return new RecoveryAdmissionVerifier.Result(true, List.of(), request.installation().toString(),
                    null, request.numericEpoch(), request.sha256(), false, false);
            });
        } catch (RecoveryControlException failure) {
            return RecoveryAdmissionVerifier.Result.denied(List.of(failure.getMessage()), null);
        } catch (RuntimeException failure) {
            return RecoveryAdmissionVerifier.Result.denied(List.of("INITIAL_NORMAL_EVIDENCE_UNAVAILABLE"), null);
        }
    }

    static void match(InitialNormalEvidence original, RecoveryAdmissionEvidence.Initial db, boolean requireRow) {
        require(db != null, "INITIAL_NORMAL_DATABASE_MISSING");
        require(original.serverUuid().equals(db.serverUuid()) && original.schema().equals(db.schema()),
            "INITIAL_NORMAL_DATABASE_IDENTITY");
        require(db.state() != null && "NORMAL".equals(db.state().mode())
            && original.numericEpoch() == db.state().numeric(), "INITIAL_NORMAL_DATABASE_STATE");
        require(!db.closedHistory(), "INITIAL_NORMAL_CLOSED_HISTORY");
        if (requireRow) require(db.request() != null, "INITIAL_NORMAL_DATABASE_MISSING");
        if (db.request() != null) require(Arrays.equals(original.originalBytes(), db.request().originalBytes()),
            "INITIAL_NORMAL_DATABASE_REQUEST");
    }

    private static RecoveryAdmissionEvidence.Initial read(RecoveryAdmissionEvidence evidence) {
        try { return evidence.initial(); }
        catch (Exception error) { throw new RecoveryControlException("INITIAL_NORMAL_DATABASE_UNAVAILABLE", error); }
    }
}
