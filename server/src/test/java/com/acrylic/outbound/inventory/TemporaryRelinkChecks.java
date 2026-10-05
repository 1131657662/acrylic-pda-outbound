package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Author checks using generated identities, real command HTTP and raw database projections. */
public final class TemporaryRelinkChecks {
    private final WebApplicationContext context;
    public TemporaryRelinkChecks(WebApplicationContext context) { this.context=context; }

    public void verify(Path attempt) throws Exception {
        relinkAndReplay(attempt);
        ambiguousCandidates();
        missingCurrentCandidate();
        currentTargetHasWork();
        priorTargetHasWork();
        laterRelink();
        concurrentReplay();
        rollbackAfterPointerSwitch();
    }

    private void relinkAndReplay(Path attempt) throws Exception {
        var f=new TemporaryLinkedFixture(context);var first=command(f);
        byte[] firstReceipt=f.post(first);var before=new TemporaryLinkedFacts(f).state();
        String next=f.createTarget();var second=command(f);byte[] receipt=f.post(second);
        var parsed=SubmissionJson.parse(receipt);var link=parsed.at("/businessDecision/submissionReceipt");
        assertThat(link.path("sourceCanonicalOrderId").asText()).isEqualTo(f.target);
        assertThat(link.path("targetOrderId").asText()).isEqualTo(next);
        assertThat(link.path("mappingVersion").asText()).isEqualTo("3");
        assertThat(parsed.at("/businessDecision/candidates/total").asText()).isEqualTo("2");
        var after=new TemporaryLinkedFacts(f).state();
        assertSwitched(before,after,next);
        assertThat(f.post(second)).containsExactly(receipt);
        assertThat(f.post(first)).containsExactly(firstReceipt);
        assertThat(new TemporaryLinkedFacts(f).state()).isEqualTo(after);
        var old=f.detail(first.path("commandId").asText());
        assertThat(old.at("/currentEffect/evaluation").asText()).isEqualTo("CONTEXT_CHANGED");
        assertThat(old.at("/currentEffect/reasons").toString()).contains("LINK_SUPERSEDED_BY_NEW_MAPPING");
        assertThat(f.detail(second.path("commandId").asText()).at("/currentEffect/evaluation").asText()).isEqualTo("LINK_IN_FORCE");
        reject(f,f.source.command("NORMAL_CONFIRM",f.source.operator()),"TEMPORARY_DUPLICATES_UNRESOLVED");
        reject(f,second.deepCopy().put("reason","不同正文"),"IDEMPOTENCY_CONTENT_MISMATCH");
        reject(f,first.deepCopy().put("commandId",UUID.randomUUID().toString()),"COMMAND_CONTEXT_CHANGED");
        var evidence=SubmissionJson.object().put("firstRequest",SubmissionJson.canonical(first))
            .put("secondRequest",SubmissionJson.canonical(second)).put("firstReceipt",new String(firstReceipt,java.nio.charset.StandardCharsets.UTF_8))
            .put("secondReceipt",new String(receipt,java.nio.charset.StandardCharsets.UTF_8));
        evidence.set("before",before);evidence.set("after",after);evidence.set("oldDetail",old);
        Files.writeString(attempt.resolve("relink-evidence.json"),SubmissionJson.canonical(evidence));
    }

    private void assertSwitched(ObjectNode before,ObjectNode after,String next) {
        assertThat(after.at("/scope/canonical_order_id").asText()).isEqualTo(next);
        assertThat(after.at("/slot/order_id").asText()).isEqualTo(next);
        assertThat(after.at("/slot/slot_version").asText()).isEqualTo("3");
        assertThat(after.path("mappings")).hasSize(4);
        for(int i=0;i<3;i++)assertThat(after.path("mappings").get(i)).isEqualTo(before.path("mappings").get(i));
        assertThat(after.path("originalOrder")).isEqualTo(before.path("originalOrder"));
        assertThat(after.path("targetOrder")).isEqualTo(before.path("targetOrder"));
        assertThat(after.path("commitments")).isNotEmpty();
        after.path("commitments").forEach(row->{
            assertThat(row.path("order_id").asText()).isEqualTo(next);
            assertThat(row.path("state").asText()).isEqualTo("REVIEW_RESERVED");
        });
        assertThat(after.path("results")).isEmpty();assertThat(after.path("ledger")).isEmpty();
    }

