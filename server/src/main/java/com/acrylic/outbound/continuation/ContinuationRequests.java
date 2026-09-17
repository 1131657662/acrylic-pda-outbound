package com.acrylic.outbound.continuation;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.recall.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class ContinuationRequests {
    private final RecallAccess access;private final Access permissions;private final RecallStore recalls;private final ContinuationStore store;
    private final ContinuationChecks checks;private final JdbcTemplate jdbc;private final IdempotencyRequests requests;
    public ContinuationRequests(RecallAccess access,Access permissions,RecallStore recalls,ContinuationStore store,ContinuationChecks checks,JdbcTemplate jdbc,IdempotencyRequests requests) {
        this.access=access;this.permissions=permissions;this.recalls=recalls;this.store=store;this.checks=checks;this.jdbc=jdbc;this.requests=requests;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode create(RecallRequests.Context c,UUID order,JsonNode command) {
        UUID actor=access.web(c.warehouse(),c.epoch(),true);permissions.require("order:resume",c.warehouse());
        var head=recalls.lockOrder(c.warehouse(),order);UUID recall=recalls.forOrder(order,c.warehouse());require(recall!=null,"缺少原机恢复证据");
        var r=recalls.lock(recalls.get(recall));equal(r.epoch(),c.epoch(),"旧恢复代次须先对账");
        var key=new IdempotencyRequests.Key(actor,"order.continuation.request",c.key());String hash=sha(object().put("orderId",order.toString()).set("command",command));
        var replay=requests.find(key,hash);if(replay!=null)return store.view(store.get(replay));
        if(store.forRecall(recall)!=null)throw new BusinessException(409,"CONTINUATION_ALREADY_EXISTS","已有续作申请，请查询原票据");
        String reason=text(command,"reason").strip();require(!reason.isBlank() && reason.length()<=500,"请填写续作原因");
        var v=checks.validate(r,command);UUID id=Ids.next();
        String scope=jdbc.queryForObject("SELECT BIN_TO_UUID(scope_id) FROM submission WHERE id=?",String.class,Ids.bytes(v.previous().control().id()));
        jdbc.update("""
            INSERT INTO order_continuation(id,request_id,scope_id,order_id,previous_submission_id,from_round,to_round,revision,
            assigned_device_id,recovery_package_id,retained_manifest_sha256,next_order_scan_seq,status,command_text,requested_by,created_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,UNHEX(?),?,'REQUESTED',?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(recall),Ids.bytes(UUID.fromString(scope)),Ids.bytes(order),Ids.bytes(v.previous().control().id()),
            v.previous().control().round(),Math.addExact(v.previous().control().round(),1),head.revision(),Ids.bytes(uuid(command,"requestedDeviceId")),
            Ids.bytes(uuid(command,"recoveryPackageId")),sha(v.retained()),v.nextSeq(),canonical(command),Ids.bytes(actor));
        for(var row:v.retained())jdbc.update("INSERT INTO continuation_bale(continuation_id,bale_id,origin_scan_id,expected_cycle,record_sha256) VALUES(?,?,?,?,UNHEX(?))",Ids.bytes(id),Ids.bytes(uuid(row,"baleId")),
            Ids.bytes(uuid(row,"captureId")),number(row,"expectedDispatchCycle"),sha(row));
        requests.record(key,hash,id);return store.view(store.get(id));
    }
}
