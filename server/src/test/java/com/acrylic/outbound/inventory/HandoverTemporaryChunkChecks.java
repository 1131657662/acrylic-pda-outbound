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
import java.util.*;
import org.slf4j.LoggerFactory;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Real large Android bytes; only transport faults are injected, no frozen facts are fabricated. */
final class HandoverTemporaryChunkChecks {
    private final HandoverTemporaryTargetFixture f;private final Path directory;
    private final byte[] bytes;private final ObjectNode body,manifest;private final UUID id;private final String base;
    private final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
    HandoverTemporaryChunkChecks(HandoverTemporaryTargetFixture f,Path evidence) throws Exception {
        this.f=f;directory=Files.createDirectory(evidence.resolve("chunks-"+UUID.randomUUID()));
        bytes=Files.readAllBytes(evidence.resolve("android-handover-payload.json"));body=(ObjectNode)parse(bytes);
        assertThat(bytes.length).isGreaterThan(1048576);id=uuid(body,"submissionId");
        base="/temporary-orders/handover-submissions/"+id;manifest=object();
        for(String field:List.of("schemaVersion","submissionId","source","assignmentId","continuationId","authorizationId",
            "freezeDeviceId","workRound","sourceRecoveryEpoch","temporaryHandover"))manifest.set(field,body.get(field));
        manifest.put("payloadSha256",sha(body)).put("byteCount",Integer.toString(bytes.length)).put("chunkCount",Integer.toString(count()));
        Files.write(directory.resolve("manifest.json"),canonical(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    void disabled() throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("disabled-sql"));var before=sql.snapshot("before");
        send(base+"/upload",json(manifest),f.token,404);send(base+"/chunks/0",part(0),f.token,404);
        send(base+"/finalize",new byte[0],f.token,404);assertThat(sql.snapshot("after")).isEqualTo(before);
    }
    void begin() throws Exception {
        send("/temporary-orders/handover-submissions",bytes,f.token,413);
        send(base+"/upload",json(manifest),f.source.otherDeviceToken,404);
        send(base+"/upload",json(manifest.deepCopy().put("byteCount","33554433").put("chunkCount","129")),f.token,400);
        send(base+"/upload",json(manifest),f.token,200);send(base+"/chunks/0",part(0),f.token,200);
        concurrentFirstChunk();
        byte[] changed=part(0);changed[0]^=1;send(base+"/chunks/0",changed,f.token,409);
        send(base+"/chunks/1",new byte[1],f.token,400);send(base+"/finalize",new byte[0],f.token,400);
        send(base+"/upload",json(manifest.deepCopy().put("payloadSha256","f".repeat(64))),f.token,409);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM submission_chunk WHERE submission_id=?",Long.class,Ids.bytes(id))).isEqualTo(1);
        assertThat(f.source.jdbc.queryForObject("SELECT status FROM submission WHERE id=?",String.class,Ids.bytes(id))).isEqualTo("RECEIVING");
        Files.writeString(directory.resolve("begun.marker"),"Original manifest and one durable chunk; missing/wrong/alternate chunks and foreign device rejected.\n");
    }
    void finish() throws Exception {
        send(base+"/upload",json(manifest),f.token,200);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM submission_chunk WHERE submission_id=?",Long.class,Ids.bytes(id))).isEqualTo(1);
        for(int index=1;index<count();index++)send(base+"/chunks/"+index,part(index),f.token,200);
        wrongStoredLength();lastSqlFailure();
        assertThat(f.source.jdbc.queryForObject("SELECT status FROM submission WHERE id=?",String.class,Ids.bytes(id))).isEqualTo("RECEIVING");
        Files.writeString(directory.resolve("ready-for-android.marker"),"Restart and final SQL fault retain RECEIVING; actual Android must resend chunks and finalize.\n");
    }
    void accepted() throws Exception {
        var accepted=parse(send(base+"/finalize",new byte[0],f.token,200)).path("data");
        assertThat(accepted.path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(parse(send(base+"/finalize",new byte[0],f.token,200)).path("data")).isEqualTo(accepted);
        var row=f.source.jdbc.queryForMap("SELECT payload_sha256,byte_count,file_key FROM submission_payload WHERE submission_id=?",Ids.bytes(id));
        assertThat(row.get("payload_sha256")).isEqualTo(sha(body));assertThat(((Number)row.get("byte_count")).intValue()).isEqualTo(bytes.length);
        assertThat(Files.readAllBytes(f.source.directory.getParent().resolve("storage").resolve(row.get("file_key").toString()))).isEqualTo(bytes);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_submission WHERE submission_id=?",Long.class,Ids.bytes(id))).isEqualTo(1);
        Files.writeString(directory.resolve("finished.marker"),"Actual Android finalized original bytes; server replay is idempotent with one immutable registration.\n");
    }
    private void concurrentFirstChunk() throws Exception {
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<byte[]> call=()->{gate.await();return send(base+"/chunks/0",part(0),f.token,200);};
            var first=pool.submit(call);var second=pool.submit(call);gate.countDown();
            assertThat(parse(first.get()).path("data")).isEqualTo(parse(second.get()).path("data"));
        }
        Files.writeString(directory.resolve("concurrent-first-chunk.marker"),"Two concurrent live HTTP sends of identical original part 0 return one durable chunk.\n");
    }
    private void wrongStoredLength() throws Exception {
        f.source.jdbc.update("UPDATE submission_chunk SET byte_count=byte_count-1 WHERE submission_id=? AND chunk_index=0",Ids.bytes(id));
        try {send(base+"/finalize",new byte[0],f.token,400);}
        finally {f.source.jdbc.update("UPDATE submission_chunk SET byte_count=byte_count+1 WHERE submission_id=? AND chunk_index=0",Ids.bytes(id));}
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=?",Long.class,Ids.bytes(id))).isZero();
    }
    private void lastSqlFailure() throws Exception {
        var sql=new TemporaryDispositionSqlChecks(f.source.jdbc,directory.resolve("last-sql"));var before=sql.snapshot("before");
        var logger=(Logger)LoggerFactory.getLogger(ApiErrors.class);var logs=new ListAppender<ILoggingEvent>();logs.start();logger.addAppender(logs);
        String message="D06_HANDOVER_CHUNK_LAST_SQL";
        f.source.jdbc.execute("CREATE TRIGGER handover_chunk_tail AFTER INSERT ON operation_event FOR EACH ROW BEGIN IF NEW.event_type='SUBMISSION_REVIEW_REQUIRED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='"+message+"'; END IF; END");
        try {send(base+"/finalize",new byte[0],f.token,500);HandoverTemporaryIntakeChecks.requireFault(logs.list,message);}
        finally {f.source.jdbc.execute("DROP TRIGGER handover_chunk_tail");logger.detachAppender(logs);logs.stop();}
        assertThat(sql.snapshot("after")).isEqualTo(before);
        assertThat(f.source.jdbc.queryForObject("SELECT COUNT(*) FROM submission_chunk WHERE submission_id=?",Long.class,Ids.bytes(id))).isEqualTo(count());
        Files.writeString(directory.resolve("exact-sql-fault.txt"),message+" SQLSTATE=45000 code=1644; business unchanged and all chunks retained\n");
    }
    private int count() {return (bytes.length+262143)/262144;}
    private byte[] part(int index) {return Arrays.copyOfRange(bytes,index*262144,Math.min(bytes.length,(index+1)*262144));}
    private byte[] json(ObjectNode value) {return canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    private byte[] send(String path,byte[] requestBytes,String token,int status) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda"+path)).timeout(java.time.Duration.ofSeconds(45))
            .header("Authorization","Bearer "+token).header("X-Server-Recovery-Epoch","1").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes)).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofByteArray());int call=calls.incrementAndGet();
        Files.write(directory.resolve("request-"+call+".bin"),requestBytes);Files.write(directory.resolve("response-"+call+".json"),response.body());
        synchronized(this) {Files.writeString(directory.resolve("http.tsv"),call+"\t"+path+"\t"+response.statusCode()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        assertThat(response.statusCode()).as(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(status);return response.body();
    }
}
