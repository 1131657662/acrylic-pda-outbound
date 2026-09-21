package com.acrylic.outbound.inventory;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IdentitySetPcIT extends IdentitySetFixture {
    @Test void concurrentSameHandoverKeepsOriginalReceiptAcrossOperatorChange() throws Exception {
        var f=new PcExceptionFixture(web());var target=f.user(jdbc,passwords,"WAREHOUSE_ADMIN");
        f.stop();f.propose(f.plan(target.identity().user().username()));f.approve();
        var body=f.application();String key=UUID.randomUUID().toString();
        assertThat(changedBeforeLock("C04-handover-replay",s->s.startsWith("UPDATE pc_work SET operator_id="),
            ()->code(target.send(f.pc.request("exception/apply",body,key))),
            ()->code(target.send(f.pc.request("exception/apply",body,key))))).isEqualTo("OK");
        assertThat(f.pc.view().at("/work/round").asInt()).isEqualTo(2);
        var a=target.data(f.pc.request("exception/apply",body,key));
        var b=target.data(f.pc.request("exception/apply",body,key));assertThat(a).isEqualTo(b);
        assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);
        recordState("C04-handover-replay",f.pc.order);
    }
    @Test void handoverChangesOriginalOperatorWhileReviewHasOldHint() throws Exception {
        var f=new PcExceptionFixture(web());var target=f.user(jdbc,passwords,"WAREHOUSE_ADMIN");
        f.stop();f.propose(f.plan(target.identity().user().username()));f.approve();
        var apply=f.pc.request("exception/apply",f.application(),UUID.randomUUID().toString());
        var review=f.pc.request("exception/review",f.decision("REJECT"),UUID.randomUUID().toString());
        assertThat(changedBeforeLock("C04-original-operator",s->s.startsWith("UPDATE pc_work SET operator_id="),
            ()->code(target.send(apply)),()->code(f.pc.api.send(review)))).isEqualTo("PC_IDENTITY_CONTEXT_CHANGED");
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(operator_id) FROM pc_work WHERE order_id=UUID_TO_BIN(?)",String.class,f.pc.order))
            .isEqualTo(target.identity().user().id().toString());
        assertThat(f.view().at("/plans/0/status").asText()).isEqualTo("APPLIED");
        assertThat(count("SELECT COUNT(*) FROM pc_exception_review WHERE plan_id=UUID_TO_BIN(?)",f.view().at("/plans/0/id").asText())).isEqualTo(1);
        assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);
        recordState("C04-original-operator",f.pc.order);
    }
    @Test void approvalChangesApproverWhileApplicationHasOldHintAndOriginalKeyCanRetry() throws Exception {
        var f=new PcExceptionFixture(web());var supervisor=f.user(jdbc,passwords,"WAREHOUSE_ADMIN");
        f.stop();f.propose(f.plan(f.pc.api.identity().user().username()));
        var review=f.pc.request("exception/review",f.decision("APPROVE"),UUID.randomUUID().toString());
        var body=f.application();String key=UUID.randomUUID().toString();
        var apply=f.pc.request("exception/apply",body,key);
        assertThat(changedBeforeLock("C04-approver",s->s.startsWith("UPDATE pc_exception_plan SET status=?,approved_by="),
            ()->code(supervisor.send(review)),()->code(f.pc.api.send(apply)))).isEqualTo("PC_IDENTITY_CONTEXT_CHANGED");
        assertThat(f.view().at("/plans/0/status").asText()).isEqualTo("APPROVED");
        assertThat(f.pc.view().at("/work/round").asInt()).isEqualTo(1);
        body.put("expectedVersion",f.pc.view().at("/work/version").asText());
        assertThat(code(f.pc.api.send(f.pc.request("exception/apply",body,key)))).isEqualTo("OK");
        assertThat(f.pc.view().at("/work/round").asInt()).isEqualTo(2);
        recordState("C04-approver",f.pc.order);
    }
    @Test void replacementPlanDoesNotReuseOldTargetOrApproval() throws Exception {
        var f=new PcExceptionFixture(web());var target=f.user(jdbc,passwords,"WAREHOUSE_ADMIN");
        f.stop();f.propose(f.plan(f.pc.api.identity().user().username()));
        f.pc.send("exception/review",f.decision("REJECT"));
        var old=f.pc.request("exception/apply",f.application(),UUID.randomUUID().toString());
        var replacement=f.pc.request("exception/plan",f.plan(target.identity().user().username()),UUID.randomUUID().toString());
        assertThat(changedBeforeLock("C04-target-plan",s->s.contains("INSERT INTO pc_exception_plan("),
            ()->code(f.pc.api.send(replacement)),()->code(f.pc.api.send(old)))).isEqualTo("PC_IDENTITY_CONTEXT_CHANGED");
        assertThat(f.view().at("/plans/0/body/targetUserId").asText()).isEqualTo(target.identity().user().id().toString());
        assertThat(f.view().at("/plans/0/status").asText()).isEqualTo("PENDING");
        assertThat(f.pc.view().at("/work/round").asInt()).isEqualTo(1);
        assertThat(f.held(jdbc,"PC_RESERVED")).isEqualTo(3);
        recordState("C04-target-plan",f.pc.order);
    }
}
