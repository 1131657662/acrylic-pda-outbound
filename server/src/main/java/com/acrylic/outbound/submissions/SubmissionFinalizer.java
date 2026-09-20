package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.AssignmentStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class SubmissionFinalizer {
    private final JdbcTemplate jdbc;private final SubmissionSnapshot snapshots;private final SubmissionStock stock;
    private final SubmissionAccounting accounting;private final SubmissionStore store;private final SubmissionEvents events;private final SubmissionReceipts receipts;
    public SubmissionFinalizer(JdbcTemplate jdbc,SubmissionSnapshot snapshots,SubmissionStock stock,SubmissionAccounting accounting,SubmissionStore store,SubmissionEvents events,SubmissionReceipts receipts) {
        this.jdbc=jdbc;this.snapshots=snapshots;this.stock=stock;this.accounting=accounting;this.store=store;this.events=events;this.receipts=receipts;
    }
    public JsonNode finish(SubmissionStore.Head head,JsonNode body,Approval approval) {
        if(head.control().temporary()!=null)throw new BusinessException(409,"TEMPORARY_CONFIRMATION_REQUIRED","临时单须独立确认，不能使用正式单审批放行");
        store.current(head);var assignment=new AssignmentStore(jdbc).get(head.control().assignment());
        var value=snapshots.validate(assignment,body);var issues=authorization(head,assignment);
        var publication=new AssignmentStore(jdbc).publication(head.control().order(),assignment.revision());
        equal(text(body,"policyVersion"),publication.policyVersion(),"原出库政策版本不符");
        require(body.path("autoDeductInventory").isBoolean() && body.path("autoDeductInventory").booleanValue()==publication.autoDeduct(),"固定扣账政策不符");
        issues.addAll(stock.reserve(head,value));
        if(head.control().continuation()!=null && !issues.isEmpty())throw new BusinessException(409,"CONTINUATION_RECONCILIATION_REQUIRED","续作库存或授权存在冲突，保留占用并先对账");
        if(approval.approved() && !issues.isEmpty()) throw new BusinessException(409,"SUBMISSION_STILL_CONFLICTED","原周期、库存或身份冲突尚未解决，不能批准出库");
        if(value.shortShipment() && !approval.approved()) issues.add(object().put("code","SHORT_SHIPMENT_APPROVAL_REQUIRED").put("reason",text(body,"shortShipmentReason")));
        if(issues.isEmpty()) return accounting.commit(head,body,value,approval.actor());
        store.state(head.control().id(),"REVIEW_REQUIRED",issues,null);events.transition(head,"REVIEW_REQUIRED",approval.actor());
        var response=receipts.base(head,"REVIEW_REQUIRED",sha(body));response.set("issues",issues);return response;
    }
    private ArrayNode authorization(SubmissionStore.Head head,AssignmentStore.View assignment) {
        var issues=object().putArray("issues");
        var claims=parse(Base64.getUrlDecoder().decode(assignment.offlineToken().split("\\.")[1]));
        var status=jdbc.queryForObject("""
            SELECT o.revoked_at IS NULL AND u.status='ACTIVE' AND d.status='ACTIVE' AND u.auth_version=? AND d.row_version=?
            FROM offline_authorization o INNER JOIN app_user u ON u.id=o.user_id INNER JOIN device d ON d.id=o.device_id WHERE o.assignment_id=?
              AND o.authorization_sequence=(SELECT MAX(current_authorization.authorization_sequence)
                FROM offline_authorization current_authorization WHERE current_authorization.assignment_id=o.assignment_id)
            """,Boolean.class,number(claims,"userAuthVersion"),number(claims,"deviceVersion"),Ids.bytes(head.control().assignment()));
        if(!Boolean.TRUE.equals(status) || !assignment.status().equals("ACTIVE")) issues.add(object().put("code","OFFLINE_AUTH_REAUTHORIZATION_REQUIRED"));
        if(!assignment.serverRecoveryEpoch().equals(head.control().epoch())) issues.add(object().put("code","ORDER_RECOVERY_REQUIRED"));
        if(assignment.downloadAckAt()==null) issues.add(object().put("code","DOWNLOAD_ACK_REQUIRED"));
        int valid=jdbc.queryForObject("SELECT COUNT(*) FROM outbound_order WHERE id=? AND current_revision=? AND status IN ('ASSIGNED','REVIEW_REQUIRED','CONTINUING')",
            Integer.class,Ids.bytes(head.control().order()),assignment.revision());
        if(valid!=1) issues.add(object().put("code","ORDER_STATE_CONFLICT"));
        return issues;
    }
    public record Approval(boolean approved,UUID actor) { }
}
