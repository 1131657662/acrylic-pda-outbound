package com.acrylic.outbound.recovery;

import java.util.Set;

/**
 * What an admitted operation root stands for. The kind fixes the sub-operations that root may
 * register, so a render hand-off cannot silently borrow a business connection and an evidence
 * reader cannot write.
 *
 * <p>The mapping below is the conservative reading of the design's operation kinds: it is declared
 * here so that "a sub-operation outside the set" is one reviewable table rather than a per-call
 * decision. It is not yet consumed by a production admission entry point.
 */
public enum RecoveryOperationKind {
    /** One HTTP request served by this instance. */
    HTTP_REQUEST(Set.of(RecoverySubOperation.CONNECTION, RecoverySubOperation.TRANSACTION,
        RecoverySubOperation.SESSION_WRITE, RecoverySubOperation.EVIDENCE_READ)),
    /** One background job run by this instance. */
    WORKER_JOB(Set.of(RecoverySubOperation.CONNECTION, RecoverySubOperation.TRANSACTION,
        RecoverySubOperation.EVIDENCE_READ, RecoverySubOperation.PROCESS_HANDOFF)),
    /** One durable file write performed by this instance. */
    FILE_WRITE(Set.of(RecoverySubOperation.CONNECTION, RecoverySubOperation.TRANSACTION)),
    /** One rendering subprocess; it may read evidence and hand work off, never write business rows. */
    RENDER_PROCESS(Set.of(RecoverySubOperation.EVIDENCE_READ, RecoverySubOperation.PROCESS_HANDOFF)),
    /** Explicit initialization work outside a request. */
    INITIALIZATION(Set.of(RecoverySubOperation.CONNECTION, RecoverySubOperation.TRANSACTION,
        RecoverySubOperation.EVIDENCE_READ));

    private final Set<RecoverySubOperation> subOperations;

    RecoveryOperationKind(Set<RecoverySubOperation> subOperations) {
        this.subOperations = Set.copyOf(subOperations);
    }

    /** The sub-operations a root of this kind may register; anything else is refused, not downgraded. */
    public Set<RecoverySubOperation> subOperations() { return subOperations; }
}
