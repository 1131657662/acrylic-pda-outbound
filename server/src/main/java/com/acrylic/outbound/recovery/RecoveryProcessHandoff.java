package com.acrylic.outbound.recovery;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Retains the dispatch root until a renderer's termination has been confirmed. */
@Component
public final class RecoveryProcessHandoff {
    private final ObjectProvider<RecoveryOperationRegistry> operations;
    private final ObjectProvider<RecoveryEffectGate> effects;

    public RecoveryProcessHandoff(ObjectProvider<RecoveryOperationRegistry> operations,
            ObjectProvider<RecoveryEffectGate> effects) {
        this.operations = operations;
        this.effects = effects;
    }

    public Lease begin() {
        var registry = operations.getIfAvailable();
        var gate = effects.getIfAvailable(RecoveryEffectGate::disabled);
        if (registry == null) return new Lease(null, null, gate);
        var token = registry.bound(Thread.currentThread());
        if (token == null) throw new RecoveryControlException(registry.ambient() == null
            ? "RECOVERY_OPERATION_REQUIRED" : "RECOVERY_OPERATION_CONTEXT_LOST");
        var root = registry.live(token);
        if (root == null) throw new RecoveryControlException("CONTROL_OPERATION_RELEASED");
        if (root.phase() == RecoveryOperationPhase.FENCED) throw new RecoveryControlException("RECOVERY_LEASE_FENCED");
        var retained = root.registerSubOperation(RecoverySubOperation.PROCESS_HANDOFF);
        return new Lease(root, retained, gate);
    }

    public static final class Lease implements AutoCloseable {
        private final RecoveryOperationLease root;
        private final RecoverySubLease retained;
        private final RecoveryEffectGate effects;
        private boolean unconfirmed;

        private Lease(RecoveryOperationLease root, RecoverySubLease retained, RecoveryEffectGate effects) {
            this.root = root; this.retained = retained; this.effects = effects;
        }

        /** Poison L2 before the existing authority's F1/F1prime/F2/F3/F4/F5 sequence. */
        public synchronized void unconfirmed() {
            if (unconfirmed) return;
            unconfirmed = true;
            if (root != null) root.quarantineUnconfirmed();
            effects.poison();
        }

        @Override public synchronized void close() {
            if (!unconfirmed && retained != null) retained.close();
        }
    }
}
