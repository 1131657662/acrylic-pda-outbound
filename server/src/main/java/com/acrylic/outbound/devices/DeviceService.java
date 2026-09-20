package com.acrylic.outbound.devices;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DeviceService {
    private final MutationAccess access;private final DeviceStore store;private final DeviceReceipts receipts;private final Access permissions;
    public DeviceService(MutationAccess access,DeviceStore store,DeviceReceipts receipts,Access permissions) {
        this.access=access;this.store=store;this.receipts=receipts;this.permissions=permissions;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DeviceCommands.Receipt register(MutationAccess.Context context,DeviceCommands.Register input) {
        var locked=access.lock(context,"device:manage");
        var ticket=receipts.prepare(context,locked.actor(),new DeviceReceipts.Payload("register",null,input));
        if(ticket.replay()!=null) return ticket.replay();
        DeviceCrypto.publicKey(input.publicKey());UUID id=Ids.next();
        store.insert(new DeviceStore.NewDevice(id,context.warehouse(),locked.actor()),input);
        return receipts.save(ticket,context,new DeviceReceipts.Result(id,"1","ACTIVE",false,"DEVICE_REGISTERED","登记设备"));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DeviceCommands.Receipt status(MutationAccess.Context context,UUID id,DeviceCommands.Status input) {
        var locked=access.lock(context,"device:manage");var device=store.find(id,true);
        if(!device.warehouseId().equals(context.warehouse())) throw new BusinessException(404,"DEVICE_NOT_FOUND","设备不存在或无权访问");
        var ticket=receipts.prepare(context,locked.actor(),new DeviceReceipts.Payload("status",id,input));
        if(ticket.replay()!=null) return ticket.replay();
        if(!device.rowVersion().equals(input.expectedVersion())) throw new BusinessException(409,"DEVICE_VERSION_CONFLICT","设备状态已变化，请刷新后核对");
        store.status(device,input.status());
        return receipts.save(ticket,context,new DeviceReceipts.Result(id,Long.toString(Math.addExact(Long.parseLong(device.rowVersion()),1)),
            input.status(),device.temporaryOrderEnabled(),"DEVICE_STATUS_CHANGED",input.reason().strip()));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DeviceCommands.Receipt temporaryOrder(MutationAccess.Context context,UUID id,DeviceCommands.TemporaryOrder input) {
        var locked=access.lock(context,"device:manage");
        permissions.require("temporary_order:manage",context.warehouse());var device=store.find(id,true);
        if(!device.warehouseId().equals(context.warehouse())) throw new BusinessException(404,"DEVICE_NOT_FOUND","设备不存在或无权访问");
        var ticket=receipts.prepare(context,locked.actor(),new DeviceReceipts.Payload("temporary-order",id,input));
        if(ticket.replay()!=null) return ticket.replay();
        if(!device.rowVersion().equals(input.expectedVersion())) throw new BusinessException(409,"DEVICE_VERSION_CONFLICT","设备状态已变化，请刷新后核对");
        if(input.enabled() && !device.status().equals("ACTIVE")) throw new BusinessException(409,"DEVICE_DISABLED","停用设备不能开启临时作业准入");
        store.temporaryOrder(device,input.enabled());
        return receipts.save(ticket,context,new DeviceReceipts.Result(id,Long.toString(Math.addExact(Long.parseLong(device.rowVersion()),1)),
            device.status(),input.enabled(),"DEVICE_TEMPORARY_ORDER_CHANGED",input.reason().strip()));
    }
}
