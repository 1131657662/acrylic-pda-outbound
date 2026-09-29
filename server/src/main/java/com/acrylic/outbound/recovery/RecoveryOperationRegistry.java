package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/**
 * The in-memory authority for admitted operation roots: which roots exist, which root a thread is
 * currently dispatching for, and which root the ambient request context carries.
 *
 * <p>A root can only come into existence through {@link #register}, which re-reads the control
 * authority, requires the instance-level identity to be unchanged, requires the token to name that
 * very authority record and then takes the shared writer lease. It therefore has to run inside the
 * serialized control critical section (the writer lease itself requires it); the caller is the
 * admission entry point of its slice, not a request handler.
 *
 * <p>Nothing here grants permission by absence: a thread with no binding and no ambient root is
 * refused by the primary data source, and this registry never falls back to the legacy
 * connection-level path.
 */
public final class RecoveryOperationRegistry {
    /**
     * Request attribute that carries the root token of the dispatch. A request that is still being
     * served but whose thread lost its binding is told apart from a thread that never had a root by
     * this marker alone.
     */
    public static final String REQUEST_ROOT_ATTRIBUTE = "com.acrylic.outbound.recovery.operationRoot";
    /** How long one request waits for the serialized admission section before it is refused. */
    static final long ADMISSION_WAIT_MILLIS = 2_000L;

    private final RecoveryControlLocation location;
    private final Path checkpoint;
    private final RecoveryControlSnapshot startup;
    private final Map<UUID, RecoveryOperationLease> roots = new HashMap<>();
    private final Map<Thread, Map<UUID, Binding>> bindings = new HashMap<>();

    public RecoveryOperationRegistry(RecoveryControlLocation location, Path checkpoint) {
        this.location = Objects.requireNonNull(location);
        this.checkpoint = Objects.requireNonNull(checkpoint);
        this.startup = new RecoveryControlReader(location).read(checkpoint);
    }

    /** The authority identity this instance was started on; every later load must still match it. */
    public RecoveryControlSnapshot startup() { return startup; }

