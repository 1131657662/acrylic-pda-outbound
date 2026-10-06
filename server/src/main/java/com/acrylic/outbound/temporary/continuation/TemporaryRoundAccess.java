package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.submissions.SubmissionAccess;
import com.acrylic.outbound.temporary.TemporaryStore;
import org.springframework.stereotype.Component;

/** Same identity/recovery prefix as ordinary PDA intake; continuation identity is also fixed by the ticket. */
@Component
public class TemporaryRoundAccess {
    private final PdaAccess pda;private final PdaAuthLocks locks;private final IdentityWriteGuard guard;private final TemporaryStore authorizations;
    public TemporaryRoundAccess(PdaAccess pda,PdaAuthLocks locks,IdentityWriteGuard guard,TemporaryStore authorizations) {
        this.pda=pda;this.locks=locks;this.guard=guard;this.authorizations=authorizations;
    }
    public PdaSessions.Principal verify(TemporaryRoundControl request,String epoch,boolean write) {
        var actor=pda.require("pda:submit");
        if(write) { guard.lock(epoch);locks.principal(actor);actor=pda.require("pda:submit"); }
        var c=request.control();var a=authorizations.get(c.temporary().authorization());
        if(!c.device().equals(actor.deviceId()) || !a.deviceId().equals(actor.deviceId())
            || !a.userId().equals(actor.userId()) || !a.warehouseId().equals(actor.warehouseId()))throw SubmissionAccess.missing();
        if(!a.recoveryEpoch().equals(c.epoch()) || write && !epoch.equals(c.epoch()))
            throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","续作恢复代次不符，保留原文核对");
        return actor;
    }
}
