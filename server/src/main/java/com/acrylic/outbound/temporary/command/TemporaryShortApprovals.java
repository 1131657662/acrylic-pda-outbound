package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Approval identity and context are retained separately; an approval never performs accounting. */
@Component
public class TemporaryShortApprovals {
    private final JdbcTemplate jdbc; private final CommandStore store; private final CommandReceiptIntegrity integrity;
    public TemporaryShortApprovals(JdbcTemplate jdbc,CommandStore store,CommandReceiptIntegrity integrity) {
        this.jdbc=jdbc;this.store=store;this.integrity=integrity;
    }
    public void bind(CommandWrite write,ObjectNode decision) {
        var receipts=jdbc.queryForList("""
            SELECT BIN_TO_UUID(q.principal_id) principal_id,q.idempotency_key FROM temporary_command_receipt r
            JOIN temporary_decision_event e ON e.receipt_id=r.id JOIN api_idempotency_request q ON q.id=r.api_request_id
            WHERE r.scope_id=? AND r.submission_id=? AND e.decision_kind='SHORT_APPROVE'
            ORDER BY e.resulting_decision_version DESC
            """,Ids.bytes(write.context().id("scopeId")),Ids.bytes(write.context().id("submissionId")));
        if(receipts.isEmpty())throw missing();
        var row=receipts.getFirst();var principal=UUID.fromString(row.get("principal_id").toString());
        var record=store.find(principal,row.get("idempotency_key").toString());var approval=integrity.verify(record);
        var previous=approval.path("originalContext");var now=write.context().json();
        if(!approval.path("resultingDecisionVersion").equals(now.path("decisionVersion")))throw missing();
        for(String key:List.of("canonicalOrderId","mappingVersion","currentSubmissionId","scopeRound","slotVersion",
            "targetRevision","receivedPayloadSha256","declaredUploadSha256","planSha256"))
            if(!Objects.equals(previous.get(key),now.get(key)))throw missing();
        if(!approval.path("businessDecision").path("candidates").equals(decision.path("candidates")))throw missing();
        currentActor(principal,approval,write.context().id("warehouseId"));
        decision.put("shortApprovalReceiptId",record.id("id").toString()).put("shortApprovalSha256",record.text("original_response_sha256"));
    }
    private void currentActor(UUID principal,JsonNode approval,UUID warehouse) {
        int valid=jdbc.queryForObject("""
            SELECT COUNT(*) FROM app_user u WHERE u.id=? AND u.status='ACTIVE' AND u.auth_version=?
            AND EXISTS(SELECT 1 FROM user_warehouse w WHERE w.user_id=u.id AND w.warehouse_id=?)
            AND EXISTS(SELECT 1 FROM user_role r JOIN role_permission p ON p.role_id=r.role_id
              JOIN permission x ON x.id=p.permission_id WHERE r.user_id=u.id AND x.code='submission:review')
            """,Integer.class,Ids.bytes(principal),approval.path("actor").path("userAuthVersion").asText(),Ids.bytes(warehouse));
        if(valid!=1)throw missing();
    }
    private BusinessException missing() { return new BusinessException(409,"SHORT_SHIPMENT_APPROVAL_REQUIRED","缺少当前来源、目标及候选版本的独立短装批准"); }
}
