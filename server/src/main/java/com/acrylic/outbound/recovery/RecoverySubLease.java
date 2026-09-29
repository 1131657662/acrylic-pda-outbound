package com.acrylic.outbound.recovery;

import java.util.Objects;

/**
 * Handle for one registered unit of retained work. Closing it is what tells the root that the work
 * ended; closing twice is a no-op so a connection that is closed twice cannot corrupt the count.
 */
public final class RecoverySubLease implements AutoCloseable {
    private final RecoveryOperationLease root;
    private final RecoverySubOperation operation;
    private boolean closed;

    RecoverySubLease(RecoveryOperationLease root, RecoverySubOperation operation) {
        this.root = Objects.requireNonNull(root);
        this.operation = Objects.requireNonNull(operation);
    }

    public RecoverySubOperation operation() { return operation; }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        root.releaseSubOperation(operation);
    }
}
