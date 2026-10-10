package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Rechecks the online reviewing supervisor and the actual disabled source before business locks. */
@Component
final class HandoverRecoveredReviewAccess {
    private final HandoverCommand.Identity identities;
    private final HandoverWriteAccess access;
    private final JdbcTemplate jdbc;

    HandoverRecoveredReviewAccess(HandoverCommand.Identity identities,HandoverWriteAccess access,JdbcTemplate jdbc) {
        this.identities=identities; this.access=access; this.jdbc=jdbc;
    }

    HandoverCommand.Actor current(HandoverCommand.Input command) {
        return identities.current(command.uuid("warehouseId"));
    }

    void lock(HandoverCommand.Input command,HandoverCommand.Actor before) {
        var actor=access.lockRecovered(command,before);
        if(!actor.mayApprove()) throw HandoverCommand.denied();
        var source=Ids.bytes(command.uuid("sourceDeviceId"));
        var devices=jdbc.queryForList("SELECT status FROM device WHERE id=? AND warehouse_id=? FOR UPDATE",
            source,Ids.bytes(command.uuid("warehouseId")));
        HandoverCommand.corruptUnless(devices.size()==1 && "DISABLED".equals(devices.getFirst().get("status")));
        var sessions=jdbc.queryForList("SELECT revoked_at FROM pda_session WHERE device_id=? FOR UPDATE",source);
        HandoverCommand.corruptUnless(sessions.stream().allMatch(row->row.get("revoked_at")!=null));
    }
}
