package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.IdentityWriteGuard;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MutationAccess {
    private final Access access;
    private final IdentityWriteGuard guard;
    private final WarehouseBaseline warehouse;
    public MutationAccess(Access access, IdentityWriteGuard guard, WarehouseBaseline warehouse) {
        this.access = access; this.guard = guard; this.warehouse = warehouse;
    }
    public Locked lock(Context context, String permission) {
        return lock(context,permission,List.of());
    }
    public Locked lock(Context context, String permission, Collection<UUID> users) {
        var before=access.require(permission, context.warehouse());
        guard.lock(context.epoch());
        var members=new ArrayList<>(users);members.add(before.id());guard.users(members);
        var actor = access.require(permission, context.warehouse());
        return new Locked(actor.id(), warehouse.read(context.warehouse(), true));
    }
    public void requireMapping(UUID warehouseId) { access.require("mapping:maintain", warehouseId); }
    public record Context(UUID warehouse, String epoch, String key, String requestId) { }
    public record Locked(UUID actor, WarehouseBaseline.Baseline baseline) { }
}
