package com.acrylic.outbound.recovery;

/** Exact round selection; first-round originals are never replaced by the newest available file. */
final class RecoveryAdmissionIntents {
    private RecoveryAdmissionIntents() { }

    static RecoveryAdmissionVerifier.RecoveryIntentView read(RecoveryControlSnapshot control,
            RecoveryControlLocation location, long baseline) {
        try {
            var loaded = Long.parseLong(control.revision()) > 4
                ? RecoveryDatabaseControl.readOnly(location, control).normal(Long.parseLong(control.revision()))
                : RecoveryNormalIntent.load(location, location.root(), baseline).intent();
            if (loaded == null) return null;
            return new RecoveryAdmissionVerifier.RecoveryIntentView() {
                public long revision() { return loaded.revision(); }
                public String recordSha256() { return loaded.recordSha256(); }
                public String intentSha256() { return loaded.intentSha256(); }
                public String schema() { return loaded.schema(); }
                public long baseline() { return loaded.baseline(); }
            };
        } catch (java.io.IOException error) {
            throw new RecoveryControlException("CONTROL_NORMAL_INTENT_UNREADABLE", error);
        }
    }
}
