package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.IdentityWriteGuard;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.pda.PdaAuthLocks;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Uses the existing identity/warehouse prefix before any source, case or package lock. */
@Component
final class HandoverWriteAccess {
    private final IdentityWriteGuard guard;
    private final WarehouseBaseline warehouses;
    private final PdaAuthLocks pda;
    private final HandoverCommand.Identity identities;

    HandoverWriteAccess(IdentityWriteGuard guard, WarehouseBaseline warehouses, PdaAuthLocks pda,
            HandoverCommand.Identity identities) {
        this.guard = guard; this.warehouses = warehouses; this.pda = pda; this.identities = identities;
    }

    HandoverCommand.Actor lock(HandoverCommand.Input input, HandoverCommand.Actor before) {
        guard.lock(input.text("transportRecoveryEpoch"));
        var users = new ArrayList<UUID>(); users.add(before.id());
        if (input.kind().equals("PREPARE") || input.kind().equals("ACTIVATE")) users.add(input.uuid("targetUserId"));
        guard.users(users);
        warehouses.read(before.warehouse(), true);
        if (before.pda() != null) pda.principal(before.pda());
        return identities.current(before.warehouse());
    }
}
