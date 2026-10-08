package com.acrylic.outbound.inventory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.*;
import ch.qos.logback.core.read.ListAppender;
import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Actual receiving-device E2A roots and STOP, with a transaction-tail fault before any success. */
final class HandoverTemporaryReceivingStopChecks {
    private final HandoverTemporaryTargetFixture f;private final Path evidence,directory;private final UUID id;
    private int calls;
    HandoverTemporaryReceivingStopChecks(HandoverTemporaryTargetFixture f,Path evidence) throws Exception {
        this.f=f;this.evidence=evidence;directory=Files.createDirectory(evidence.resolve("stop-checks-"+UUID.randomUUID()));
        id=uuid(parse(Files.readAllBytes(evidence.resolve("android-handover-payload.json"))),"submissionId");
    }
    void disabled() throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("disabled"));var before=sql.snapshot("before");
        send(object(),f.token,404);assertThat(sql.snapshot("after")).isEqualTo(before);
    }
    void before() throws Exception {
        var request=request();var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("proof-rejections"));var before=sql.snapshot("before");
        send(request,f.source.otherDeviceToken,404);
        send(request.deepCopy().put("verificationId",UUID.randomUUID().toString()),f.token,409);
        var mixed=request.deepCopy();mixed.set("temporaryContinuation",object());send(mixed,f.token,400);
        var changed=request.deepCopy();((ObjectNode)changed.path("temporaryHandover")).put("ticketId",UUID.randomUUID().toString());
        send(changed,f.token,400);assertThat(sql.snapshot("after")).isEqualTo(before);
        lastSql(request);
    }
    void after() throws Exception {
        var request=request();byte[] receipt=send(request,f.token,200);
        assertThat(parse(receipt).path("data").path("status").asText()).isEqualTo("ABORTED");
        assertThat(parse(send(request,f.token,200)).path("data")).isEqualTo(parse(receipt).path("data"));
        send(request.deepCopy().put("reason","different"),f.token,409);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=? AND state='REVIEW_RESERVED'",Long.class,Ids.bytes(id)))
            .isEqualTo(number(parse(Files.readAllBytes(evidence.resolve("android-handover-payload.json"))),"expectedCount"));
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_completion WHERE submission_id=?",Long.class,Ids.bytes(id))).isZero();
        Files.write(directory.resolve("server-handover-stop.json"),receipt);
        var original=evidence.resolve("server-handover-stop.json");
        if(Files.exists(original))assertThat(parse(Files.readAllBytes(original)).path("data")).isEqualTo(parse(receipt).path("data"));
        else Files.write(original,receipt);
        Files.writeString(directory.resolve("verified.marker"),"Actual independent receiving STOP, original replay and owners kept; no completion or resumed work.\n");
    }
    private void lastSql(ObjectNode request) throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("last-sql"));var before=sql.snapshot("before");
        var logger=(Logger)LoggerFactory.getLogger(ApiErrors.class);var logs=new ListAppender<ILoggingEvent>();logs.start();logger.addAppender(logs);
        String message="D06_HANDOVER_STOP_LAST_SQL";
        f.source.jdbc.execute("CREATE TRIGGER handover_stop_tail AFTER INSERT ON operation_event FOR EACH ROW BEGIN IF NEW.event_type='SUBMISSION_ABORTED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='"+message+"'; END IF; END");
        try {send(request,f.token,500);HandoverTemporaryIntakeChecks.requireFault(logs.list,message);}
        finally {f.source.jdbc.execute("DROP TRIGGER handover_stop_tail");logger.detachAppender(logs);logs.stop();}
        assertThat(sql.snapshot("after")).isEqualTo(before);
        Files.writeString(directory.resolve("exact-sql-fault.txt"),message+" SQLSTATE=45000 code=1644; all business/evidence/owners unchanged\n");
    }
    private ObjectNode request() throws Exception {return (ObjectNode)parse(Files.readAllBytes(evidence.resolve("android-handover-stop-request.json")));}
    private byte[] send(ObjectNode body,String token,int status) throws Exception {
        byte[] bytes=canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/temporary-orders/handover-submissions/"+id+"/stop"))
            .timeout(java.time.Duration.ofSeconds(45))
            .header("Authorization","Bearer "+token).header("X-Server-Recovery-Epoch","1").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofByteArray());int call=++calls;
        Files.write(directory.resolve("request-"+call+".json"),bytes);Files.write(directory.resolve("response-"+call+".json"),response.body());
        assertThat(response.statusCode()).as(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(status);return response.body();
    }
}
