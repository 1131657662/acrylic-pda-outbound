package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual HTTP positives and counterexamples; receipts never stand in for an independent human. */
public final class TemporaryExclusionHttpChecks {
    private final WebApplicationContext context;
    public TemporaryExclusionHttpChecks(WebApplicationContext context) { this.context=context; }
    public void verify(Path attempt) throws Exception {
        positive(attempt);unrelated();selfApproval();permission();crossWarehouse();candidateDrift();stockConflict();concurrent();rollback();
        new TemporaryExclusionSelectionChecks(context).verify(attempt);withdrawn();
    }
    private void positive(Path attempt) throws Exception {
        var f=fixture();var id=f.cancelled();var command=f.command(id,"CANCELLED");
        var original=f.http.post(command);var after=f.state();var receipt=SubmissionJson.parse(original);
        assertThat(f.http.post(command)).containsExactly(original);assertThat(f.state()).isEqualTo(after);
        assertThat(f.http.original(f.originalPath(command))).containsExactly(original);
        assertThat(receipt.at("/businessDecision/candidates/total").asText()).isEqualTo("2");
        assertThat(receipt.at("/businessDecision/exclusion/candidate/order/status").asText()).isEqualTo("CANCELLED");
        assertThat(receipt.path("actor").path("principalId").asText()).isEqualTo(f.supervisor.id().toString());
        assertThat(f.source.jdbc.queryForObject("SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)",String.class,id)).isEqualTo("CANCELLED");
        assertThat(after.get("dispatch")).isEqualTo(List.of());assertThat(after.get("result")).isEqualTo(List.of());
        var changed=command.deepCopy().put("reason","相同原键异文不得重写");
        assertThat(f.http.send(changed).statusCode()).isEqualTo(409);assertThat(f.state()).isEqualTo(after);
        Files.write(attempt.resolve("exclusion-original.json"),original);
        Files.writeString(attempt.resolve("exclusion-command.json"),SubmissionJson.canonical(command));
        Files.writeString(attempt.resolve("exclusion-restart-input.json"),SubmissionJson.canonical(SubmissionJson.object()
            .put("username",f.supervisor.getUsername()).put("originalPath",f.originalPath(command))));
    }
    private void unrelated() throws Exception {
        var f=fixture();var command=f.command(f.unrelated(),"UNRELATED");
        assertThat(SubmissionJson.parse(f.http.post(command)).at("/businessDecision/exclusion/basis").asText()).isEqualTo("UNRELATED");
        var same=f.command(f.linked.target,"UNRELATED");var before=f.state();
        assertThat(f.http.send(same).statusCode()).isEqualTo(409);assertThat(f.state()).isEqualTo(before);
    }
    private void selfApproval() throws Exception {
        var f=fixture();var command=f.command(f.cancelled(),"CANCELLED");
        ((ObjectNode)command.path("expected")).put("userAuthVersion",Long.toString(f.source.operator().authVersion()));
        f.http.login(f.source.operator().getUsername());var before=f.state();
        var response=f.http.send(command);assertThat(response.statusCode()).isEqualTo(403);
        assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("TEMPORARY_EXCLUSION_SELF_APPROVAL_FORBIDDEN");
        assertThat(f.state()).isEqualTo(before);
    }
    private void permission() throws Exception {
        var f=fixture();var command=f.command(f.cancelled(),"CANCELLED");
        f.source.only(f.supervisor,List.of("submission:review"));var before=f.state();
        assertThat(f.http.send(command).statusCode()).isEqualTo(403);assertThat(f.state()).isEqualTo(before);
    }
    private void candidateDrift() throws Exception {
        var f=fixture();var command=f.command(f.cancelled(),"CANCELLED");f.linked.createTarget();var before=f.state();
        assertThat(f.http.send(command).statusCode()).isEqualTo(409);assertThat(f.state()).isEqualTo(before);
        var stale=f.command(command.at("/exclusion/orderId").asText(),"CANCELLED");
        f.source.jdbc.update("UPDATE outbound_order SET row_version=row_version+1 WHERE id=UUID_TO_BIN(?)",stale.at("/exclusion/orderId").asText());
        assertThat(f.http.send(stale).statusCode()).isEqualTo(409);assertThat(f.state()).isEqualTo(before);
    }
    private void crossWarehouse() throws Exception {
        var f=fixture();var other=fixture();var command=f.command(f.cancelled(),"CANCELLED");
        ((ObjectNode)command.path("exclusion")).put("orderId",other.cancelled());var before=f.state();
        assertThat(f.http.send(command).statusCode()).isEqualTo(409);assertThat(f.state()).isEqualTo(before);
    }
    private void stockConflict() throws Exception {
        var f=fixture();String id=f.cancelled();
        f.source.jdbc.update("UPDATE bale_commitment SET order_id=UUID_TO_BIN(?) WHERE submission_id=UUID_TO_BIN(?)",id,f.source.source.id());
        var command=f.command(id,"CANCELLED");var before=f.state();
        var response=f.http.send(command);assertThat(response.statusCode()).isEqualTo(409);
        assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("TEMPORARY_EXCLUSION_WORK_CONFLICT");
        assertThat(f.state()).isEqualTo(before);
    }
    private void concurrent() throws Exception {
        var f=fixture();var command=f.command(f.cancelled(),"CANCELLED");var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<byte[]> call=()->{start.await();return f.http.post(command);};
            var one=executor.submit(call);var two=executor.submit(call);start.countDown();
            assertThat(one.get(40,TimeUnit.SECONDS)).containsExactly(two.get(40,TimeUnit.SECONDS));
        }
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_command_receipt WHERE submission_id=UUID_TO_BIN(?)",Integer.class,f.source.source.id())).isEqualTo(1);
    }
    private void rollback() throws Exception {
        var f=fixture();var command=f.command(f.cancelled(),"CANCELLED");var before=f.state();
        f.source.jdbc.execute("CREATE TRIGGER exclusion_audit_failure BEFORE INSERT ON operation_event FOR EACH ROW "
            +"BEGIN IF NEW.entity_type='TEMPORARY_COMMAND' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='exclusion last SQL probe'; END IF; END");
        try { assertThat(f.http.send(command).statusCode()).isEqualTo(500); }
        finally { f.source.jdbc.execute("DROP TRIGGER exclusion_audit_failure"); }
        assertThat(f.state()).isEqualTo(before);assertThat(f.http.post(command)).isNotEmpty();
    }
    private void withdrawn() throws Exception {
        var f=fixture();String id=f.linked.createTarget();
        f.source.only(f.supervisor,List.of("temporary_order:link","temporary_order:confirm","submission:review","order:recall","order:publish"));
        f.source.jdbc.update("UPDATE outbound_order SET status='DRAFT' WHERE id=UUID_TO_BIN(?)",id);
        var publisher=new TemporaryCandidateHttp();publisher.login(f.supervisor.getUsername());
        String policy=f.source.jdbc.queryForObject("SELECT version FROM policy_version WHERE warehouse_id=UUID_TO_BIN(?) ORDER BY version DESC LIMIT 1",
            String.class,f.source.source.f.web.identity().warehouse().toString());
        publisher.post("/orders/"+id+"/publish?warehouseId="+f.source.source.f.web.identity().warehouse(),
            SubmissionJson.object().put("expectedVersion","1").put("expectedPolicyVersion",policy).put("reason","隔离候选正式发布"),UUID.randomUUID().toString());
        var claim=f.linked.claimTarget(id);
        assertThat(claim.statusCode()).as("claim: %s",SubmissionJson.parse(claim.body()).path("code")).isEqualTo(200);
        var active=f.command(id,"CANCELLED");var before=f.state();
        assertThat(SubmissionJson.parse(f.http.send(active).body()).path("code").asText()).isEqualTo("TEMPORARY_EXCLUSION_WORK_CONFLICT");
        assertThat(f.state()).isEqualTo(before);
        String version=f.source.jdbc.queryForObject("SELECT row_version FROM outbound_order WHERE id=UUID_TO_BIN(?)",String.class,id);
        var original=f.http.withdrawal("/orders/"+id+"/change-requests?warehouseId="+f.source.source.f.web.identity().warehouse(),
            SubmissionJson.object().put("type","FAULT_WITHDRAWAL").put("reason","隔离坏机无STOP撤回核验").put("expectedVersion",version));
        var command=f.command(id,"WITHDRAWN");((ObjectNode)command.path("exclusion")).set("withdrawalRequestId",original.path("changeRequestId"));
        var receipt=SubmissionJson.parse(f.http.post(command));
        assertThat(receipt.at("/businessDecision/exclusion/withdrawalReceipt")).isEqualTo(original);
        assertThat(f.http.post(command)).isNotEmpty();
    }
    private TemporaryExclusionHttpFixture fixture() throws Exception { return new TemporaryExclusionHttpFixture(context); }
}
