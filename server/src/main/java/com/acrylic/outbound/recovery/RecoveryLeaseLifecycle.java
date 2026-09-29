package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Application wiring for the P5 root (design rows 8–9): the explicitly supplied P5 identity, the
 * fence channel, the orphan-lease scan and the process record with its lifecycle lock, all taken
 * once per process and released on retirement.
 *
 * <p>It owns exactly one {@link RecoveryFenceAuthority} — the same object the effect gate attaches
 * to — so a second wiring cannot silently hold a second lifecycle lock. Nothing here reads or
 * repairs a missing P5 tree: {@code verify} refuses (startup fails closed) instead, and only the
 * explicit supplier may provision.
 *
 * <p>The anchor is an out-of-band secret; only its identity is committed inside the writable tree.
 */
public final class RecoveryLeaseLifecycle implements AutoCloseable {
    private final RecoveryFenceAuthority authority;

    private RecoveryLeaseLifecycle(RecoveryFenceAuthority authority) {
        this.authority = authority;
    }

    /**
     * Verifies the pinned P5 binding and opens the process record inside the coordinator lock.
     * Refusals keep their control code so startup failures name the check that refused.
     */
    static RecoveryLeaseLifecycle open(RecoveryLeaseRoots.Binding binding, String markerSha256, String anchorHex) {
        Objects.requireNonNull(binding, "binding");
        if (markerSha256 == null || markerSha256.isBlank()) {
            throw new RecoveryControlException("RECOVERY_LEASE_ROOT_UNBOUND", null);
        }
        if (anchorHex == null || !anchorHex.matches("(?:[a-f0-9]{2}){32}")) {
            throw new RecoveryControlException("CONTROL_ANCHOR_REQUIRED", null);
        }
        var roots = new RecoveryLeaseRoots(binding, markerSha256, RecoveryControlFormat.bytes(anchorHex));
        var authority = new RecoveryFenceAuthority(roots);
        try {
            authority.validateStartup();
        } catch (SQLException refused) {
            if (refused.getCause() instanceof RecoveryControlException control) throw control;
            throw new RecoveryControlException(refused.getMessage(), refused);
        }
        return new RecoveryLeaseLifecycle(authority);
    }

    /** The one authority this process presents on the P5 root. */
    public RecoveryFenceAuthority authority() {
        return authority;
    }

    @Override public void close() throws IOException {
        authority.retire();
    }
}
