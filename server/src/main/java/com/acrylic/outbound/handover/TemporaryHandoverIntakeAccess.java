package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.submissions.SubmissionAccess;
import com.acrylic.outbound.temporary.TemporaryStore;
import org.springframework.stereotype.Component;

@Component
final class TemporaryHandoverIntakeAccess {
    private final PdaAccess pda;private final PdaAuthLocks locks;private final IdentityWriteGuard guard;
    private final TemporaryStore authorizations;
    TemporaryHandoverIntakeAccess(PdaAccess pda,PdaAuthLocks locks,IdentityWriteGuard guard,TemporaryStore authorizations) {
        this.pda=pda;this.locks=locks;this.guard=guard;this.authorizations=authorizations;
    }
    PdaSessions.Principal require(TemporaryHandoverControl request,String epoch) {
        var actor=pda.require("pda:submit");guard.lock(epoch);locks.principal(actor);actor=pda.require("pda:submit");
        var control=request.control();var a=authorizations.get(control.temporary().authorization());
        if(!control.device().equals(actor.deviceId()) || !a.deviceId().equals(actor.deviceId())
            || !a.userId().equals(actor.userId()) || !a.warehouseId().equals(actor.warehouseId()))throw SubmissionAccess.missing();
        if(!epoch.equals(control.epoch()) || !a.recoveryEpoch().equals(epoch))
            throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","交接恢复代次不符，请保留原件");
        if(!a.userVersion().equals(actor.userAuthVersion()) || !a.deviceVersion().equals(actor.deviceVersion()))
            throw TemporaryHandoverIntakeFacts.changed();
        if(authorizations.ack(a)==null)throw new BusinessException(409,"TEMPORARY_ACTIVATION_UNACKNOWLEDGED","交接新资格尚未确认资料");
        return actor;
    }
}
