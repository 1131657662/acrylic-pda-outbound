package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.IdentityWriteGuard;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.temporary.TemporaryStore;
import org.springframework.stereotype.Component;

/** Authenticates the original TEMP operator/device without borrowing a FORMAL assignment. */
@Component
final class HandoverTemporaryStopAccess {
    private final PdaAccess pda;
    private final PdaAuthLocks locks;
    private final IdentityWriteGuard guard;
    private final TemporaryStore authorizations;

    HandoverTemporaryStopAccess(PdaAccess pda, PdaAuthLocks locks, IdentityWriteGuard guard, TemporaryStore authorizations) {
        this.pda = pda; this.locks = locks; this.guard = guard; this.authorizations = authorizations;
    }

    PdaSessions.Principal require(HandoverStopStore.Origin origin, String epoch, boolean write) {
        var actor = pda.require("pda:recover");
        if (write) { guard.lock(epoch); locks.principal(actor); actor = pda.require("pda:recover"); }
        var authorization = authorizations.get(origin.temporary().authorization());
        if (!authorization.deviceId().equals(origin.device()) || !authorization.userId().equals(origin.operator())
                || !authorization.warehouseId().equals(origin.warehouse())) throw HandoverCommand.missing();
        if (!authorization.recoveryEpoch().equals(origin.epoch()) || write && !origin.epoch().equals(epoch))
            throw HandoverCommand.stale();
        return actor;
    }
}
