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
    private final com.acrylic.outbound.temporary.continuation.TemporaryRoundConfirmationSource rounds;
    private final com.acrylic.outbound.handover.TemporaryHandoverConfirmationSource handovers;
    public TemporaryConfirmationSource(SubmissionStore submissions,SubmissionFiles files,TemporarySubmissionPlan plans,
        TemporarySubmissionEvidence evidence,TemporarySubmissionSnapshot snapshots,TemporaryReviewEligibility eligibility,ServerState server,
        org.springframework.jdbc.core.JdbcTemplate jdbc,com.acrylic.outbound.temporary.continuation.TemporaryRoundConfirmationSource rounds,
        com.acrylic.outbound.handover.TemporaryHandoverConfirmationSource handovers) {
        this.submissions=submissions;this.files=files;this.plans=plans;this.evidence=evidence;this.snapshots=snapshots;
        this.eligibility=eligibility;this.server=server;this.jdbc=jdbc;this.rounds=rounds;this.handovers=handovers;
    }
    public Value read(CommandContext context) {
        var head=submissions.required(context.id("submissionId"));submissions.current(head);
        if(head.control().temporary()==null || head.order()==null || !head.status().equals("REVIEW_REQUIRED"))throw CommandErrors.stale();
        if(head.control().round()!=1) {
            if(handovers.contains(head.control().id())) {
                var checked=handovers.read(head);var issues=reasons(checked.authorization());
                if(!issues.isEmpty())throw new BusinessException(409,issues.getFirst(),"交接新资格条件不满足，保留待审及占用");
                return checked.value();
            }
            var checked=rounds.read(head);var issues=reasons(checked.authorization());
            if(!issues.isEmpty())throw new BusinessException(409,issues.getFirst(),"续作新资格条件不满足，保留待审及占用");
            return checked.value();
        }
        long mappingVersion=context.version("mappingVersion");
        boolean linked=confirmableSource(context,mappingVersion);
        target(head,linked);
        var hash=submissions.payloadHash(head.control().id());if(hash==null)throw CommandErrors.stale();
        var body=parse(files.read(hash));equal(sha(body),hash,"原受理正文损坏");
        equal(SubmissionControl.read(uuid(body,"submissionId"),body),head.control(),"原控制与正文不符");
        if(linked)requireReviewedPlan(head,body);
        var plan=plans.validate(head,body);evidence.validate(head,plan,body);var value=snapshots.validate(plan,body);
        var issues=reasons(plan.authorization());
        if(!issues.isEmpty())throw new BusinessException(409,issues.getFirst(),"原资格或当前身份条件不满足，保留待审及占用");
        require(!value.scans().isEmpty(),"整单零包不能确认出库");
        equal(context.text("targetRevision"),text(body,"orderRevision"),"目标计划版本已变化");
        return new Value(head,body,value,linked);
    }
    /**
     * A temporary source is confirmable in exactly two states: its untouched v1 mapping, or a v2+ mapping
     * installed by the controlled link. Any other origin (or a missing/foreign mapping row) stays stale, so
     * a version bump by anything but the reviewed link can never make a source confirmable.
     */
    private boolean confirmableSource(CommandContext context,long mappingVersion) {
        if(mappingVersion<1)throw CommandErrors.stale();
        var origins=jdbc.queryForList("""
            SELECT m.origin FROM submission_scope c JOIN temporary_source_mapping m
              ON m.scope_id=c.id AND m.mapping_version=c.mapping_version
            WHERE c.id=? AND m.canonical_order_id=c.canonical_order_id
            """,String.class,Ids.bytes(context.id("scopeId")));
        if(origins.size()!=1)throw CommandErrors.stale();
        boolean controlled="CONTROLLED_LINK".equals(origins.getFirst());
        if(mappingVersion==1) { if(controlled)throw CommandErrors.stale(); return false; }
        if(!controlled)throw CommandErrors.stale();
        return true;
    }
    /**
     * The linked formal order must still carry exactly the plan the reviewer bound, so an in-place edit under
     * the same revision cannot slip through the confirmation. This mirrors what the link itself verified.
     */
    private void requireReviewedPlan(SubmissionStore.Head head,JsonNode body) {
        var expected=new java.util.HashSet<String>();
        TemporaryDecisionCandidates.products(body).forEach(line->expected.add(lineKey(line.path("deliveryCode").asText(),
            line.path("productId").asText(),line.path("model").asText(),line.path("name").asText(),line.path("plannedCount").asText())));
        var actual=new java.util.HashSet<String>();
        jdbc.query("""
            SELECT l.delivery_code,BIN_TO_UUID(l.product_id),l.model_snapshot,l.name_snapshot,CAST(l.planned_count AS CHAR)
            FROM order_line l JOIN order_revision r ON r.id=l.revision_id
            WHERE r.order_id=? AND r.revision=(SELECT current_revision FROM outbound_order WHERE id=?)
            """,(rs,n)->actual.add(lineKey(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5))),
            Ids.bytes(head.order()),Ids.bytes(head.order()));
        if(!expected.equals(actual))
            throw new BusinessException(409,"TEMPORARY_LINK_TARGET_PLAN_DIFFERS","目标正式单计划与原临时计划不一致，请重新核对候选");
    }
    private static String lineKey(String deliveryCode,String productId,String model,String name,String plannedCount) {
        return deliveryCode+"|"+productId+"|"+model+"|"+name+"|"+plannedCount;
    }
    private void target(SubmissionStore.Head head,boolean linked) {
        // v1: the scope canonical is still the PDA_TEMP order awaiting review.
        // linked: the link moved the canonical (and the slot) to the reviewed formal order, which is still
        // blank and without a result; anything else means the target changed after the link.
        int matches=jdbc.queryForObject("""
            SELECT COUNT(*) FROM outbound_order o JOIN order_submission_slot l ON l.order_id=o.id
            WHERE o.id=? AND o.warehouse_id=? AND o.source=? AND o.status=? AND o.current_result_version IS NULL
              AND l.current_submission_id=? AND l.phase='FROZEN'
            """,Integer.class,Ids.bytes(head.order()),Ids.bytes(head.warehouse()),linked?"WEB":"PDA_TEMP",
            linked?"READY":"REVIEW_REQUIRED",Ids.bytes(head.control().id()));
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
    public record Value(SubmissionStore.Head head,JsonNode body,SubmissionSnapshot.Validated snapshot,boolean linked) { }
}
