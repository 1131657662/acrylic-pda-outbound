package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class RecallFaultWithdrawals {
    private final RecallAccess access;
    private final RecallStore store;
    private final RecallFaultState state;
    private final IdempotencyRequests requests;
    private final boolean enabled;

    public RecallFaultWithdrawals(RecallAccess access,RecallStore store,RecallFaultState state,IdempotencyRequests requests,
        @Value("${outbound.recall.fault-withdrawal-enabled:false}") boolean enabled) {
        this.access=access;this.store=store;this.state=state;this.requests=requests;this.enabled=enabled;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode withdraw(RecallRequests.Context c,UUID order,JsonNode command) {
        if(!enabled)throw new BusinessException(409,"FAULT_WITHDRAWAL_DISABLED","故障订单撤回尚未开放，原记录保留");
        UUID actor=access.web(c.warehouse(),c.epoch(),true);
        state.ensureScope(c.warehouse(),order);
        var head=store.lockOrder(c.warehouse(),order);
        var key=new IdempotencyRequests.Key(actor,"order.fault-withdrawal",c.key());
        String hash=sha(object().put("orderId",order.toString()).set("command",command));
        var replay=requests.find(key,hash);
        if(replay!=null)return parse(store.get(replay).resolution().getBytes(StandardCharsets.UTF_8));
        equal(text(command,"type"),"FAULT_WITHDRAWAL","故障撤回类型不匹配");
        String reason=text(command,"reason").strip();
        require(!reason.isBlank() && reason.length()<=500,"请填写1至500字故障撤回原因");
        if(!head.rowVersion().equals(text(command,"expectedVersion")))
            throw new BusinessException(409,"ORDER_VERSION_CONFLICT","订单已变化，请刷新核对");
        var assignment=state.requireUnfinished(head);
        UUID requestId=state.requestId(assignment);
        var receipt=state.withdraw(new RecallFaultState.Work(requestId,c,head,assignment,actor,reason),command);
        requests.record(key,hash,uuid(receipt,"changeRequestId"));
        return receipt;
    }
}
