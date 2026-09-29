package com.acrylic.outbound.recovery;

/**
 * Lifecycle of an admitted operation root. Only {@link #SERVING} and {@link #FINALIZING} accept new
 * sub-operations, so the final session save and an error-dispatch commit stay covered while a root
 * that has started releasing, has been released or has been fenced accepts nothing new.
 */
public enum RecoveryOperationPhase {
    /** The operation is serving; every declared sub-operation may be registered. */
    SERVING,
    /** The dispatch chain has returned; retained work such as the final session save may still run. */
    FINALIZING,
    /** Retirement started; no new sub-operation may be registered. */
    RELEASING,
    /** The root is gone; its token can no longer be resolved to a live root. */
    RELEASED,
    /** A fence was published for this root; nothing may be borrowed through it. */
    FENCED;

    /** Whether a sub-operation registered now is still inside the retained surface of the root. */
    public boolean acceptsNewSubOperations() {
        return this == SERVING || this == FINALIZING;
    }
}
