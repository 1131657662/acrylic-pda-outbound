package com.acrylic.outbound.recovery;

import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * The initialization window of an admitted instance: one explicit {@code INITIALIZATION} root,
 * created before any application bean and bound to the thread that refreshes the context. Every
 * startup consumer of the primary data source — a bean constructor reading {@code server_state}, a
 * command-line bootstrap or a mode notice — therefore runs inside a root, exactly like a request
 * does, instead of outside every root.
 *
 * <p>The window closes when the application is ready: the root's bindings are cleared and the root
 * is retired, so business traffic starts from a state with no live root and the request count stays
 * the only source of roots. An unconfirmed authority fails startup (admission refuses), which keeps
 * the "explicit permission first" order of the design.
 */
public final class RecoveryInitializationWindow implements ApplicationListener<ApplicationReadyEvent>, AutoCloseable {
    private final RecoveryOperationRegistry operations;
    private RecoveryOperationLease root;

    public RecoveryInitializationWindow(RecoveryOperationRegistry operations) {
        this.operations = Objects.requireNonNull(operations);
        var token = new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.INITIALIZATION,
            operations.startup().installationId(), operations.startup().recordSha256(), null);
        this.root = operations.admit(token);
        this.root.bindDispatch(Thread.currentThread());
    }

    /** The root that covers startup; {@code null} once the window is closed. */
    RecoveryOperationLease root() { return root; }

    /** The window covers the framework's own startup runners as well, so it closes only when ready. */
    @Override public void onApplicationEvent(ApplicationReadyEvent event) { close(); }

    @Override public synchronized void close() {
        if (root == null) return;
        RecoveryOperationLease open = root;
        root = null;
        open.unbindAll();
        open.close();
    }

    @Override public String toString() {
        return "RecoveryInitializationWindow[root=" + (root == null ? "closed" : "open") + "]";
    }
}
