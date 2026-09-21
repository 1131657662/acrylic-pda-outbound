package com.acrylic.outbound.pda;

import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.common.IdentityWriteGuard;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PdaAuthLocks {
    private final DeviceStore devices;private final WarehouseBaseline warehouses;
    private final IdentityWriteGuard guard;private final PdaSessionLocks sessions;
    public PdaAuthLocks(DeviceStore devices,WarehouseBaseline warehouses,IdentityWriteGuard guard,PdaSessionLocks sessions) {
        this.devices=devices;this.warehouses=warehouses;this.guard=guard;this.sessions=sessions;
    }
    public DeviceStore.Device principal(PdaSessions.Principal principal) {
        guard.users(List.of(principal.userId()));
        var device=device(principal.deviceId());sessions.principal(principal);return device;
    }
    public DeviceStore.Device device(UUID id) {
        var before=devices.find(id,false);warehouses.read(before.warehouseId(),true);
        var device=devices.find(id,true);device.requireActive();return device;
    }
}
