package com.acrylic.outbound.pda;

import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PdaAuthLocks {
    private final DeviceStore devices;private final WarehouseBaseline warehouses;
    public PdaAuthLocks(DeviceStore devices,WarehouseBaseline warehouses) { this.devices=devices;this.warehouses=warehouses; }
    public DeviceStore.Device device(UUID id) {
        var before=devices.find(id,false);warehouses.read(before.warehouseId(),true);
        var device=devices.find(id,true);device.requireActive();return device;
    }
}
