package com.acrylic.outbound.pairing;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.*;
import java.security.SecureRandom;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PairingRegistration {
    private final PairingChallenges challenges;private final PairingStore store;private final ServerState state;
    private final PairingAuthority authority;private final DeviceStore devices;
    private static final SecureRandom RANDOM=new SecureRandom();
    public PairingRegistration(PairingChallenges challenges,PairingStore store,ServerState state,PairingAuthority authority,DeviceStore devices) {
        this.challenges=challenges;this.store=store;this.state=state;this.authority=authority;this.devices=devices;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PairingCommands.View create(PairingCommands.Command command,String requestId) {
        challenges.consume("CREATE",command);var p=command.payload();store.lockKey(DeviceCrypto.sha(p.publicKey()));
        var existing=store.find(p.applicationId(),true);
        if(existing!=null) return store.view(authenticate(existing,p));
        if(!p.approvalSha256().isEmpty()) throw new BusinessException(400,"INVALID_REQUEST","新申请不能包含确认凭据");
        store.requireNewKey(DeviceCrypto.sha(p.publicKey()));insert(p);
        var row=store.find(p.applicationId(),false);authority.audit(row,"PAIRING_REQUESTED",requestId);return store.view(row);
    }
    private void insert(PairingCommands.Payload p) {
        for(int attempt=0;attempt<5;attempt++) {
            String code=String.format(java.util.Locale.ROOT,"%08d",RANDOM.nextInt(100_000_000));
            try { store.insert(p,code,state.current().epoch());return; }
            catch(DuplicateKeyException collision) { if(attempt==4) throw collision; }
        }
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PairingCommands.View operate(String action,PairingCommands.Command command,String requestId) {
        challenges.consume(action,command);var p=command.payload();var before=authenticate(store.find(p.applicationId(),false),p);
        authority.lockWarehouse(before);
        var row=authenticate(store.find(p.applicationId(),true),p);row=store.expire(row);
        if(action.equals("CANCEL") && (row.status().equals("WAITING") || row.status().equals("PENDING"))) {
            store.cancel(row.id());authority.audit(store.find(row.id(),false),"PAIRING_CANCELLED",requestId);
        }
        if(action.equals("CONFIRM")) confirm(row,p,requestId);
        return store.view(store.find(row.id(),false));
    }
    private PairingStore.Row authenticate(PairingStore.Row row,PairingCommands.Payload p) {
        if(row==null || !row.publicKey().equals(p.publicKey()) || !row.credentialSha().equals(DeviceCrypto.sha(p.credential())))
            throw new BusinessException(401,"PAIRING_AUTH_INVALID","无法核对本机登记申请");
        if(!row.requestSha().equals(p.originalHash())) throw new BusinessException(409,"PAIRING_CONTENT_MISMATCH","原登记申请内容已改变");
        state.lockWritable(row.epoch());return row;
    }
    private void confirm(PairingStore.Row row,PairingCommands.Payload p,String requestId) {
        if(row.status().equals("EXPIRED") || row.status().equals("CANCELLED")) return;
        if(row.approvalSha()==null || !row.approvalSha().equals(p.approvalSha256()))
            throw new BusinessException(409,"PAIRING_CONFIRMATION_MISMATCH","请先核对管理员确认的仓库和设备信息");
        if(row.status().equals("COMPLETED")) return;
        authority.requireApprover(row);var approval=row.approval();UUID id=Ids.next();
        devices.insert(new DeviceStore.NewDevice(id,approval.warehouseId(),row.actor()),
            new DeviceCommands.Register(approval.deviceCode(),approval.name(),row.publicKey()));
        store.complete(row.id(),id);authority.audit(store.find(row.id(),false),"PAIRING_COMPLETED",requestId);
    }
}
