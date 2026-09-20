package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class TemporaryAccess {
    private final PdaAccess access;private final PdaAuthLocks locks;private final IdentityWriteGuard guard;
    public TemporaryAccess(PdaAccess access,PdaAuthLocks locks,IdentityWriteGuard guard) {
        this.access=access;this.locks=locks;this.guard=guard;
    }
    public PdaSessions.Principal lock(String epoch) {
        var principal=access.require("temporary_order:create");guard.lock(epoch);
        var device=locks.device(principal.deviceId());
        principal=access.require("temporary_order:create");access.require("pda:scan");access.require("pda:submit");
        if(!device.temporaryOrderEnabled()) throw new BusinessException(403,"TEMPORARY_ORDER_DISABLED","此设备未开启临时作业准入");
        return principal;
    }
    public void owned(TemporaryStore.Stored row,PdaSessions.Principal principal) {
        if(!row.deviceId().equals(principal.deviceId()) || !row.userId().equals(principal.userId()) || !row.warehouseId().equals(principal.warehouseId()))
            throw TemporaryStore.missing();
    }
    public void usable(TemporaryStore.Stored row,PdaSessions.Principal principal,String epoch) {
        owned(row,principal);
        if(!row.recoveryEpoch().equals(epoch)) throw new BusinessException(409,"TEMPORARY_RECOVERY_REQUIRED","临时资格来自旧恢复代次，保留资料并对账");
        if(!row.userVersion().equals(principal.userAuthVersion()) || !row.deviceVersion().equals(principal.deviceVersion()))
            throw new BusinessException(409,"TEMPORARY_REAUTHORIZATION_REQUIRED","人员或设备版本已变化，原临时资格不能继续激活");
    }
    public void unexpired(TemporaryStore.Stored row) {
        if(!row.expiresAt().isAfter(Instant.now())) throw new BusinessException(409,"TEMPORARY_AUTH_EXPIRED","临时资格已到期，保留原作业和资料");
    }
}
