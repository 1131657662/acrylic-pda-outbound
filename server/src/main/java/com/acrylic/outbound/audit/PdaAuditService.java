package com.acrylic.outbound.audit;

import com.acrylic.outbound.common.IdentityWriteGuard;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.recall.RecoveryAudit;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class PdaAuditService {
    private final PdaAccess access;private final PdaAuthLocks locks;private final IdentityWriteGuard guard;
    private final PdaAuditStore store;private final RecoveryAudit recoveries;
    public PdaAuditService(PdaAccess access,PdaAuthLocks locks,IdentityWriteGuard guard,PdaAuditStore store,RecoveryAudit recoveries) {
        this.access=access;this.locks=locks;this.guard=guard;this.store=store;this.recoveries=recoveries;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Result upload(String epoch,JsonNode body) {
        var actor=access.require("pda:submit");guard.lock(epoch);
        require(body.isObject() && body.size()==2,"审计批次字段无效");
        equal(uuid(body,"originDeviceId"),actor.deviceId(),"审计来源设备不匹配");
        var events=array(body,"events");require(events.size()<=200,"每批审计最多200条");
        require(canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=512*1024,"审计批次过大");
        var ids=new HashSet<UUID>();var sequences=new HashSet<Long>();
        for(var row:events) { PdaAuditFacts.validate(row);require(ids.add(uuid(row,"id")) && sequences.add(number(row,"localSeq")),"批次内审计ID或序号重复"); }
        guard.users(store.users(actor,events));locks.principal(actor);actor=access.require("pda:submit");
        recoveries.verifyExisting(actor.deviceId(),events);
        for(var row:events) store.accept(actor,row);
        return new Result(epoch,actor.deviceId().toString(),Long.toString(store.advance(epoch,actor.deviceId())),sha(body));
    }
    public record Result(String recoveryEpoch,String originDeviceId,String acknowledgedThrough,String batchSha256) { }
}
