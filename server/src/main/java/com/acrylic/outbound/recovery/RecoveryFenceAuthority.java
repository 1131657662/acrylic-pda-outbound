package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;

/** Explicitly supplied real P3/P5 authority. No datasource, request root or Session wiring. */
public final class RecoveryFenceAuthority implements RecoveryEffectGate.Authority {
    private final RecoveryLeaseRoots roots;
    private RecoveryFenceLock signal;
    private RecoveryLeaseProcess process;
    private final List<UUID> operations = new ArrayList<>();
    private volatile boolean localFenced;
    private boolean sequenceStarted;
    private boolean retired;
    private boolean retirementRequested;

    public RecoveryFenceAuthority(RecoveryLeaseRoots roots) { this.roots = Objects.requireNonNull(roots); }

    @Override public synchronized void validateStartup() throws SQLException {
        guarded(() -> {
            signal = RecoveryFenceLock.forRoots(roots);
            coordinated(() -> {
                signal.probe(roots); RecoveryLeaseHistory.check(roots); RecoveryLeaseScan.check(roots);
                if (process == null) process = RecoveryLeaseProcess.open(roots);
                return null;
            });
        });
    }

    @Override public void checkEffect() throws SQLException {
        if (localFenced || signal == null || process == null) throw new SQLException("RECOVERY_LEASE_FENCED");
        guarded(() -> {
            signal.requireLocal();
            coordinated(() -> { signal.probe(roots); RecoveryLeaseScan.check(roots); return null; });
        });
    }

    /** Publishes one operation owned by this process. */
    public synchronized UUID registerOperation() throws SQLException {
        if (retirementRequested) throw new SQLException("CONTROL_RETIREMENT_IN_PROGRESS");
        checkEffect();
        var result = new UUID[1];
        guarded(() -> result[0] = coordinated(() -> {
            signal.requireLocal(); signal.probe(roots); RecoveryLeaseScan.check(roots);
            return RecoveryLeaseScan.publish(roots, process);
        }));
        operations.add(result[0]); return result[0];
    }

    /** Only this live process may retire its own confirmed operation, through the same P3 authority. */
    public synchronized boolean retireOperation(UUID operation) throws SQLException {
        if (!operations.contains(operation)) throw new SQLException("RECOVERY_LEASE_OUTSTANDING");
        var record = new Path[1];
        guarded(() -> coordinated(() -> {
            signal.requireLocal(); signal.probe(roots); RecoveryLeaseScan.check(roots);
            record[0] = RecoveryLeaseRetirement.decide(roots, process, operation);
            // Cleanup is still serialized with observers. Its failure follows confirmed retirement.
            try { RecoveryLeaseRetirement.finish(record[0]); }
            catch (IOException | RuntimeException error) {
                try { RecoveryLeaseRetirement.finish(record[0]); }
                catch (IOException | RuntimeException again) { fenceLocal(); RecoveryFenceAudit.event("CONTROL_RETIREMENT_CLEANUP_UNCONFIRMED", again); }
            }
            return null;
        }));
        operations.remove(operation);
        if (operations.isEmpty() && retirementRequested) finishRetirementAfterOperations();
        return !localFenced;
    }

    @Override public void fenceLocal() {
        localFenced = true;
        if (signal != null) signal.fenceLocal();
    }

    /**
     * Clean retirement, for a process that is shutting down without an unknown outcome: F3 without
     * the unknown audit (release the lifecycle lock and read it back, then close the channel).
     * Fencing itself stays with {@link #fenceUnknown()}: retirement must never claim a fence that
     * never happened.
     */
    public synchronized void retire() throws IOException {
        if (process == null || retired) return;
        if (!operations.isEmpty()) { retirementRequested = true; return; }
        finishRetirement();
    }

    private void finishRetirementAfterOperations() throws SQLException {
        try { finishRetirement(); }
        catch (IOException error) {
            fenceLocal(); RecoveryFenceAudit.event("FENCE_F3_UNCONFIRMED", error);
        }
    }

    private void finishRetirement() throws IOException {
        retired = true;
        try {
            process.releaseLock();
        } finally {
            process.closeChannel();
        }
        RecoveryFenceAudit.event("FENCE_F3_RETIRED", "lifecycle lock released on clean shutdown");
    }

    @Override public synchronized void fenceUnknown() {
        fenceLocal();
        if (sequenceStarted) return;
        sequenceStarted = true;
        RecoveryFenceSequence.run(actions());
    }

    RecoveryFenceSequence.Actions actions() {
        return new RecoveryFenceSequence.Actions() {
            public void hold() throws IOException { signal.hold(roots); }
            public void audit() throws IOException { writeUnknown(); }
            public void release() throws IOException { process.releaseLock(); }
            public void close() throws IOException { process.closeChannel(); }
            public void halt() { Runtime.getRuntime().halt(70); }
        };
    }

    private void writeUnknown() throws IOException {
        for (UUID operation : operations) {
            Path path = roots.root().resolve("leases").resolve(operation + ".unknown.json");
            var value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                .put("operationId", operation.toString()).put("processIdentity", process.id.toString())
                .put("event", "RECOVERY_LEASE_FENCED").put("at", RecoveryLeaseProcess.now());
            Files.writeString(path, RecoveryControlFormat.canonical(value), StandardOpenOption.CREATE_NEW);
        }
    }

    private <T> T coordinated(RecoveryControlLock.Read<T> work) throws IOException {
        roots.verify(); // Includes native coordinator identity before every acquisition.
        return RecoveryControlLock.lockedWaiting(roots.authorityRoot(), RecoveryOperationRegistry.ADMISSION_WAIT_MILLIS,
            () -> { roots.verify(); return work.run(); });
    }
    @FunctionalInterface private interface Work { void run() throws IOException; }
    private void guarded(Work work) throws SQLException {
        try { work.run(); }
        catch (IOException | RuntimeException error) {
            if (error instanceof RecoveryControlException control
                    && "CONTROL_COORDINATOR_BUSY".equals(control.code()))
                throw new SQLException(control.code(), control);
            fenceLocal();
            String code = error instanceof RecoveryControlException control ? control.code() : "CONTROL_AUTHORITY_UNAVAILABLE";
            RecoveryFenceAudit.event(code, error);
            throw new SQLException(code, error);
        }
    }
}
