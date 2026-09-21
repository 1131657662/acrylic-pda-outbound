package com.acrylic.outbound.continuation;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.recall.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class ContinuationApproval {
    private final RecallAccess access;private final Access permissions;private final RecallStore recalls;private final ContinuationStore store;
    private final ContinuationChecks checks;private final ContinuationAuthorization authorization;private final JdbcTemplate jdbc;
    private final IdempotencyRequests requests;private final OfflineSigner signer;private final RecallEvents events;
    public ContinuationApproval(RecallAccess access,Access permissions,RecallStore recalls,ContinuationStore store,ContinuationChecks checks,
        ContinuationAuthorization authorization,JdbcTemplate jdbc,IdempotencyRequests requests,OfflineSigner signer,RecallEvents events) {
        this.access=access;this.permissions=permissions;this.recalls=recalls;this.store=store;this.checks=checks;this.authorization=authorization;
        this.jdbc=jdbc;this.requests=requests;this.signer=signer;this.events=events;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode approve(RecallRequests.Context c,UUID id,JsonNode command) {
        permissions.require("order:recall",c.warehouse());permissions.require("order:resume",c.warehouse());
        var before=store.get(id);var r=recalls.get(before.recall());equal(r.warehouse(),c.warehouse(),"仓库不匹配");equal(r.epoch(),c.epoch(),"恢复代次变化须先对账");
        var origin=authorization.origin(r);UUID actor=access.web(c.warehouse(),c.epoch(),true,List.of(origin.user()));
        permissions.require("order:resume",c.warehouse());
        var principal=authorization.principal(r,origin);recalls.lock(r);var h=store.get(id);
        authorization.recheck(recalls.get(h.recall()),origin);
        var key=new IdempotencyRequests.Key(actor,"order.continuation.approve",c.key());String hash=sha(object().put("continuationId",id.toString()).set("command",command));
        var replay=requests.find(key,hash);if(replay!=null)return receipt(store.get(replay));
        ContinuationChecks.conflict(h.status().equals("REQUESTED"),"CONTINUATION_ALREADY_APPROVED","申请已批准，请查询原票据");
        equal(text(command,"action"),"REOPEN_WORK","续作审批动作不匹配");
        equal(text(command,"retainedManifestSha256"),h.manifest(),"保留集合摘要不匹配");
        ContinuationChecks.conflict(recalls.lockOrder(c.warehouse(),r.order()).rowVersion().equals(text(command,"expectedVersion")),"ORDER_VERSION_CONFLICT","订单已变化，请刷新核验");
        String evidence=text(command,"physicalEvidence").strip();require(!evidence.isBlank() && evidence.length()<=1000,"请填写实物核验位置、结果及凭据");
        var validated=checks.validate(r,h.command());checks.stock(h.order(),validated.body(),store.dispositions(h));
        var assignment=authorization.issue(h,principal);
        var receipt=object().put("continuationId",id.toString()).put("status","APPROVED").put("previousSubmissionId",h.previous().toString())
            .put("assignmentId",assignment.id()).put("assignmentEpoch",assignment.epoch()).put("revision",h.revision()).put("workRound",Long.toString(h.toRound()))
            .put("nextOrderScanSeq",Long.toString(h.nextSeq())).put("retainedManifestSha256",h.manifest()).put("recoveryPackageId",h.recovery().toString())
            .put("issuedRecoveryEpoch",c.epoch()).put("expiresAt",assignment.expiresAt()).put("mayResumeScanning",false).put("stockReleased",false);
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-continuation");claims.set("receipt",receipt.deepCopy());receipt.put("signedReceipt",signer.sign(claims).token());
        save(h,assignment.id(),actor,evidence,receipt);events.transition(r,actor,"ORDER_CONTINUATION_APPROVED","CONTINUING");requests.record(key,hash,id);return receipt;
    }
    private void save(ContinuationStore.Head h,String assignment,UUID actor,String evidence,JsonNode receipt) {
        jdbc.update("UPDATE order_continuation SET status='APPROVED',new_assignment_id=?,approved_by=?,physical_evidence=?,receipt_text=?,approved_at=UTC_TIMESTAMP(3) WHERE id=?",
            Ids.bytes(UUID.fromString(assignment)),Ids.bytes(actor),evidence,canonical(receipt),Ids.bytes(h.id()));
        jdbc.update("UPDATE submission SET superseded_by=? WHERE id=?",Ids.bytes(h.id()),Ids.bytes(h.previous()));
        jdbc.update("UPDATE submission_scope SET work_round=?,current_submission_id=NULL WHERE id=?",h.toRound(),Ids.bytes(h.scope()));
        jdbc.update("UPDATE order_submission_slot SET work_round=?,current_submission_id=NULL,phase='CONTINUING' WHERE order_id=?",h.toRound(),Ids.bytes(h.order()));
        jdbc.update("UPDATE bale_commitment SET state='CONTINUATION_RESERVED',submission_id=NULL,recovery_package_id=NULL,continuation_id=? WHERE order_id=? AND state!='QUARANTINED'",Ids.bytes(h.id()),Ids.bytes(h.order()));
    }
    private JsonNode receipt(ContinuationStore.Head h) { return parse(h.receipt().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}
