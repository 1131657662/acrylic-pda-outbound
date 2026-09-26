package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.temporary.review.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Rebuild business facts from accepted original bytes while source/identity locks are held. */
@Component
public class TemporaryConfirmationSource {
    private final SubmissionStore submissions; private final SubmissionFiles files; private final TemporarySubmissionPlan plans;
    private final TemporarySubmissionEvidence evidence; private final TemporarySubmissionSnapshot snapshots;
    private final TemporaryReviewEligibility eligibility; private final ServerState server;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public TemporaryConfirmationSource(SubmissionStore submissions,SubmissionFiles files,TemporarySubmissionPlan plans,
        TemporarySubmissionEvidence evidence,TemporarySubmissionSnapshot snapshots,TemporaryReviewEligibility eligibility,ServerState server,
        org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.submissions=submissions;this.files=files;this.plans=plans;this.evidence=evidence;this.snapshots=snapshots;
        this.eligibility=eligibility;this.server=server;this.jdbc=jdbc;
    }
    public Value read(CommandContext context) {
        var head=submissions.required(context.id("submissionId"));submissions.current(head);
        if(head.control().temporary()==null || head.order()==null || !head.status().equals("REVIEW_REQUIRED")
            || head.control().round()!=1 || !"1".equals(context.text("mappingVersion")))throw CommandErrors.stale();
        target(head);
        var hash=submissions.payloadHash(head.control().id());if(hash==null)throw CommandErrors.stale();
        var body=parse(files.read(hash));equal(sha(body),hash,"原受理正文损坏");
        equal(SubmissionControl.read(uuid(body,"submissionId"),body),head.control(),"原控制与正文不符");
        var plan=plans.validate(head,body);evidence.validate(head,plan,body);var value=snapshots.validate(plan,body);
        var issues=reasons(plan.authorization());
        if(!issues.isEmpty())throw new BusinessException(409,issues.getFirst(),"原资格或当前身份条件不满足，保留待审及占用");
        require(!value.scans().isEmpty(),"整单零包不能确认出库");
        equal(context.text("targetRevision"),text(body,"orderRevision"),"目标计划版本已变化");
        return new Value(head,body,value);
    }
    private void target(SubmissionStore.Head head) {
        int matches=jdbc.queryForObject("""
            SELECT COUNT(*) FROM outbound_order o JOIN order_submission_slot l ON l.order_id=o.id
            WHERE o.id=? AND o.warehouse_id=? AND o.source='PDA_TEMP' AND o.status='REVIEW_REQUIRED'
              AND o.current_result_version IS NULL AND l.current_submission_id=? AND l.phase='FROZEN'
            """,Integer.class,Ids.bytes(head.order()),Ids.bytes(head.warehouse()),Ids.bytes(head.control().id()));
        if(matches!=1)throw CommandErrors.stale();
    }
    public java.util.List<String> reasons(TemporaryStore.Stored authorization) {
        var result=new java.util.ArrayList<String>();
        for(var issue:eligibility.current(authorization,server.current())) {
            String code=issue.path("code").asText();
            // Expiry stops new saving, not confirmation of evidence saved inside its original validity window.
            if(!code.equals("TEMPORARY_ORDER_CONFIRMATION_REQUIRED") && !code.equals("TEMPORARY_AUTH_EXPIRED"))result.add(code);
        }
        return java.util.List.copyOf(result);
    }
    public record Value(SubmissionStore.Head head,JsonNode body,SubmissionSnapshot.Validated snapshot) { }
}
