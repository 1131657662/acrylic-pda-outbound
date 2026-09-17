package com.acrylic.outbound.pairing;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PairingAdministration {
    private final Access access;private final MutationAccess mutation;private final PairingStore store;
    private final IdempotencyRequests requests;private final PairingAuthority authority;
    public PairingAdministration(Access access,MutationAccess mutation,PairingStore store,IdempotencyRequests requests,PairingAuthority authority) {
        this.access=access;this.mutation=mutation;this.store=store;this.requests=requests;this.authority=authority;
    }
    public PairingCommands.View lookup(UUID warehouse,String code) {
        access.require("device:manage",warehouse);
        if(!code.matches("[0-9]{8}")) throw new BusinessException(400,"INVALID_REQUEST","请输入8位登记码");
        var row=store.byCode(code);requireWarehouse(row,warehouse);return store.view(row);
    }
    public PairingCommands.View current(UUID warehouse,UUID id) {
        access.require("device:manage",warehouse);var row=store.find(id,false);
        if(row==null) throw new BusinessException(404,"PAIRING_NOT_FOUND","登记申请不存在");
        requireWarehouse(row,warehouse);return store.view(row);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PairingCommands.ApprovalReceipt approve(MutationAccess.Context context,UUID id,PairingCommands.ApprovalRequest input) {
        var locked=mutation.lock(context,"device:manage");var row=store.find(id,true);
        if(row==null) throw new BusinessException(404,"PAIRING_NOT_FOUND","登记申请不存在");
        requireWarehouse(row,context.warehouse());
        var key=new IdempotencyRequests.Key(locked.actor(),"pairing.approve",context.key());
        var approval=new PairingCommands.Approval(id,context.warehouse(),authority.warehouseName(context.warehouse()),
            input.deviceCode(),input.name().strip(),row.keySha(),row.epoch());
        String digest=com.acrylic.outbound.devices.DeviceCrypto.sha(id+"\n"+context.warehouse()+"\n"+input.deviceCode()+"\n"+input.name());
        UUID replay=requests.find(key,digest);
        if(replay!=null) return receipt(store.find(replay,false));
        if(!row.status().equals("WAITING") || !Instant.parse(row.expires()).isAfter(Instant.now()))
            throw new BusinessException(409,"PAIRING_STATE_CONFLICT","申请已确认、取消或过期，请核对原申请状态");
        if(!row.epoch().equals(context.epoch())) throw new BusinessException(409,"SERVER_RECOVERY_EPOCH_MISMATCH","登记申请恢复代次已变化");
        store.approve(row,approval,new PairingStore.Approver(locked.actor(),access.current().authVersion()));
        requests.record(key,digest,id);row=store.find(id,false);authority.audit(row,"PAIRING_ADMIN_APPROVED",context.requestId());return receipt(row);
    }
    private void requireWarehouse(PairingStore.Row row,UUID warehouse) {
        if(row.approval()!=null && !row.approval().warehouseId().equals(warehouse))
            throw new BusinessException(404,"PAIRING_NOT_FOUND","登记申请不存在或无权访问");
    }
    private PairingCommands.ApprovalReceipt receipt(PairingStore.Row row) {
        return new PairingCommands.ApprovalReceipt(row.id(),row.approvalSha(),row.approval());
    }
}
