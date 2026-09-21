package com.acrylic.outbound.submissions;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.pda.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SubmissionAccess {
    private final PdaAccess pda;private final PdaAuthLocks locks;private final AssignmentStore assignments;
    private final Access web;private final IdentityWriteGuard guard;private final WarehouseBaseline warehouses;
    private final com.acrylic.outbound.temporary.TemporarySubmissionAccess temporary;
    public SubmissionAccess(PdaAccess pda,PdaAuthLocks locks,AssignmentStore assignments,Access web,IdentityWriteGuard guard,WarehouseBaseline warehouses,com.acrylic.outbound.temporary.TemporarySubmissionAccess temporary) {
        this.pda=pda;this.locks=locks;this.assignments=assignments;this.web=web;this.guard=guard;this.warehouses=warehouses;
        this.temporary=temporary;
    }
    public Actor pda(SubmissionControl control,String epoch,boolean write) {
        var actor=pda.require("pda:submit");
        if(write) { guard.lock(epoch);locks.principal(actor);actor=pda.require("pda:submit"); }
        if(control.temporary()!=null)return temporary.verify(control,actor,epoch,write);
        AssignmentStore.View assignment;
        try { assignment=assignments.get(control.assignment()); }
        catch(org.springframework.dao.EmptyResultDataAccessException error) { throw missing(); }
        if(!control.device().equals(actor.deviceId()) || !assignment.deviceId().equals(actor.deviceId().toString()) ||
            !assignment.operatorId().equals(actor.userId().toString()) || !assignment.orderId().equals(control.order().toString())) throw missing();
        if(!assignment.serverRecoveryEpoch().equals(control.epoch()) || (write && !epoch.equals(control.epoch()))) throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","原资料恢复代次不符，保留原批次并对账");
        return new Actor(actor.userId(),actor.warehouseId(),actor.userAuthVersion());
    }
    public Actor web(UUID warehouse,String epoch,boolean write) {
        var actor=web.require(write?"submission:review":"order:read",warehouse);
        if(write) { guard.lock(epoch);guard.users(java.util.List.of(actor.id()));warehouses.read(warehouse,true);actor=web.require("submission:review",warehouse); }
        return new Actor(actor.id(),warehouse,Long.toString(actor.authVersion()));
    }
    public static BusinessException missing() { return new BusinessException(404,"SUBMISSION_NOT_FOUND","批次不存在或不属于当前身份"); }
    public record Actor(UUID user,UUID warehouse,String permissionVersion) { }
}
