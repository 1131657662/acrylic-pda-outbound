package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Uses B's actual LOCAL roots; SQL injections cannot create a partial selection, owner transfer or receipt. */
final class HandoverTemporaryLocalChecks {
    private final HandoverTemporaryTargetFixture f;private final Path directory;private final UUID id;
    HandoverTemporaryLocalChecks(HandoverTemporaryTargetFixture f,Path directory) throws Exception {
        this.f=f;this.directory=directory;id=uuid(parse(Files.readAllBytes(directory.resolve("android-handover-payload.json"))),"submissionId");
    }
    void registered() throws Exception {
        noPayload();var row=f.source.jdbc.queryForMap("SELECT s.status,BIN_TO_UUID(c.current_submission_id) current_id,l.phase "
            +"FROM submission s JOIN submission_scope c ON c.id=s.scope_id JOIN order_submission_slot l ON l.scope_id=c.id WHERE s.id=?",Ids.bytes(id));
        assertThat(row.get("status")).isEqualTo("RECEIVING");assertThat(row.get("current_id")).isNull();assertThat(row.get("phase")).isEqualTo("CONTINUING");
        var body=parse(Files.readAllBytes(directory.resolve("android-handover-payload.json")));
        var invalid=object().put("submissionId",id.toString()).put("reason","unverified local proof")
            .put("sourceEvidenceSetId",UUID.randomUUID().toString()).put("verificationId",UUID.randomUUID().toString()).put("setSha256","0".repeat(64));
        invalid.set("temporaryHandover",body.path("temporaryHandover"));
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("local-register-sql"));var before=sql.snapshot("before");
        send(invalid,f.token,409);assertThat(sql.snapshot("after")).isEqualTo(before);
    }
    void before() throws Exception {
        noPayload();var request=request();var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("local-proof-sql"));var before=sql.snapshot("before");
        send(request,f.source.otherDeviceToken,404);send(request.deepCopy().put("verificationId",UUID.randomUUID().toString()),f.token,409);
        assertThat(sql.snapshot("after")).isEqualTo(before);
        manifestFault(request);missingOriginal(request);tailFault(request);
    }
    void after() throws Exception {
        noPayload();var request=request();var receipt=parse(send(request,f.token,200)).path("data");
        assertThat(receipt.path("status").asText()).isEqualTo("ABORTED");assertThat(receipt.path("payloadPresent").asBoolean()).isFalse();
        assertThat(receipt.path("payloadSha256").isNull()).isTrue();assertThat(receipt.path("localDeletionAllowed").asBoolean()).isFalse();
        var body=parse(Files.readAllBytes(directory.resolve("android-handover-payload.json")));
        assertThat(receipt.path("localFrozenPayloadSha256").asText()).isEqualTo(sha(body));
        assertThat(parse(send(request,f.token,200)).path("data")).isEqualTo(receipt);send(request.deepCopy().put("reason","different"),f.token,409);
        long active=number(body,"expectedCount");long held=f.source.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment k JOIN submission s ON s.scope_id=k.source_scope_id "
            +"WHERE s.id=? AND (k.submission_id=s.id AND k.state='REVIEW_RESERVED' OR k.state='SOURCE_RESERVED' AND k.source_evidence_set_id=UUID_TO_BIN(?))",
            Long.class,Ids.bytes(id),text(request,"sourceEvidenceSetId"));assertThat(held).isEqualTo(active);
        Files.writeString(directory.resolve("local-stop-receipt.json"),canonical(receipt));
    }
    private void noPayload() {
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=?",Long.class,Ids.bytes(id))).isZero();
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM submitted_capture WHERE submission_id=?",Long.class,Ids.bytes(id))).isZero();
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM submitted_event WHERE submission_id=?",Long.class,Ids.bytes(id))).isZero();
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM submission_chunk WHERE submission_id=?",Long.class,Ids.bytes(id))).isZero();
    }
    private void manifestFault(ObjectNode request) throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("local-manifest-fault"));var original=sql.snapshot("original");
        f.source.jdbc.update("UPDATE submission_upload SET byte_count=byte_count+1 WHERE submission_id=?",Ids.bytes(id));
        try {var before=sql.snapshot("before");send(request,f.token,409);assertThat(sql.snapshot("after")).isEqualTo(before);}
        finally {f.source.jdbc.update("UPDATE submission_upload SET byte_count=byte_count-1 WHERE submission_id=?",Ids.bytes(id));}
        assertThat(sql.snapshot("restored")).isEqualTo(original);
    }
    private void missingOriginal(ObjectNode request) throws Exception {
        String file=f.source.jdbc.queryForObject("SELECT o.file_key FROM source_evidence_set e JOIN source_evidence_item i ON i.set_id=e.id "
            +"JOIN source_evidence_object o ON o.id=i.object_id WHERE e.id=UUID_TO_BIN(?) AND o.kind='LOCAL_FROZEN_PAYLOAD'",String.class,text(request,"sourceEvidenceSetId"));
        Path raw=directory.getParent().resolve("storage").resolve(file),saved=raw.resolveSibling(raw.getFileName()+".local-retained");
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("local-missing-original"));var before=sql.snapshot("before");
        Files.move(raw,saved);try {send(request,f.token,409);}finally {Files.move(saved,raw);}
        assertThat(sql.snapshot("after")).isEqualTo(before);
    }
    private void tailFault(ObjectNode request) throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("local-stop-tail"));var before=sql.snapshot("before");
        var continued=new HandoverMixedContinuationSql(f.source.jdbc,directory.resolve("local-stop-tail"));var held=continued.snapshot("before");
        var local=new HandoverTemporaryLocalSql(f.source.jdbc,directory.resolve("local-stop-tail"));var transport=local.snapshot("before");
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(ApiErrors.class);
        var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();logger.addAppender(logs);
        f.source.jdbc.execute("CREATE TRIGGER local_stop_tail AFTER INSERT ON operation_event FOR EACH ROW BEGIN IF NEW.event_type='SUBMISSION_ABORTED' AND NEW.entity_id=UUID_TO_BIN('"+id+"') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D06_LOCAL_STOP_TAIL'; END IF; END");
        try {send(request,f.token,500);HandoverTemporaryIntakeChecks.requireFault(logs.list,"D06_LOCAL_STOP_TAIL");}
        finally {f.source.jdbc.execute("DROP TRIGGER local_stop_tail");logger.detachAppender(logs);logs.stop();}
        assertThat(sql.snapshot("after")).isEqualTo(before);assertThat(continued.snapshot("after")).isEqualTo(held);
        assertThat(local.snapshot("after")).isEqualTo(transport);
        Files.writeString(directory.resolve("local-stop-tail/exact-sql-fault.txt"),"D06_LOCAL_STOP_TAIL SQLSTATE=45000 code=1644; scope/slot/owners and all facts rollback\n");
    }
    private ObjectNode request() throws Exception {return (ObjectNode)parse(Files.readAllBytes(directory.resolve("android-handover-stop-request.json")));}
    private byte[] send(ObjectNode body,String token,int status) throws Exception {
        return f.source.post("/temporary-orders/handover-submissions/"+id+"/stop",body,token,status);
    }
}
