package com.acrylic.outbound.recovery;

import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import java.util.Objects;

/**
 * The container's last boundary for a request. It clears every dispatch binding of the request's
 * root — including a binding leaked on another thread — and retires the root, so a token can never
 * outlive the request it was created for and the number of live roots stays equal to the number of
 * requests in flight.
 *
 * <p>A request that never obtained a root (refused admission, or an unadmitted path) is ignored.
 */
public final class RecoveryRequestRootListener implements ServletRequestListener {
    private final RecoveryOperationRegistry operations;

    public RecoveryRequestRootListener(RecoveryOperationRegistry operations) {
        this.operations = Objects.requireNonNull(operations);
    }

    @Override public void requestDestroyed(ServletRequestEvent event) {
        Object held = event.getServletRequest().getAttribute(RecoveryOperationRegistry.REQUEST_ROOT_ATTRIBUTE);
        if (!(held instanceof RecoveryOperationToken token)) return;
        RecoveryOperationLease root = operations.live(token);
        if (root == null) return;
        root.unbindAll();
        root.close();
    }
}
