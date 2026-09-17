package com.acrylic.outbound.recall;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.pda.*;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RecallAccess {
    private final Access web;private final PdaAccess pda;private final ServerState state;private final WarehouseBaseline warehouses;
    private final PdaAuthLocks locks;private final AssignmentStore assignments;
    public RecallAccess(Access web,PdaAccess pda,ServerState state,WarehouseBaseline warehouses,PdaAuthLocks locks,AssignmentStore assignments) {
        this.web=web;this.pda=pda;this.state=state;this.warehouses=warehouses;this.locks=locks;this.assignments=assignments;
    }
    public UUID web(UUID warehouse,String epoch,boolean write) {
        var actor=web.require(write?"order:recall":"order:read",warehouse);
        if(write) { state.lockWritable(epoch);warehouses.read(warehouse,true);actor=web.require("order:recall",warehouse); }
        return actor.id();
    }
    public void cancel(UUID warehouse) { web.require("order:cancel",warehouse); }
    public PdaSessions.Principal pda(UUID assignment,String epoch,boolean write) {
        var actor=pda.require("pda:recover");
        if(write) { state.lockWritable(epoch);locks.device(actor.deviceId());actor=pda.require("pda:recover"); }
        AssignmentStore.View a;
        try { a=assignments.get(assignment); } catch(org.springframework.dao.EmptyResultDataAccessException e) { throw missing(); }
        if(!a.deviceId().equals(actor.deviceId().toString()) || !a.operatorId().equals(actor.userId().toString())) throw missing();
        if(write && !a.serverRecoveryEpoch().equals(epoch)) throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","旧恢复代次资料须先对账");
        return actor;
    }
    private BusinessException missing() { return new BusinessException(404,"ASSIGNMENT_NOT_FOUND","原授权不存在或不属于当前人员及设备"); }
}
