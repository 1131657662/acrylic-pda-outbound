package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.IdentityWriteGuard;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.pda.PdaAuthLocks;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Locks identity, users, warehouse and target device before source and case rows. */
@Component
final class HandoverWriteAccess {
    private final IdentityWriteGuard guard;
    private final WarehouseBaseline warehouses;
    private final PdaAuthLocks pda;
    private final HandoverCommand.Identity identities;
    private final JdbcTemplate jdbc;

    HandoverWriteAccess(IdentityWriteGuard guard, WarehouseBaseline warehouses, PdaAuthLocks pda,
            HandoverCommand.Identity identities, JdbcTemplate jdbc) {
        this.guard = guard;
        this.warehouses = warehouses;
        this.pda = pda;
        this.identities = identities;
        this.jdbc = jdbc;
    }

    HandoverCommand.Actor lock(HandoverCommand.Input input, HandoverCommand.Actor before) {
        guard.lock(input.text("transportRecoveryEpoch"));
        return lockPrincipals(input,before);
    }

    /** Invoked only inside the original archive's closed transaction, before its business row locks. */
    HandoverCommand.Actor lockRecovered(HandoverCommand.Input input,HandoverCommand.Actor before) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || !org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new IllegalStateException("Recovered handover requires this database's closed transaction");
        var state=jdbc.queryForMap("SELECT recovery_epoch,service_mode FROM server_state WHERE id=1 FOR SHARE");
        if(!"RECOVERY_PENDING".equals(state.get("service_mode"))
                || !state.get("recovery_epoch").toString().equals(input.text("transportRecoveryEpoch"))) throw HandoverCommand.stale();
        if(before.pda()!=null) throw HandoverCommand.denied();
        guard.authentication();
        return lockPrincipals(input,before);
    }

    private HandoverCommand.Actor lockPrincipals(HandoverCommand.Input input,HandoverCommand.Actor before) {
        var users = new ArrayList<UUID>();
        users.add(before.id());
        if (targetCommand(input)) users.add(input.uuid("targetUserId"));
        guard.users(users);
        warehouses.read(before.warehouse(), true);
        if (before.pda() != null) pda.principal(before.pda());
        if (targetCommand(input) && input.uuid("targetDeviceId") != null)
            jdbc.queryForList("SELECT id FROM device WHERE id=? AND warehouse_id=? FOR UPDATE",
                Ids.bytes(input.uuid("targetDeviceId")), Ids.bytes(before.warehouse()));
        if (input.kind().equals("FAULT_FENCE")) {
            if (before.pda() != null) throw HandoverCommand.denied();
            jdbc.queryForList("SELECT id FROM device WHERE id=? AND warehouse_id=? FOR UPDATE",
                Ids.bytes(input.uuid("sourceDeviceId")), Ids.bytes(before.warehouse()));
            new com.acrylic.outbound.pda.PdaSessionLocks(jdbc).device(input.uuid("sourceDeviceId"));
        }
        return identities.current(before.warehouse());
    }

    private boolean targetCommand(HandoverCommand.Input input) {
        return input.kind().equals("PREPARE") || input.kind().equals("ACTIVATE");
    }
}
