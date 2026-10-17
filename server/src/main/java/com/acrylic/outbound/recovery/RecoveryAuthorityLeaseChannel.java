package com.acrylic.outbound.recovery;

import java.sql.SQLException;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;

/** Resolves the existing P5 authority after the initialization window, without a startup cycle. */
final class RecoveryAuthorityLeaseChannel implements RecoveryOperationRegistry.LeaseChannel {
    private final ObjectProvider<RecoveryFenceAuthority> authority;

    RecoveryAuthorityLeaseChannel(ObjectProvider<RecoveryFenceAuthority> authority) { this.authority = authority; }

    @Override public UUID publish() throws SQLException {
        var fence = authority.getIfAvailable();
        return fence == null ? null : fence.registerOperation();
    }

    @Override public boolean retire(UUID operation) throws SQLException {
        var fence = authority.getIfAvailable();
        if (fence == null) throw new SQLException("RECOVERY_LEASE_OUTSTANDING");
        return fence.retireOperation(operation);
    }

    @Override public void unknown() {
        var fence = authority.getIfAvailable();
        if (fence != null) fence.fenceUnknown();
    }

    @Override public void fenceLocal() {
        var fence = authority.getIfAvailable();
        if (fence != null) fence.fenceLocal();
    }
}
