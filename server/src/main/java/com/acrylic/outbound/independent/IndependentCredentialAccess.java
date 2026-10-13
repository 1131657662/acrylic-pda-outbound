package com.acrylic.outbound.independent;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.pda.*;
import org.springframework.stereotype.Component;

@Component
public class IndependentCredentialAccess {
    private final PdaAccess access;private final IdentityWriteGuard guard;private final PdaAuthLocks locks;
    public IndependentCredentialAccess(PdaAccess access,IdentityWriteGuard guard,PdaAuthLocks locks) {
        this.access=access;this.guard=guard;this.locks=locks;
    }
    public PdaSessions.Principal current() { return access.require("pda:independent"); }
    public DeviceStore.Device lock(PdaSessions.Principal actor,String epoch) {
        guard.lock(epoch);var device=locks.principal(actor);
        if(!actor.equals(current())) throw new BusinessException(401,"AUTH_EXPIRED","身份已变化，原件保留");
        access.require("pda:scan");return device;
    }
}
