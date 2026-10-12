package com.acrylic.outbound.rescue;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.pda.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RescueAccess {
    private final Access web;private final PdaAccess pda;private final IdentityWriteGuard guard;
    private final PdaAuthLocks locks;private final DeviceStore devices;
    public RescueAccess(Access web,PdaAccess pda,IdentityWriteGuard guard,PdaAuthLocks locks,DeviceStore devices) {
        this.web=web;this.pda=pda;this.guard=guard;this.locks=locks;this.devices=devices;
    }
    public PdaSessions.Principal pda() { return pda.require("pda:recover"); }
    public DeviceStore.Device device(UUID id) { return devices.find(id,false); }
    public DeviceStore.Device request(PdaSessions.Principal actor,UUID rescuer,String epoch) {
        guard.lock(epoch);guard.users(List.of(actor.userId(),rescuer));
        var device=locks.principal(actor);samePrincipal(actor,pda());return device;
    }
    public UUID authorize(RescueStore.Request request,Context context,boolean activeDevice) {
        var actor=web.require("rescue:authorize",context.warehouse());scope(request,context.warehouse());
        if(actor.id().equals(request.operatorId()))
            throw new BusinessException(403,"INDEPENDENT_RESCUE_APPROVAL_REQUIRED","救援授权须由原操作员以外的有权主管核准");
        guard.lock(context.epoch());guard.users(List.of(actor.id(),request.operatorId(),request.rescuerId()));
        // Warehouse/device locks follow E -> G -> sorted users, as in all identity mutations.
        var device=activeDevice?locks.device(request.deviceId()):devices.find(request.deviceId(),true);
        if(!device.warehouseId().equals(request.warehouseId())) throw missing();
        return web.require("rescue:authorize",context.warehouse()).id();
    }
    public void webRead(RescueStore.Request request,UUID warehouse) {
        web.require("rescue:authorize",warehouse);scope(request,warehouse);
    }
    public void own(RescueStore.Request request) {
        var actor=pda();
        if(!actor.userId().equals(request.operatorId()) || !actor.deviceId().equals(request.deviceId()) ||
            !actor.warehouseId().equals(request.warehouseId())) throw missing();
    }
    public void rescuer(RescueStore.Request request) {
        var actor=pda.require("rescue:read");
        if(!actor.userId().equals(request.rescuerId()) || !actor.deviceId().equals(request.deviceId()) ||
            !actor.warehouseId().equals(request.warehouseId())) throw missing();
        requireRegisteredKey(request);
    }
    public void requireRegisteredKey(RescueStore.Request request) {
        var device=devices.find(request.deviceId(),false);
        if(!RescueRequests.keyDigest(device).equals(request.deviceKeySha256()))
            throw new BusinessException(409,"RESCUE_DEVICE_KEY_CHANGED","登记钥已变化，须保留原件并重新核对救援申请");
    }
    private void samePrincipal(PdaSessions.Principal before,PdaSessions.Principal after) {
        if(!before.equals(after)) throw new BusinessException(401,"AUTH_EXPIRED","身份变化，请重新认证；原件保留");
    }
    private void scope(RescueStore.Request request,UUID warehouse) {
        if(!request.warehouseId().equals(warehouse)) throw missing();
    }
    private BusinessException missing() { return new BusinessException(404,"RESCUE_REQUEST_NOT_FOUND","救援申请不存在或无权访问"); }
    public record Context(UUID warehouse,String epoch,String key,String requestId) { }
}
