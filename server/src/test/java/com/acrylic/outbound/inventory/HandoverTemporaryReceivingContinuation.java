package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.command.*;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Real R06 commands consume the receiving-device STOP and immutable inherited owners. */
final class HandoverTemporaryReceivingContinuation {
    private final HandoverTemporaryTargetFixture f;
    private final WebApplicationContext app;
    private final Path directory;
    private final JsonNode original;
    private final UUID source;
    HandoverTemporaryReceivingContinuation(HandoverTemporaryTargetFixture f,WebApplicationContext app,Path directory) throws Exception {
        this.f=f;this.app=app;this.directory=directory;
        original=parse(Files.readAllBytes(directory.resolve("android-handover-payload.json")));source=uuid(original,"submissionId");
        f.source.jdbc=app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
    }

    void activate() throws Exception {
        var context=app.getBean(CommandFacts.class).read(source);
        assertThat(context.text("creatorDeviceId")).isEqualTo(text(original.path("source"),"creatorDeviceId"));
        assertThat(context.text("clientOrderId")).isEqualTo(text(original.path("source"),"clientOrderId"));
        var owners=f.source.jdbc.queryForList("SELECT * FROM bale_commitment ORDER BY bale_id");
        retain(context);
        resumePermission();
        context=app.getBean(CommandFacts.class).read(source);
        var request=reopen(context,null);wrongManifest(request);lastSql(request);
        byte[] issued=f.source.post("/temporary-commands",request,f.token,200);
        assertThat(f.source.post("/temporary-commands",request,f.token,200)).isEqualTo(issued);
        f.source.post("/temporary-commands",request.deepCopy().put("reason","different"),f.token,409);
        UUID ticket=uuid(parse(issued).path("businessDecision"),"ticketId");
        var activation=reopen(app.getBean(CommandFacts.class).read(source),ticket);
        byte[] raw=f.source.post("/temporary-commands",activation,f.token,200);
        assertThat(f.source.post("/temporary-commands",activation,f.token,200)).isEqualTo(raw);
        Files.write(directory.resolve("server-continuation-issued.json"),issued);
        Files.write(directory.resolve("server-continuation-activation.json"),raw);
        HandoverMixedCommandOriginChecks.verify(f.source.jdbc,source);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_bale WHERE continuation_id=?",Long.class,Ids.bytes(ticket)))
            .isEqualTo(number(original,"expectedCount"));
        activatedOwners(owners,ticket,context.id("evidenceSetId"));
    }
    private void activatedOwners(List<Map<String,Object>> before,UUID ticket,UUID evidence) {
        var retained=active().stream().map(scan->Ids.bytes(uuid(scan,"baleId"))).toList();
        var expected=before.stream().map(row->{
            var next=new HashMap<>(row);
            if(retained.stream().noneMatch(id->Arrays.equals(id,(byte[])row.get("bale_id"))))return next;
            next.put("state","TEMP_CONTINUATION_RESERVED");next.put("temporary_continuation_id",Ids.bytes(ticket));
            next.put("source_evidence_set_id",Ids.bytes(evidence));next.put("submission_id",null);next.put("order_id",null);
            return next;
        }).toList();
        assertThat(f.source.jdbc.queryForList("SELECT * FROM bale_commitment ORDER BY bale_id"))
            .usingRecursiveComparison().isEqualTo(expected);
    }
    private void resumePermission() throws Exception {
        var denied=reopen(app.getBean(CommandFacts.class).read(source),null);
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("continuation-permission"));
        var before=sql.snapshot("before");
        assertThat(parse(f.source.post("/temporary-commands",denied,f.token,403)).path("code").asText())
            .isEqualTo("PERMISSION_DENIED");
        assertThat(sql.snapshot("after-denial")).isEqualTo(before);
        // Explicit synthetic fixture grant; the production role/permission mapping is unchanged.
        assertThat(f.source.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'",Ids.bytes(f.user))).isEqualTo(1);
    }

