package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.*;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Actual recipient submission and separate supervisor accounting, with final SQL rollback. */
final class HandoverTemporarySubmissionChecks {
    private final HandoverTemporaryTargetFixture f;private final WebApplicationContext app;private final Path directory;
    private final ObjectNode body;private final UUID submission;
    HandoverTemporarySubmissionChecks(HandoverTemporaryTargetFixture f,WebApplicationContext app,Path directory) throws Exception {
        this.f=f;this.app=app;this.directory=directory;
        body=(ObjectNode)parse(Files.readAllBytes(directory.resolve("android-handover-payload.json")));submission=uuid(body,"submissionId");
    }
    void verify() throws Exception {
        var head=app.getBean(SubmissionStore.class).required(submission);
        assertThat(head.actor()).isEqualTo(f.user);assertThat(head.control().device()).isEqualTo(f.device);
        assertThat(head.status()).isEqualTo("REVIEW_REQUIRED");assertThat(head.control().temporary().client()).isEqualTo(uuid(body.path("source"),"clientOrderId"));
        assertThat(body.has("temporaryContinuation")).isFalse();assertThat(head.control().assignment()).isNull();
        var original=original();
        for(String field:List.of("captures","scans","events"))for(int i=0;i<array(original,field).size();i++)
            assertThat(array(body,field).get(i)).isEqualTo(array(original,field).get(i));
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=? AND state='REVIEW_RESERVED'",Long.class,
            Ids.bytes(submission))).isEqualTo(number(body,"expectedCount"));
        authorize();targetPlanChange();rollback();byte[] receipt=confirm();
        assertThat(f.context.reviewer().post("/temporary-commands",commandForReplay,200,null)).isEqualTo(receipt);
        f.context.reviewer().post("/temporary-commands",commandForReplay.deepCopy().put("reason","different"),409,"IDEMPOTENCY_CONTENT_MISMATCH");
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_completion WHERE submission_id=?",Long.class,Ids.bytes(submission))).isEqualTo(1);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=? AND state='DISPATCHED'",Long.class,
            Ids.bytes(submission))).isEqualTo(number(body,"expectedCount"));
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_submission WHERE submission_id=?",Long.class,Ids.bytes(submission))).isZero();
        Files.write(directory.resolve("server-success-command.json"),canonical(commandForReplay).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(directory.resolve("server-success.json"),receipt);
    }
    private com.fasterxml.jackson.databind.JsonNode original() throws Exception {
        var original=parse(Files.readAllBytes(directory.resolve("android-original.json")));
        assertThat(original).isEqualTo(f.context.body());
        var hashes=f.source.jdbc.queryForList("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?",String.class,
            Ids.bytes(f.source.head.control().id()));
        if(hashes.isEmpty()) {
            var stopped=parse(f.source.head.receipt());assertThat(stopped.path("payloadPresent").asBoolean()).isFalse();
            assertThat(stopped.path("payloadSha256").isNull()).isTrue();
            assertThat(stopped.path("localFrozenPayloadSha256").asText()).isEqualTo(sha(original));
        } else assertThat(hashes).containsExactly(sha(original));
        return original;
    }
    private ObjectNode commandForReplay;
    private void authorize() throws Exception {
        var checker=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("permission-sql"));
        var before=checker.snapshot("before-denied");
        f.context.reviewer().post("/temporary-commands",command(),403,"PERMISSION_DENIED");
        assertThat(checker.snapshot("after-denied")).isEqualTo(before);
        f.source.jdbc.update("INSERT IGNORE INTO user_role SELECT u.id,r.id FROM app_user u JOIN role r ON r.code='TEMPORARY_ORDER_CONFIRMER' WHERE u.username=?",
            f.context.username());
    }
    private void targetPlanChange() throws Exception {
        var head=app.getBean(SubmissionStore.class).required(submission);
        var revision=f.source.jdbc.queryForObject("SELECT id FROM order_revision WHERE order_id=? AND revision=?",byte[].class,
            Ids.bytes(head.order()),number(body,"orderRevision"));
        var code=text(array(body.path("temporaryOrder"),"lines").get(0),"deliveryCode");
        f.source.jdbc.update("UPDATE order_line SET planned_count=planned_count+1 WHERE revision_id=? AND delivery_code=?",revision,code);
        try { f.context.reviewer().post("/temporary-commands",command(),400,"INVALID_SUBMISSION"); }
        finally { f.source.jdbc.update("UPDATE order_line SET planned_count=planned_count-1 WHERE revision_id=? AND delivery_code=?",revision,code); }
        assertThat(app.getBean(SubmissionStore.class).required(submission).status()).isEqualTo("REVIEW_REQUIRED");
    }
    private byte[] confirm() throws Exception {
        commandForReplay=command();var receipt=f.context.reviewer().post("/temporary-commands",commandForReplay,200,null);
        assertThat(parse(receipt).at("/businessDecision/submissionReceipt/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(parse(receipt).path("capabilities").path("localDeletionAllowed").asBoolean()).isFalse();return receipt;
    }
    private void rollback() throws Exception {
        var before=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("submission-sql"));
        var snapshot=before.snapshot("before-final-fault");var failed=command();
        var continuation=new HandoverMixedContinuationSql(f.source.jdbc,directory.resolve("submission-sql"));
        var original=continuation.snapshot("before-final-fault");
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(com.acrylic.outbound.common.ApiErrors.class);
        var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();logger.addAppender(logs);
        f.source.jdbc.execute("CREATE TRIGGER d06_handover_completion_fault AFTER INSERT ON temporary_handover_completion FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D06_HANDOVER_FINAL_SQL'");
        try { f.context.reviewer().post("/temporary-commands",failed,500,"INTERNAL_ERROR");HandoverTemporaryIntakeChecks.requireFault(logs.list,"D06_HANDOVER_FINAL_SQL"); }
        finally { f.source.jdbc.execute("DROP TRIGGER d06_handover_completion_fault");logger.detachAppender(logs);logs.stop(); }
        assertThat(before.snapshot("after-final-fault")).isEqualTo(snapshot);
        assertThat(continuation.snapshot("after-final-fault")).isEqualTo(original);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_completion WHERE submission_id=?",Long.class,Ids.bytes(submission))).isZero();
        Files.writeString(directory.resolve("submission-sql/exact-sql-fault.txt"),"D06_HANDOVER_FINAL_SQL SQLSTATE=45000 code=1644; handover completion and all business/continuation rows rolled back\n");
    }
    private ObjectNode command() {
        var context=app.getBean(CommandFacts.class).read(submission);
        var request=object().put("commandVersion",1).put("commandType","NORMAL_CONFIRM").put("commandId",UUID.randomUUID().toString())
            .put("transportRecoveryEpoch","1").put("reason","");
        for(String field:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))
            request.set(field,context.json().get(field));
        request.putObject("source").put("kind","TEMP").put("creatorDeviceId",context.text("creatorDeviceId")).put("clientOrderId",context.text("clientOrderId"));
        var candidates=app.getBean(TemporaryDecisionCandidates.class).snapshot(f.source.head.warehouse(),submission,body);
        var expected=CommandExpected.current(context,"1");expected.set("candidateContextSha256",candidates.get("contextSha256"));
        request.set("expected",expected);return request;
    }
}
