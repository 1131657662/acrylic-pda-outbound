package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionFiles;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="B4_RETENTION_VERIFY",matches="true")
class TemporaryRoundChunksHttpIT {
    @Test void chunkedContinuationRecoversOriginalBytesAndConfirmsOnce() throws Exception {
        try(var f=new TemporaryRoundConfirmationHttpFixture(false,false)) {
            TemporaryRoundChunkHttp.addInvalidInputs(f.body);var h=new TemporaryRoundChunkHttp(f);
            assertThat(h.bytes.length).isGreaterThan(1048576);
            rejectionsBeforeStart(f,h);var before=f.http.database.stock();
            h.send("/upload",h.manifestBytes(),"chunk-start");
            h.send("/chunks/0",h.chunk(0),"chunk-first");
            rejectionsAfterStart(f,h);assertThat(f.http.database.stock()).isEqualTo(before);
            h.send("/upload",h.manifestBytes(),"chunk-resume-original-manifest");
            concurrentFirstReplay(h);for(int i=1;i<h.count();i++)h.send("/chunks/"+i,h.chunk(i),"chunk-part-"+i);
            finalRollback(f,h);var accepted=parse(h.send("/finalize",new byte[0],"chunk-finalize").body()).path("data");
            assertThat(accepted.path("status").asText()).isEqualTo("REVIEW_REQUIRED");
            assertThat(f.http.app.getBean(SubmissionFiles.class).read(sha(f.body))).isEqualTo(h.bytes);
            assertThat(parse(h.send("/finalize",new byte[0],"chunk-finalize-replay").body()).path("data")).isEqualTo(accepted);
            f.acknowledge();byte[] receipt=f.http.accepted(f.command("NORMAL_CONFIRM"),"chunk-independent-confirm");
            new TemporaryRoundConfirmationState(f).completed(parse(receipt),4);
            assertThat(parse(h.send("/finalize",new byte[0],"chunk-terminal-finalize").body()).path("data").path("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(parse(h.send("/upload",h.manifestBytes(),"chunk-terminal-start").body()).path("data").path("status").asText()).isEqualTo("SUCCEEDED");
            var changed=h.manifest.deepCopy().put("byteCount",Integer.toString(h.bytes.length-1));
            h.send(h.base+"/upload",canonical(changed).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new TemporaryRoundChunkHttp.Request(f.http.token,"1","chunk-terminal-changed-length",400));
            Files.writeString(f.http.database.profile.attempt.resolve("test-completed.marker"),"Real continuation chunk HTTP/MySQL and confirmation; synthetic source/identity.\n");
        }
    }
    private void rejectionsBeforeStart(TemporaryRoundConfirmationHttpFixture f,TemporaryRoundChunkHttp h) throws Exception {
        var request=new TemporaryRoundChunkHttp.Request(f.http.otherToken,"1","chunk-wrong-person",404);
        h.send(h.base+"/upload",h.manifestBytes(),request);
        h.send(h.base+"/upload",h.manifestBytes(),new TemporaryRoundChunkHttp.Request(f.http.otherDeviceToken,"1","chunk-wrong-device",404));
        h.send(h.base+"/upload",h.manifestBytes(),new TemporaryRoundChunkHttp.Request(f.http.token,"2","chunk-wrong-epoch",409));
        var over=h.manifest.deepCopy().put("byteCount","33554433").put("chunkCount","129");
        h.send(h.base+"/upload",canonical(over).getBytes(java.nio.charset.StandardCharsets.UTF_8),new TemporaryRoundChunkHttp.Request(f.http.token,"1","chunk-oversize",400));
    }
    private void rejectionsAfterStart(TemporaryRoundConfirmationHttpFixture f,TemporaryRoundChunkHttp h) throws Exception {
        h.send(h.base+"/finalize",new byte[0],new TemporaryRoundChunkHttp.Request(f.http.token,"1","chunk-incomplete",400));
        byte[] different=h.chunk(0);different[0]='x';
        h.send(h.base+"/chunks/0",different,new TemporaryRoundChunkHttp.Request(f.http.token,"1","chunk-different-original",409));
        h.send(h.base+"/chunks/1",new byte[1],new TemporaryRoundChunkHttp.Request(f.http.token,"1","chunk-wrong-length",400));
        var other=h.manifest.deepCopy().put("payloadSha256","f".repeat(64));
        h.send(h.base+"/upload",canonical(other).getBytes(java.nio.charset.StandardCharsets.UTF_8),new TemporaryRoundChunkHttp.Request(f.http.token,"1","chunk-different-manifest",409));
    }
    private void concurrentFirstReplay(TemporaryRoundChunkHttp h) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var first=pool.submit(()->{start.await();return h.send("/chunks/0",h.chunk(0),"chunk-concurrent-a");});
            var second=pool.submit(()->{start.await();return h.send("/chunks/0",h.chunk(0),"chunk-concurrent-b");});
            start.countDown();assertThat(parse(first.get(40,TimeUnit.SECONDS).body()).path("data"))
                .isEqualTo(parse(second.get(40,TimeUnit.SECONDS).body()).path("data"));
        }
    }
    private void finalRollback(TemporaryRoundConfirmationHttpFixture f,TemporaryRoundChunkHttp h) throws Exception {
        var j=f.http.database.jdbc;var before=f.http.database.stock();
        j.execute("CREATE TRIGGER chunk_final_failure BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            +"IF NEW.event_type='SUBMISSION_REVIEW_REQUIRED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='chunk final audit failure'; END IF; END");
        try {
            h.send(h.base+"/finalize",new byte[0],new TemporaryRoundChunkHttp.Request(f.http.token,"1","chunk-final-audit-failure",500));
            assertThat(f.http.database.stock()).isEqualTo(before);
            assertThat(j.queryForObject("SELECT COUNT(*) FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",Integer.class,text(f.body,"submissionId"))).isZero();
            assertThat(j.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,text(f.body,"submissionId"))).isEqualTo("RECEIVING");
            assertThat(j.queryForObject("SELECT COUNT(*) FROM submission_chunk WHERE submission_id=UUID_TO_BIN(?)",Integer.class,text(f.body,"submissionId"))).isEqualTo(h.count());
        } finally { j.execute("DROP TRIGGER chunk_final_failure"); }
    }
}
