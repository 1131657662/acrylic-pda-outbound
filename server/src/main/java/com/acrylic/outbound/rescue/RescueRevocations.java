package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Appends a signed revocation even when the original device is disabled. */
@Service
public class RescueRevocations {
    private final RescueAccess access;private final RescueStore store;private final RescueTokens tokens;
    private final RescueJournal journal;private final RescueEnabled enabled;
    public RescueRevocations(RescueAccess access,RescueStore store,RescueTokens tokens,RescueJournal journal,RescueEnabled enabled) {
        this.access=access;this.store=store;this.tokens=tokens;this.journal=journal;this.enabled=enabled;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RescueCommands.RevocationReceipt revoke(RescueAccess.Context context,UUID id,RescueCommands.Revoke input) {
        enabled.require();RescueApprovals.requireReason(input==null?null:input.reason());
        var grant=store.grantDetails(id);var request=store.request(grant.requestId(),false);
        var actor=access.authorize(request,context,false);request=store.request(request.id(),true);
        var command=new RescueJournal.Command(new IdempotencyRequests.Key(actor,"rescue.revoke",context.key()),
            Map.of("grantId",id,"command",input));
        var replay=journal.replay(command);
        if(replay!=null) return store.revocation(replay);
        if(store.revocation(id)!=null) throw new BusinessException(409,"RESCUE_ALREADY_REVOKED","原授权已撤销，请读取原撤销结果");
        var now=Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var claims=new RescueTokens.Grant(id,request,actor,now,grant.expiresAt());
        String token=tokens.revoke(claims,DeviceCrypto.sha(grant.token()));
        store.revoke(id,actor,now,token,input.reason());journal.record(command,id,context.requestId());
        return store.revocation(id);
    }
}
