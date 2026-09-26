package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.common.IdentityWriteGuard;
import com.acrylic.outbound.pda.*;
import org.springframework.stereotype.Component;

@Component
public class DraftEvidenceAccess {
    private final PdaAccess access;
    private final IdentityWriteGuard guard;
    private final PdaAuthLocks locks;
    public DraftEvidenceAccess(PdaAccess access,IdentityWriteGuard guard,PdaAuthLocks locks) {
        this.access=access;this.guard=guard;this.locks=locks;
    }
    public PdaSessions.Principal current() { return access.require("pda:recover"); }
    public PdaSessions.Principal lock(String epoch) {
        var before=current(); guard.lock(epoch); locks.principal(before); var after=current();
        DraftEvidenceWire.require(before.equals(after)); return after;
    }
    static void owner(PdaSessions.Principal actor, DraftEvidenceStore.Header header) {
        if(!actor.userId().equals(header.operator()) || !actor.deviceId().equals(header.device()) || !actor.warehouseId().equals(header.warehouse()))
            throw DraftEvidenceWire.missing();
    }
}
