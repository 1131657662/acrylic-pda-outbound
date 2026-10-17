package com.acrylic.outbound.recovery;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/**
 * One admitted operation root: the retained authority for a request, job or other operation. It is
 * created (and only created) inside the serialized control critical section by
 * {@link RecoveryOperationRegistry#register}, holds one reference to the shared writer lease for its
 * whole life, and hands out one further reference per borrowed connection.
 *
 * <p>The phase machine is deliberately small. A root that is asked for retained work after it
 * started releasing refuses it ({@code CONTROL_SUBOPERATION_FORBIDDEN}) and a released root is no
 * longer resolvable from its token ({@code CONTROL_OPERATION_RELEASED}). Retirement confirms child
 * completion and P5 deletion before releasing the writer. Unconfirmed work keeps its poisoned L2.
 */
public final class RecoveryOperationLease implements AutoCloseable {
    private final RecoveryOperationRegistry registry;
    private final RecoveryOperationToken token;
    private final RecoveryWriterLease writer;
    private final Map<RecoverySubOperation, Integer> open = new EnumMap<>(RecoverySubOperation.class);
    private RecoveryOperationPhase phase = RecoveryOperationPhase.SERVING;
    private boolean retired;
    private boolean quarantined;
    private java.util.UUID leaseId;

    /**
     * The immutable identity of the P5 record published by this root, including after retirement.
     */
    public synchronized java.util.UUID leaseId() { return leaseId; }

    synchronized void attachLease(java.util.UUID published) {
        this.leaseId = Objects.requireNonNull(published, "published");
    }

    RecoveryOperationLease(RecoveryOperationRegistry registry, RecoveryOperationToken token, RecoveryWriterLease writer) {
        this.registry = Objects.requireNonNull(registry);
        this.token = Objects.requireNonNull(token);
        this.writer = Objects.requireNonNull(writer);
    }

    public RecoveryOperationToken token() { return token; }

    public synchronized RecoveryOperationPhase phase() { return phase; }

    /** The dispatch chain has returned; retained work (final session save, error commit) may still run. */
    public synchronized void enterFinalizing() {
        require(!retired && phase == RecoveryOperationPhase.SERVING, "CONTROL_OPERATION_PHASE");
        phase = RecoveryOperationPhase.FINALIZING;
    }

    /** Retirement started: no new sub-operation may be registered. */
    public synchronized void enterReleasing() {
        require(!retired && phase.acceptsNewSubOperations(), "CONTROL_OPERATION_PHASE");
        phase = RecoveryOperationPhase.RELEASING;
    }

    /** Publishes a fence for this root; the transition is irreversible. */
    public synchronized void fence() {
        phase = RecoveryOperationPhase.FENCED;
    }

    /** An unconfirmed process must survive the owner's finally/close as retained, poisoned work. */
    synchronized void quarantineUnconfirmed() {
        require(!retired, "CONTROL_OPERATION_RELEASED");
        quarantined = true;
        phase = RecoveryOperationPhase.FENCED;
        writer.poison();
    }

    /**
     * Registers retained work against this root. Refused ({@code CONTROL_SUBOPERATION_FORBIDDEN})
     * when the root is releasing, released or fenced, or when the sub-operation is outside the set
     * its kind declares.
     */
    public RecoverySubLease registerSubOperation(RecoverySubOperation operation) {
        Objects.requireNonNull(operation, "operation");
        synchronized (this) {
            require(!retired && phase.acceptsNewSubOperations() && token.kind().subOperations().contains(operation),
                "CONTROL_SUBOPERATION_FORBIDDEN");
            open.merge(operation, 1, Integer::sum);
        }
        return new RecoverySubLease(this, operation);
    }

    synchronized void releaseSubOperation(RecoverySubOperation operation) {
        Integer depth = open.get(operation);
        require(depth != null && depth > 0, "CONTROL_SUBLEASE_NOT_HELD");
        if (depth == 1) open.remove(operation); else open.put(operation, depth - 1);
    }

    /**
     * Retained sub-operations still open; retirement requires zero and a non-poisoned writer.
     */
    public synchronized int openSubOperations() {
        int total = 0;
        for (int depth : open.values()) total += depth;
        return total;
    }

    /** One independent reference to the shared writer lease, handed to exactly one connection. */
    RecoveryWriterLease writerReference() {
        synchronized (this) {
            require(!retired, "CONTROL_OPERATION_RELEASED");
            require(phase != RecoveryOperationPhase.FENCED, "RECOVERY_LEASE_FENCED");
        }
        return writer.reference();
    }

    /** Binds this root to the current dispatch of {@code thread}; nested dispatches add depth. */
    public synchronized void bindDispatch(Thread thread) {
        require(!retired && phase.acceptsNewSubOperations(), "CONTROL_OPERATION_PHASE");
        registry.bindDispatch(thread, this);
    }

    /** Ends one dispatch of {@code thread}; the binding goes away when its depth reaches zero. */
    public void unbindDispatch(Thread thread) { registry.unbindDispatch(thread, this); }

    /** How many times {@code thread} is currently bound to this root. */
    public int boundDepth(Thread thread) { return registry.boundDepth(thread, this); }

    /**
     * Drops every dispatch binding of this root on every thread. This belongs to the destroy path of
     * the dispatch owner (the container guarantees it runs last) and is deliberately not part of
     * {@link #close()}: a token that outlives its dispatch must still be observable as released
     * instead of silently becoming "no root at all".
     */
    public void unbindAll() { registry.unbindAll(this); }

    /**
     * Decision failures keep the root/P5/L2 and invoke the original UNKNOWN authority. After P5 is
     * confirmed retired, a release failure is local fencing only, with one bounded retry.
     */
    @Override
    public synchronized void close() {
        if (retired || quarantined) return;
        boolean cleanupConfirmed;
        try {
            phase = RecoveryOperationPhase.RELEASING;
            require(openSubOperations() == 0 && writer.confirmed(), "CONTROL_OPERATION_OUTCOME_UNCONFIRMED");
            // Legacy no-P5 adapters preserve the stale-binding observation; serving P5 roots drain it.
            require(leaseId == null || !registry.hasBindings(this), "CONTROL_OPERATION_BINDINGS_ACTIVE");
            cleanupConfirmed = leaseId == null || registry.retirePublished(leaseId);
        } catch (java.sql.SQLException | RuntimeException | Error error) {
            quarantineUnconfirmed();
            registry.retirementUnknown();
            throw new RecoveryControlException("CONTROL_OPERATION_RETIREMENT_UNCONFIRMED", error);
        }
        retired = true;
        phase = cleanupConfirmed ? RecoveryOperationPhase.RELEASED : RecoveryOperationPhase.FENCED;
        registry.retired(this);
        if (!cleanupConfirmed) { writer.poison(); return; }
        releaseWriter();
    }

    private void releaseWriter() {
        try {
            writer.close();
        } catch (java.io.IOException | RuntimeException | Error first) {
            try { writer.close(); }
            catch (java.io.IOException | RuntimeException | Error again) {
                first.addSuppressed(again);
                writer.poison(); phase = RecoveryOperationPhase.FENCED;
                registry.retirementFenceLocal();
                RecoveryFenceAudit.event("CONTROL_WRITER_RELEASE_UNCONFIRMED", first);
                throw new RecoveryControlException("CONTROL_WRITER_RELEASE_UNCONFIRMED", first);
            }
        }
    }
}
