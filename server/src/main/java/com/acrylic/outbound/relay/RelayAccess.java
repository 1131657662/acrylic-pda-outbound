package com.acrylic.outbound.relay;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.*;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.TemporaryStore;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RelayAccess {
    private final Access web;private final IdentityWriteGuard guard;private final WarehouseBaseline warehouses;
    private final DeviceStore devices;private final JdbcTemplate jdbc;private final AssignmentStore assignments;private final TemporaryStore temporary;
    public RelayAccess(Access web,IdentityWriteGuard guard,WarehouseBaseline warehouses,DeviceStore devices,JdbcTemplate jdbc,
        AssignmentStore assignments,TemporaryStore temporary) {
        this.web=web;this.guard=guard;this.warehouses=warehouses;this.devices=devices;this.jdbc=jdbc;this.assignments=assignments;this.temporary=temporary;
    }
    public UUID write(UUID warehouse,String epoch,UUID collector) {
        var actor=web.require("rescue:relay",warehouse);guard.lock(epoch);
        guard.users(collector==null?List.of(actor.id()):List.of(actor.id(),collector));warehouses.read(warehouse,true);
        return web.require("rescue:relay",warehouse).id();
    }
    public void read(UUID warehouse) { web.require("rescue:relay",warehouse); }
    public void reviewIndependent(UUID warehouse) {
        web.require("rescue:relay",warehouse);web.require("temporary_order:confirm",warehouse);
    }
    public boolean trusted(RelayArchive.Bundle bundle,boolean lock) {
        try {
            var device=devices.find(bundle.original().device(),lock);
            return device.warehouseId().equals(bundle.original().warehouse()) && device.publicKey().equals(bundle.publicKey());
        } catch(BusinessException error) { if(error.code().equals("DEVICE_NOT_FOUND"))return false;throw error; }
    }
    public SubmissionAccess.Actor original(RelayArchive.Bundle bundle,String epoch) {
        if(!trusted(bundle,true))throw new BusinessException(409,"ORIGINAL_SOURCE_UNVERIFIED","原登记设备钥未核验，不得业务处理");
        var original=bundle.original();devices.find(original.device(),true).requireActive();
        var versions=jdbc.queryForList("""
            SELECT auth_version FROM app_user u WHERE id=? AND status='ACTIVE'
              AND EXISTS(SELECT 1 FROM user_warehouse w WHERE w.user_id=u.id AND w.warehouse_id=?)
              AND EXISTS(SELECT 1 FROM user_role ur INNER JOIN role_permission rp ON rp.role_id=ur.role_id
                INNER JOIN permission p ON p.id=rp.permission_id WHERE ur.user_id=u.id AND p.code='pda:submit')
            """,String.class,Ids.bytes(original.user()),Ids.bytes(original.warehouse()));
        if(versions.size()!=1)throw new BusinessException(403,"ORIGINAL_COLLECTOR_UNAUTHORIZED","原采集人已停用或失去原仓库提交权限，证据保留");
        var control=bundle.control();equal(control.epoch(),epoch,"原冻结恢复代次不符，保留原件对账");
        qualification(control,original);
        return new SubmissionAccess.Actor(original.user(),original.warehouse(),versions.getFirst());
    }
    private void qualification(SubmissionControl c,RelayArchive.Identity original) {
        if(c.temporary()!=null) {
            var row=temporary.get(c.temporary().authorization());
            equal(row.deviceId(),original.device(),"原临时资格设备不符");equal(row.userId(),original.user(),"原临时资格人员不符");
            equal(row.warehouseId(),original.warehouse(),"原临时资格仓库不符");equal(row.recoveryEpoch(),c.epoch(),"原临时资格恢复代次不符");
            temporary.requireInitialSubmission(c.temporary().authorization());return;
        }
        var a=assignments.get(c.assignment());
        equal(a.deviceId(),original.device().toString(),"原领取设备不符");equal(a.operatorId(),original.user().toString(),"原领取人员不符");
        equal(a.orderId(),c.order().toString(),"原领取订单不符");equal(a.serverRecoveryEpoch(),c.epoch(),"原领取恢复代次不符");
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE id=? AND warehouse_id=?",Integer.class,
            Ids.bytes(c.assignment()),Ids.bytes(original.warehouse()));equal(count,1,"原领取仓库不符");
        int withdrawn=jdbc.queryForObject("SELECT COUNT(*) FROM order_change_request WHERE assignment_id=? AND status='WITHDRAWN'",Integer.class,Ids.bytes(c.assignment()));
        if(withdrawn!=0)throw new BusinessException(409,"ASSIGNMENT_WITHDRAWN","原领取已被后台撤回，保留原件，不得再处理旧作业");
    }
}
