package com.acrylic.outbound.pda;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PdaAccess {
    private final PdaSessions sessions;private final IdentityStore identities;private final IdentityWriteGuard guard;private final PdaAuthLocks locks;
    public PdaAccess(PdaSessions sessions,IdentityStore identities,IdentityWriteGuard guard,PdaAuthLocks locks) {
        this.sessions=sessions;this.identities=identities;this.guard=guard;this.locks=locks;
    }
    public PdaSessions.Principal current() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !(auth.getPrincipal() instanceof PdaSessions.Principal principal)) throw new BusinessException(401,"UNAUTHENTICATED","请从已绑定PDA登录");
        return sessions.recheck(principal);
    }
    public PdaSessions.Principal require(String permission) {
        var principal=current();
        if(!identities.permissions(principal.userId()).contains(permission)) throw new BusinessException(403,"PERMISSION_DENIED","没有执行此PDA操作的权限");
        return principal;
    }
    public PdaSessions.Principal lock(String epoch) {
        var principal=require("pda:assign");guard.lock(epoch);locks.device(principal.deviceId());
        principal=require("pda:assign");
        if(!identities.permissions(principal.userId()).contains("pda:scan")) throw new BusinessException(403,"PERMISSION_DENIED","缺少扫码作业权限");
        return principal;
    }
    public PdaSessions.Principal barcodes(String epoch) {
        current();guard.lock(epoch);var principal=current();
        if(identities.permissions(principal.userId()).contains("pda:assign")) return principal;
        var device=locks.device(principal.deviceId());
        principal=require("temporary_order:create");require("pda:scan");require("pda:submit");
        if(!device.temporaryOrderEnabled()) throw new BusinessException(403,"TEMPORARY_ORDER_DISABLED","此设备未开启临时作业准入");
        return principal;
    }
}
