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

    public RecoveryFenceAuthority(RecoveryLeaseRoots roots) { this.roots = Objects.requireNonNull(roots); }

    @Override public synchronized void validateStartup() throws SQLException {
        guarded(() -> {
            signal = RecoveryFenceLock.forRoots(roots);
            coordinated(() -> {
                signal.probe(roots); RecoveryLeaseScan.check(roots);
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

    /** Offline operation registration only; no request integration or retirement is provided. */
    public synchronized UUID registerOperation() throws SQLException {
        checkEffect();
        var result = new UUID[1];
        guarded(() -> result[0] = coordinated(() -> {
            signal.requireLocal(); signal.probe(roots); RecoveryLeaseScan.check(roots);
            return RecoveryLeaseScan.publish(roots, process);
        }));
        operations.add(result[0]); return result[0];
    }

    @Override public void fenceLocal() {
        localFenced = true;
        if (signal != null) signal.fenceLocal();
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
        return RecoveryControlLock.locked(roots.authorityRoot(), () -> { roots.verify(); return work.run(); });
    }
    @FunctionalInterface private interface Work { void run() throws IOException; }
    private void guarded(Work work) throws SQLException {
        try { work.run(); }
        catch (IOException | RuntimeException error) {
            fenceLocal();
            String code = error instanceof RecoveryControlException control ? control.code() : "CONTROL_AUTHORITY_UNAVAILABLE";
            RecoveryFenceAudit.event(code, error);
            throw new SQLException(code, error);
        }
    }
}