    private void ambiguousCandidates() throws Exception {
        var f=linked();f.createTarget();f.createTarget();
        unchangedOnReject(f,"TEMPORARY_LINK_DUPLICATES_UNRESOLVED");
    }

    private void missingCurrentCandidate() throws Exception {
        var f=linked();outsideWindow(f,f.target);f.createTarget();f.createTarget();
        unchangedOnReject(f,"TEMPORARY_LINK_DUPLICATES_UNRESOLVED");
    }

    private void currentTargetHasWork() throws Exception {
        var f=linked();f.createTarget();addFormalScope(f,f.target);
        unchangedOnReject(f,"TEMPORARY_LINK_TARGET_HAS_WORK");
    }

    private void priorTargetHasWork() throws Exception {
        var f=linked();f.createTarget();f.post(command(f));
        outsideWindow(f,f.target);f.createTarget();addFormalScope(f,f.target);
        unchangedOnReject(f,"TEMPORARY_LINK_TARGET_HAS_WORK");
    }

    private void laterRelink() throws Exception {
        var f=linked();f.createTarget();var second=command(f);f.post(second);
        outsideWindow(f,f.target);String next=f.createTarget();
        var receipt=SubmissionJson.parse(f.post(command(f)));
        assertThat(receipt.at("/businessDecision/submissionReceipt/mappingVersion").asText()).isEqualTo("4");
        assertThat(receipt.at("/businessDecision/submissionReceipt/targetOrderId").asText()).isEqualTo(next);
        assertThat(f.detail(second.path("commandId").asText()).at("/currentEffect/evaluation").asText()).isEqualTo("CONTEXT_CHANGED");
    }

    private void concurrentReplay() throws Exception {
        var f=linked();f.createTarget();var request=command(f);
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<byte[]> call=()->{start.await();return f.post(request);};
            var one=executor.submit(call);var two=executor.submit(call);start.countDown();
            assertThat(one.get(30,java.util.concurrent.TimeUnit.SECONDS))
                .containsExactly(two.get(30,java.util.concurrent.TimeUnit.SECONDS));
        }
        var state=new TemporaryLinkedFacts(f).state();
        assertThat(state.at("/scope/mapping_version").asText()).isEqualTo("3");
        assertThat(state.path("receiptCount").asText()).isEqualTo("2");
        assertThat(state.path("mappings")).hasSize(4);
    }

    private void rollbackAfterPointerSwitch() throws Exception {
        var f=linked();f.createTarget();var request=command(f);var before=new TemporaryLinkedFacts(f).state();
        var commandState=f.source.state();
        f.source.jdbc.execute("CREATE TRIGGER relink_receipt_failure BEFORE INSERT ON temporary_command_receipt "
            +"FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='relink rollback probe'");
        try { assertThat(f.send(request).statusCode()).isEqualTo(500); }
        finally { f.source.jdbc.execute("DROP TRIGGER relink_receipt_failure"); }
        assertThat(new TemporaryLinkedFacts(f).state()).isEqualTo(before);
        assertThat(f.source.state()).isEqualTo(commandState);
        assertThat(SubmissionJson.parse(f.post(request)).at("/businessDecision/submissionReceipt/mappingVersion").asText()).isEqualTo("3");
    }

    private TemporaryLinkedFixture linked() throws Exception {
        var f=new TemporaryLinkedFixture(context);f.post(command(f));return f;
    }
    private ObjectNode command(TemporaryLinkedFixture f) {
        return f.source.command("LINK",f.source.operator()).put("reason","人工核对完整候选后受控重关联");
    }
    private void outsideWindow(TemporaryLinkedFixture f,String order) {
        f.source.jdbc.update("UPDATE outbound_order SET created_at=DATE_SUB(created_at,INTERVAL 30 DAY) WHERE id=UUID_TO_BIN(?)",order);
    }
    private void addFormalScope(TemporaryLinkedFixture f,String order) {
        f.source.jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
            UUID.randomUUID().toString(),f.source.source.f.web.identity().warehouse().toString(),order);
    }
    private void unchangedOnReject(TemporaryLinkedFixture f,String code) throws Exception {
        var before=new TemporaryLinkedFacts(f).state();var commandState=f.source.state();
        reject(f,command(f),code);
        assertThat(new TemporaryLinkedFacts(f).state()).isEqualTo(before);
        assertThat(f.source.state()).isEqualTo(commandState);
    }
    private void reject(TemporaryLinkedFixture f,ObjectNode command,String code) throws Exception {
        var response=f.send(command);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(code);
    }
}
