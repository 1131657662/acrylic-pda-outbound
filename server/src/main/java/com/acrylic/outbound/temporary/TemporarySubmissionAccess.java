package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.*;
import org.springframework.stereotype.Component;

@Component
public class TemporarySubmissionAccess {
    private final TemporaryStore store;
    public TemporarySubmissionAccess(TemporaryStore store) { this.store=store; }
    public SubmissionAccess.Actor verify(SubmissionControl c,PdaSessions.Principal actor,String epoch,boolean write) {
        var row=store.get(c.temporary().authorization());
        if(!c.device().equals(actor.deviceId()) || !row.deviceId().equals(actor.deviceId()) || !row.userId().equals(actor.userId()) ||
            !row.warehouseId().equals(actor.warehouseId()))throw SubmissionAccess.missing();
        if(!row.recoveryEpoch().equals(c.epoch()) || write && !epoch.equals(c.epoch()))
            throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","原临时资料恢复代次不符，保留原批次对账");
        // Historical evidence/abort must remain recoverable after offline expiry or admission changes.
        return new SubmissionAccess.Actor(actor.userId(),actor.warehouseId(),actor.userAuthVersion());
    }
}
