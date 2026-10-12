package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Issues a read capability for the immutable, device-signed request; never changes assignments. */
@Service
public class RescueApprovals {
    private final RescueAccess access;private final RescueStore store;private final RescueTokens tokens;
    private final RescueJournal journal;private final RescueEnabled enabled;
    public RescueApprovals(RescueAccess access,RescueStore store,RescueTokens tokens,RescueJournal journal,RescueEnabled enabled) {
        this.access=access;this.store=store;this.tokens=tokens;this.journal=journal;this.enabled=enabled;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RescueCommands.GrantReceipt approve(RescueAccess.Context context,UUID id,RescueCommands.Approve input) {
        enabled.require();requireReason(input==null?null:input.reason());
        var request=store.request(id,false);var actor=access.authorize(request,context,true);
        request=store.request(id,true);
        var command=new RescueJournal.Command(new IdempotencyRequests.Key(actor,"rescue.approve",context.key()),
            Map.of("requestId",id,"command",input));
        var replay=journal.replay(command);
        if(replay!=null) return store.grant(replay);
        if(store.grantForRequest(id)!=null) throw new BusinessException(409,"RESCUE_ALREADY_AUTHORIZED","申请已有授权，请读取原结果；变更须先撤销并另建申请");
        store.requireRescuer(request.rescuerId(),request.warehouseId());access.requireRegisteredKey(request);
        var now=Instant.now().truncatedTo(ChronoUnit.MILLIS);var expires=expiry(input.expiresAt(),now);
        UUID grantId=Ids.next();var claims=new RescueTokens.Grant(grantId,request,actor,now,expires);
        String token=tokens.grant(claims,store.scope(id));
        store.grant(grantId,request,actor,now,expires,token,input.reason());journal.record(command,grantId,context.requestId());
        return store.grant(grantId);
    }
    static void requireReason(String reason) {
        if(reason==null || reason.isBlank() || reason.length()>1000)
            throw new BusinessException(400,"RESCUE_REASON_REQUIRED","救援审批或撤销须填写非空且不超过1000字的理由");
    }
    static Instant expiry(Instant requested,Instant now) {
        if(requested==null) return null;
        var value=requested.truncatedTo(ChronoUnit.MILLIS);
        if(!value.isAfter(now) || value.isAfter(Instant.parse("9999-12-31T23:59:59Z")))
            throw new BusinessException(400,"RESCUE_EXPIRY_INVALID","救援到期时间须晚于服务端签发时间并在可存储范围内");
        return value;
    }
}
