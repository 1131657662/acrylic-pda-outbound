package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.pda.PdaSessionLocks;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CommandLocks {
    private final IdentityWriteGuard guard;private final WarehouseBaseline warehouses;private final DeviceStore devices;
    private final PdaSessionLocks sessions;private final JdbcTemplate jdbc;
    public CommandLocks(IdentityWriteGuard guard,WarehouseBaseline warehouses,DeviceStore devices,PdaSessionLocks sessions,JdbcTemplate jdbc) {
        this.guard=guard;this.warehouses=warehouses;this.devices=devices;this.sessions=sessions;this.jdbc=jdbc;
    }
    public void identity(CommandIdentity.Actor actor,CommandContext hint) {
        guard.authentication(); // E/G ordering without imposing a new-write epoch/mode gate on history.
        guard.users(List.of(actor.id(),hint.id("originalOperatorId")));
        warehouses.read(actor.warehouse(),true);
        var ids=new TreeSet<UUID>(Comparator.comparing(UUID::toString));ids.add(hint.id("creatorDeviceId"));
        if(actor.pda()!=null)ids.add(actor.pda().deviceId());
        for(UUID id:ids) {
            var device=devices.find(id,true);
            if(!device.warehouseId().equals(actor.warehouse()))throw CommandErrors.stale();
        }
        if(actor.pda()!=null)sessions.principal(actor.pda());
    }
    public void source(CommandContext hint) {
        // T = existing authorization, then its existing work-origin binding; no bind/create helper.
        lock("SELECT id FROM temporary_authorization WHERE id=? FOR UPDATE",hint.id("authorizationId"));
        jdbc.queryForList("SELECT authorization_id FROM temporary_work_origin WHERE creator_device_id=? AND client_order_id=? FOR UPDATE",
            Ids.bytes(hint.id("creatorDeviceId")),Ids.bytes(hint.id("clientOrderId")));
        lock("SELECT id FROM submission_scope WHERE id=? FOR UPDATE",hint.id("scopeId"));
        lock("SELECT scope_id FROM temporary_decision_state WHERE scope_id=? FOR UPDATE",hint.id("scopeId"));
        if(hint.id("canonicalOrderId")!=null) {
            lock("SELECT id FROM outbound_order WHERE id=? FOR UPDATE",hint.id("canonicalOrderId"));
            lock("SELECT order_id FROM order_submission_slot WHERE order_id=? FOR UPDATE",hint.id("canonicalOrderId"));
        }
        lock("SELECT id FROM submission WHERE id=? FOR UPDATE",hint.id("submissionId"));
    }
    private void lock(String sql,UUID id) {
        if(jdbc.queryForList(sql,Ids.bytes(id)).size()!=1)throw CommandErrors.stale();
    }
    public void sameHint(CommandContext before,CommandContext after) {
        for(String key:List.of("scopeId","warehouseId","originalOperatorId","creatorDeviceId","clientOrderId","authorizationId","canonicalOrderId"))
            if(!Objects.equals(before.text(key),after.text(key)))throw CommandErrors.stale();
    }
}