    /**
     * The request-entry admission: the caller is a dispatch thread that does not hold the
     * coordinator lock, so this takes the serialized control critical section and creates exactly
     * one root inside it. It waits for that section instead of failing at once, so concurrent
     * requests serialize rather than being refused; the wait is bounded and running out of it is
     * still a refusal. It is the subset of the design's admission sequence (S0–S4) that the request
     * wiring needs today: authority identity, the token's binding to it, and the shared writer
     * lease. The remaining admission steps (P2 evidence, P5 lease publication and probes) belong to
     * the admission gate of a later slice and are not claimed here.
     */
    public RecoveryOperationLease admit(RecoveryOperationToken token) {
        Objects.requireNonNull(token, "token");
        try {
            return RecoveryControlLock.lockedWaiting(location.checked().root(), ADMISSION_WAIT_MILLIS,
                () -> register(token));
        } catch (IOException error) {
            throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error);
        }
    }

    /**
     * Creates the root of one operation and takes its writer lease. Must be called by the thread
     * that already holds this authority's coordinator lock; otherwise the writer lease refuses with
     * {@code CONTROL_COORDINATOR_REQUIRED}.
     */
    public RecoveryOperationLease register(RecoveryOperationToken token) {
        Objects.requireNonNull(token, "token");
        RecoveryControlSnapshot now = authority();
        require(token.installationId().equals(now.installationId())
            && token.authorityRecordSha256().equals(now.recordSha256()), "CONTROL_INSTANCE_STALE");
        RecoveryWriterLease writer = writer();
        RecoveryOperationLease lease = new RecoveryOperationLease(this, token, writer);
        synchronized (this) {
            require(!roots.containsKey(token.operationId()), "CONTROL_OPERATION_DUPLICATE");
            roots.put(token.operationId(), lease);
        }
        return lease;
    }

    /** The live root a token still resolves to, or {@code null} once it was released or never existed. */
    RecoveryOperationLease live(RecoveryOperationToken token) {
        synchronized (this) {
            RecoveryOperationLease lease = roots.get(token.operationId());
            return lease != null && lease.token().equals(token) ? lease : null;
        }
    }

    /**
     * The token of the root {@code thread} is dispatching for, or {@code null}. A thread may only
     * carry one root's dispatch scope; if two are somehow bound at once this returns {@code null}
     * rather than guessing, so the caller fails closed.
     */
    RecoveryOperationToken bound(Thread thread) {
        synchronized (this) {
            Map<UUID, Binding> held = bindings.get(thread);
            if (held == null || held.isEmpty()) return null;
            RecoveryOperationToken found = null;
            for (Binding binding : held.values()) {
                if (binding.depth <= 0) continue;
                if (found != null) return null;
                found = binding.token;
            }
            return found;
        }
    }

    /**
     * The root token the ambient request context carries, or {@code null}. The admission filter of a
     * later slice publishes the token here for the whole request, which is why an unbound thread
     * inside such a request is refused differently from a thread that never had a root.
     */
    public RecoveryOperationToken ambient() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) return null;
        Object value = attributes.getAttribute(REQUEST_ROOT_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof RecoveryOperationToken token ? token : null;
    }

    void bindDispatch(Thread thread, RecoveryOperationLease lease) {
        synchronized (this) {
            Map<UUID, Binding> held = bindings.computeIfAbsent(thread, key -> new LinkedHashMap<>());
            UUID id = lease.token().operationId();
            Binding binding = held.get(id);
            if (binding == null) held.put(id, new Binding(lease.token(), 1));
            else { require(binding.token.equals(lease.token()), "CONTROL_OPERATION_BINDING_MISMATCH"); binding.depth++; }
        }
    }

    void unbindDispatch(Thread thread, RecoveryOperationLease lease) {
        synchronized (this) {
            Map<UUID, Binding> held = bindings.get(thread);
            if (held == null) return;
            Binding binding = held.get(lease.token().operationId());
            require(binding != null && binding.token.equals(lease.token()), "CONTROL_OPERATION_BINDING_MISSING");
            if (--binding.depth <= 0) held.remove(lease.token().operationId());
            if (held.isEmpty()) bindings.remove(thread);
        }
    }

    /** Drops every binding of this root, on every thread; the destroy path of the dispatch owner. */
    void unbindAll(RecoveryOperationLease lease) {
        synchronized (this) {
            for (Iterator<Map.Entry<Thread, Map<UUID, Binding>>> threads = bindings.entrySet().iterator(); threads.hasNext(); ) {
                Map<UUID, Binding> held = threads.next().getValue();
                held.remove(lease.token().operationId());
                if (held.isEmpty()) threads.remove();
            }
        }
    }

    int boundDepth(Thread thread, RecoveryOperationLease lease) {
        synchronized (this) {
            Map<UUID, Binding> held = bindings.get(thread);
            if (held == null) return 0;
            Binding binding = held.get(lease.token().operationId());
            return binding != null && binding.token.equals(lease.token()) ? binding.depth : 0;
        }
    }

    /** How many roots are live right now; used by admission checks and by tests. */
    public int liveRoots() {
        synchronized (this) { return roots.size(); }
    }

    void retired(RecoveryOperationLease lease) {
        synchronized (this) { roots.remove(lease.token().operationId(), lease); }
    }

    private RecoveryControlSnapshot authority() {
        try {
            RecoveryControlLocation checked = location.checked();
            RecoveryControlLocation current = location.checked();
            require(checked.equals(current), "CONTROL_AUTHORITY_CHANGED");
            Path trusted = current.checkpoint(checkpoint);
            RecoveryControlSnapshot now = RecoveryControlHistory.load(current, trusted, RecoveryControlPurpose.NORMAL_ADMISSION);
            require(startup.equals(now), "CONTROL_INSTANCE_STALE");
            return now;
        } catch (IOException error) {
            throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error);
        }
    }

    private RecoveryWriterLease writer() {
        try {
            return RecoveryWriterLease.acquire(location.checked().root());
        } catch (IOException error) {
            throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error);
        }
    }

    private static final class Binding {
        private final RecoveryOperationToken token;
        private int depth;

        private Binding(RecoveryOperationToken token, int depth) {
            this.token = token;
            this.depth = depth;
        }
    }
}
