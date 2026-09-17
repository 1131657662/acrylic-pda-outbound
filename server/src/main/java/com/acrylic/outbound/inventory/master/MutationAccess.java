package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.ServerState;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MutationAccess {
    private final Access access;
    private final ServerState state;
    private final WarehouseBaseline warehouse;
    public MutationAccess(Access access, ServerState state, WarehouseBaseline warehouse) {
        this.access = access; this.state = state; this.warehouse = warehouse;
    }
    public Locked lock(Context context, String permission) {
        var actor = access.require(permission, context.warehouse());
        state.lockWritable(context.epoch());
        return new Locked(actor.id(), warehouse.read(context.warehouse(), true));
    }
    public void requireMapping(UUID warehouseId) { access.require("mapping:maintain", warehouseId); }
    public record Context(UUID warehouse, String epoch, String key, String requestId) { }
    public record Locked(UUID actor, WarehouseBaseline.Baseline baseline) { }
}
