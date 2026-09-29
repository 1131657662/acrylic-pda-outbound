package com.acrylic.outbound.recovery;

/**
 * One unit of retained work inside an admitted operation root. A root registers a sub-operation
 * before it uses the capability and releases it when the use ends, so a retirement can tell whether
 * anything is still open. The set a root may register is fixed by its {@link RecoveryOperationKind}.
 */
public enum RecoverySubOperation {
    /** A borrowed database connection. */
    CONNECTION,
    /** A database transaction on a borrowed connection. */
    TRANSACTION,
    /** A durable session write (framework session save). */
    SESSION_WRITE,
    /** A read of retained recovery evidence. */
    EVIDENCE_READ,
    /** A hand-off of retained work to another process. */
    PROCESS_HANDOFF
}
