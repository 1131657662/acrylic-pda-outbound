package com.acrylic.outbound.recovery;

/**
 * Optional test seam for the admitted-connection boundary. The production path uses {@link #NOOP},
 * so the only effect is one no-op call inside the serialized section; no wiring, bean or capability
 * is attached to it. It exists so a caller can force the exact interleaving of an in-flight
 * execution and a concurrent close instead of relying on the thread scheduler.
 */
interface RecoveryAdmissionProbe {
    RecoveryAdmissionProbe NOOP = () -> { };

    /**
     * Runs while the admitted connection is inside its serialized boundary and before the delegate
     * call. It fires for connection-level calls and for the statement/result-set calls that share
     * that boundary.
     */
    void aroundDelegateCall();
}