    private void retain(CommandContext context) throws Exception {
        var command=base(context,"PHYSICAL_DISPOSITION");var manifest=object().put("version",1);var rows=manifest.putArray("items");
        active().forEach(scan->rows.addObject().put("baleId",text(scan,"baleId")).put("disposition","RETAIN"));
        command.set("dispositionManifest",manifest);
        var expected=(ObjectNode)command.get("expected");expected.put("dispositionManifestSha256",sha(manifest)).put("dispositionManifestVersion","1");
        nonRetained(command);
        byte[] raw=f.source.post("/temporary-commands",command,f.token,200);
        assertThat(f.source.post("/temporary-commands",command,f.token,200)).isEqualTo(raw);
        Files.write(directory.resolve("server-continuation-retain.json"),raw);
    }
    private void nonRetained(ObjectNode valid) throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("continuation-release-denied"));
        var before=sql.snapshot("before");
        for(String action:List.of("RELEASE","QUARANTINE")) {
            var wrong=valid.deepCopy();
            ((ObjectNode)array(wrong.path("dispositionManifest"),"items").get(0)).put("disposition",action);
            ((ObjectNode)wrong.path("expected")).put("dispositionManifestSha256",sha(wrong.path("dispositionManifest")));
            assertThat(parse(f.source.post("/temporary-commands",wrong,f.token,409)).path("code").asText())
                .isEqualTo("COMMAND_EXECUTION_NOT_IMPLEMENTED");
            assertThat(sql.snapshot("after-"+action)).isEqualTo(before);
        }
    }
    private ObjectNode reopen(CommandContext context,UUID ticket) {
        var command=base(context,ticket==null?"REOPEN_WORK":"ACTIVATE_CONTINUATION");
        if(ticket!=null)command.put("continuationTicketId",ticket.toString());
        command.put("nextOrderScanSeq",Long.toString(number(original,"captureHighWatermark")+1));
        var manifest=object().put("version",1);var rows=manifest.putArray("items");
        active().forEach(scan->rows.addObject().put("baleId",text(scan,"baleId"))
            .put("expectedDispatchCycle",text(scan,"expectedDispatchCycle")).put("recordSha256",sha(scan)));
        command.set("retainedManifest",manifest);
        ((ObjectNode)command.get("expected")).put("retainedManifestSha256",sha(manifest)).put("retainedManifestVersion","1");return command;
    }
    private List<JsonNode> active() {
        var scans=new ArrayList<JsonNode>();array(original,"scans").forEach(scan->{if("ACTIVE".equals(text(scan,"status")))scans.add(scan);});
        scans.sort(Comparator.comparing(scan->uuid(scan,"baleId")));return scans;
    }
    private ObjectNode base(CommandContext context,String kind) {
        var command=object().put("commandVersion",1).put("commandType",kind).put("commandId",UUID.randomUUID().toString())
            .put("transportRecoveryEpoch","1").put("reason","隔离混合接续，原包继续保留");
        for(String field:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))command.set(field,context.json().get(field));
        command.putObject("source").put("kind","TEMP").put("creatorDeviceId",context.text("creatorDeviceId")).put("clientOrderId",context.text("clientOrderId"));
        var expected=CommandExpected.current(context,"1");
        for(String field:List.of("evidenceSetId","evidenceSetSha256","verificationId","verificationVersion","verificationAlgorithmVersion"))expected.set(field,context.json().get(field));
        command.set("expected",expected);return command;
    }
    private void wrongManifest(ObjectNode valid) throws Exception {
        var wrong=valid.deepCopy();array(wrong.path("retainedManifest"),"items").remove(0);
        ((ObjectNode)wrong.path("expected")).put("retainedManifestSha256",sha(wrong.path("retainedManifest")));
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("continuation-reduced"));var before=sql.snapshot("before");
        f.source.post("/temporary-commands",wrong,f.token,409);
        assertThat(sql.snapshot("after")).isEqualTo(before);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation WHERE previous_submission_id=?",Long.class,Ids.bytes(source))).isZero();
    }
    private void lastSql(ObjectNode command) throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("continuation-tail"));var before=sql.snapshot("before");
        var continuation=new HandoverMixedContinuationSql(f.source.jdbc,directory.resolve("continuation-tail"));
        var continuationBefore=continuation.snapshot("before");
        f.source.jdbc.execute("CREATE TRIGGER mixed_continuation_tail AFTER INSERT ON operation_event FOR EACH ROW BEGIN IF NEW.event_type='TEMPORARY_REOPEN_WORK' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D06_MIXED_COMMAND_TAIL'; END IF; END");
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(com.acrylic.outbound.common.ApiErrors.class);
        var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();logger.addAppender(logs);
        try {f.source.post("/temporary-commands",command,f.token,500);HandoverTemporaryIntakeChecks.requireFault(logs.list,"D06_MIXED_COMMAND_TAIL");}
        finally {f.source.jdbc.execute("DROP TRIGGER mixed_continuation_tail");logger.detachAppender(logs);logs.stop();}
        assertThat(sql.snapshot("after")).isEqualTo(before);
        assertThat(continuation.snapshot("after")).isEqualTo(continuationBefore);
        Files.writeString(directory.resolve("continuation-tail/exact-sql-fault.txt"),"D06_MIXED_COMMAND_TAIL SQLSTATE=45000 code=1644; business and continuation tables unchanged\n");
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation WHERE previous_submission_id=?",Long.class,Ids.bytes(source))).isZero();
    }
    void complete() throws Exception {
        var body=parse(Files.readAllBytes(directory.resolve("android-continuation-payload.json")));UUID id=uuid(body,"submissionId");
        var head=app.getBean(SubmissionStore.class).required(id);assertThat(head.status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(head.control().temporary().creator()).isEqualTo(f.device);assertThat(head.control().round()).isEqualTo(3);
        f.source.jdbc.update("INSERT IGNORE INTO user_role SELECT u.id,r.id FROM app_user u JOIN role r ON r.code='TEMPORARY_ORDER_CONFIRMER' WHERE u.username=?",f.context.username());
        var context=app.getBean(CommandFacts.class).read(id);var command=base(context,"NORMAL_CONFIRM").put("reason","");
        var expected=CommandExpected.current(context,"1");expected.set("candidateContextSha256",app.getBean(TemporaryDecisionCandidates.class).snapshot(f.source.head.warehouse(),id,body).get("contextSha256"));
        command.set("expected",expected);confirmationTail(command,id);
        byte[] raw=f.context.reviewer().post("/temporary-commands",command,200,null);
        assertThat(f.context.reviewer().post("/temporary-commands",command,200,null)).isEqualTo(raw);
        assertThat(parse(raw).at("/businessDecision/submissionReceipt/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(parse(raw).path("capabilities").path("localDeletionAllowed").asBoolean(true)).isFalse();
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=? AND state='DISPATCHED'",Long.class,Ids.bytes(id))).isEqualTo(4);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_closure WHERE target_submission_id=? AND status='COMPLETED'",Long.class,Ids.bytes(id))).isEqualTo(1);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_completion WHERE submission_id=?",Long.class,Ids.bytes(id))).isZero();
        Files.write(directory.resolve("server-continuation-success.json"),raw);
    }
    private void confirmationTail(ObjectNode command,UUID id) throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("continuation-confirmation-tail"));
        var before=sql.snapshot("before");
        var continuation=new HandoverMixedContinuationSql(f.source.jdbc,directory.resolve("continuation-confirmation-tail"));
        var continuationBefore=continuation.snapshot("before");
        f.source.jdbc.execute("CREATE TRIGGER mixed_confirmation_tail AFTER INSERT ON operation_event FOR EACH ROW BEGIN IF NEW.event_type='TEMPORARY_NORMAL_CONFIRM' AND EXISTS(SELECT 1 FROM temporary_source_continuation_closure WHERE target_submission_id=UUID_TO_BIN('"+id+"') AND status='COMPLETED') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D06_MIXED_CONFIRMATION_TAIL'; END IF; END");
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(com.acrylic.outbound.common.ApiErrors.class);
        var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();logger.addAppender(logs);
        try {f.context.reviewer().post("/temporary-commands",command,500,"INTERNAL_ERROR");HandoverTemporaryIntakeChecks.requireFault(logs.list,"D06_MIXED_CONFIRMATION_TAIL");}
        finally {f.source.jdbc.execute("DROP TRIGGER mixed_confirmation_tail");logger.detachAppender(logs);logs.stop();}
        assertThat(sql.snapshot("after")).isEqualTo(before);
        assertThat(continuation.snapshot("after")).isEqualTo(continuationBefore);
        Files.writeString(directory.resolve("continuation-confirmation-tail/exact-sql-fault.txt"),"D06_MIXED_CONFIRMATION_TAIL SQLSTATE=45000 code=1644; completed closure rolled back with all business and continuation rows\n");
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_closure WHERE target_submission_id=?",Long.class,Ids.bytes(id))).isZero();
    }
}
