package com.acrylic.outbound.inventory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.*;
import ch.qos.logback.core.read.ListAppender;
import com.acrylic.outbound.common.ApiErrors;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.LoggerFactory;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Same actual Android frozen input; closed routing, transactional tail failures and racing retries. */
final class HandoverTemporaryIntakeChecks {
    private final HandoverTemporaryTargetFixture f;private final Path directory;
    private final ObjectNode body;private final AtomicInteger calls=new AtomicInteger();
    HandoverTemporaryIntakeChecks(HandoverTemporaryTargetFixture f,Path evidence) throws Exception {
        this.f=f;directory=Files.createDirectory(evidence.resolve("intake-"+UUID.randomUUID()));
        body=(ObjectNode)parse(Files.readAllBytes(evidence.resolve("android-handover-payload.json")));
    }
    void disabled() throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("disabled-sql"));
        var before=sql.snapshot("before");post(body,404,null);assertThat(sql.snapshot("after")).isEqualTo(before);
        assertThat(registrations()).isZero();
    }
    void verify() throws Exception {
        assertThat(registrations()).isZero();rejectIncomplete();fault("order_sync_outbox","SUBMISSION_REVIEW_REQUIRED");
        fault("operation_event","SUBMISSION_REVIEW_REQUIRED");racingRetries();
        assertThat(registrations()).isEqualTo(1);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=?",Long.class,
            Ids.bytes(uuid(body,"submissionId")))).isEqualTo(1);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=? AND state='REVIEW_RESERVED'",Long.class,
            Ids.bytes(uuid(body,"submissionId")))).isEqualTo(number(body,"expectedCount"));
        Files.writeString(directory.resolve("verified.marker"),"Actual HTTP: default closed, outbox and last audit SQL rollback, concurrent identical retries, alternate submission rejected; unique registration and retained owners.\n");
    }
    private long registrations() {
        return f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_submission WHERE submission_id=?",Long.class,
            Ids.bytes(uuid(body,"submissionId")));
    }
    private void rejectIncomplete() throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("incomplete-sql"));var before=sql.snapshot("before");
        var reduced=body.deepCopy();array(reduced,"scans").remove(0);post(reduced,400,"INVALID_SUBMISSION");
        var unfrozen=body.deepCopy();var events=array(unfrozen,"events");events.remove(events.size()-1);
        post(unfrozen,400,"INVALID_SUBMISSION");
        var rewritten=body.deepCopy();((ObjectNode)array(rewritten,"captures").get(0)).put("rawInput","changed");
        post(rewritten,400,"INVALID_SUBMISSION");assertThat(sql.snapshot("after")).isEqualTo(before);
        assertThat(registrations()).isZero();
    }
    private void fault(String table,String event) throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve(table+"-sql"));
        var before=sql.snapshot("before");var logger=(Logger)LoggerFactory.getLogger(ApiErrors.class);
        var logs=new ListAppender<ILoggingEvent>();logs.start();logger.addAppender(logs);
        var message="D06_INTAKE_TAIL_"+table;
        f.source.jdbc.execute("CREATE TRIGGER d06_intake_tail AFTER INSERT ON "+table+" FOR EACH ROW BEGIN "
            +"IF NEW.event_type='"+event+"' AND EXISTS(SELECT 1 FROM temporary_handover_submission WHERE submission_id=UUID_TO_BIN('"
            +uuid(body,"submissionId")+"')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='"+message+"'; END IF; END");
        try {post(body,500,"INTERNAL_ERROR");requireFault(logs.list,message);}
        finally {f.source.jdbc.execute("DROP TRIGGER d06_intake_tail");logger.detachAppender(logs);logs.stop();}
        assertThat(sql.snapshot("after")).isEqualTo(before);assertThat(registrations()).isZero();
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_completion WHERE submission_id=?",Long.class,
            Ids.bytes(uuid(body,"submissionId")))).isZero();
        Files.writeString(directory.resolve(table+"-fault.txt"),message+" SQLSTATE=45000 code=1644; full business snapshot and registration unchanged\n");
    }
    static void requireFault(List<ILoggingEvent> logs,String message) {
        for(var log:logs)if(log.getThrowableProxy() instanceof ThrowableProxy proxy)
            for(Throwable cause=proxy.getThrowable();cause!=null;cause=cause.getCause())
                if(cause instanceof SQLException sql && message.equals(sql.getMessage())) {
                    assertThat(sql.getSQLState()).isEqualTo("45000");assertThat(sql.getErrorCode()).isEqualTo(1644);return;
                }
        fail("Missing exact transactional-tail SQL fault: "+message);
    }
    private void racingRetries() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        Callable<byte[]> original=()->{ready.countDown();assertThat(start.await(30,TimeUnit.SECONDS)).isTrue();return post(body,200,null);};
        try {
            var first=pool.submit(original);var second=pool.submit(original);
            assertThat(ready.await(30,TimeUnit.SECONDS)).isTrue();start.countDown();
            var a=parse(first.get(45,TimeUnit.SECONDS));var b=parse(second.get(45,TimeUnit.SECONDS));
            assertThat(a.path("data")).isEqualTo(b.path("data"));
            var alternate=body.deepCopy().put("submissionId",UUID.randomUUID().toString());
            post(alternate,409,"TEMP_HANDOVER_BINDING_CHANGED");
            post(body.deepCopy().put("shortShipmentReason","different"),409,"IDEMPOTENCY_CONTENT_MISMATCH");
        } finally {start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    private byte[] post(ObjectNode payload,int status,String code) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/temporary-orders/handover-submissions"))
            .timeout(java.time.Duration.ofSeconds(30)).header("Authorization","Bearer "+f.token)
            .header("X-Server-Recovery-Epoch","1").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(canonical(payload))).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofByteArray());var call=calls.incrementAndGet();
        Files.write(directory.resolve("response-"+call+".json"),response.body());
        Files.writeString(directory.resolve("request-"+call+".json"),canonical(payload));
        assertThat(response.statusCode()).as(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(status);
        if(code!=null)assertThat(parse(response.body()).path("code").asText()).isEqualTo(code);
        return response.body();
    }
}
