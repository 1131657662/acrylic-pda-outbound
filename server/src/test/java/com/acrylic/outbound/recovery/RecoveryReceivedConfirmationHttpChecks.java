package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual POST route consumes configured trust and the original bearer, preserving precise confirmation effects. */
public final class RecoveryReceivedConfirmationHttpChecks {
    public static void verify(WebApplicationContext app,Path attempt,boolean restart) throws Exception {
        var command=(ObjectNode)SubmissionJson.parse(Files.readAllBytes(attempt.resolve("confirmation-command.json")));
        var session=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("private/recipient-login-response.json"))).path("data");
        String token=session.path("accessToken").asText(),base="/handovers/"+text(command,"caseId")+"/recovered-confirmation";
        var request=new Request(base,token,command); String prefix=restart?"confirmation-http-restart":"confirmation-http";
        var snapshots=new E2aMigrationSnapshot(app.getBean(JdbcTemplate.class),attempt,attempt.resolve("storage")); var before=snapshots.capture();
        Path receiptFile=attempt.resolve("confirmation-receipt.json");
        if(!Files.exists(receiptFile)) RecoveryConfirmationHttpFirstChecks.verify(attempt,request,snapshots);
        before=snapshots.capture();
        byte[] expected=Files.readAllBytes(receiptFile);
        replay(attempt,request,expected,prefix);
        if(!restart) {
            negatives(attempt,request); concurrent(attempt,request,expected);
            RecoveryConfirmationHttpLostResponseChecks.verify(attempt,request,expected);
            RecoveryConfirmationHttpIdentityChecks.verify(app,attempt,request);
            RecoveryConfirmationHttpDeviceChecks.verify(app,attempt,request);
            RecoveryConfirmationHttpInputTailChecks.verify(app,attempt,command);
        }
        assertThat(snapshots.capture()).isEqualTo(before);
        Files.writeString(attempt.resolve(prefix+"-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve(prefix+"-after.json"),snapshots.capture().toPrettyString());
        save(attempt,prefix,restart);
    }
    private static void replay(Path attempt,Request request,byte[] expected,String prefix) throws Exception {
        var command=request.command();
        var response=post(request,command,text(command,"transportRecoveryEpoch"));
        assertThat(response.statusCode()).as("%s",new String(response.body(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(200);
        assertThat(response.body()).isEqualTo(expected); Files.write(attempt.resolve(prefix+"-receipt.json"),response.body());
        assertThat(post(new Request(request.path()+"?path=ignored&pin=ignored",request.token(),command),command,text(command,"transportRecoveryEpoch")).body()).isEqualTo(expected);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("Content-Length")).contains(Integer.toString(expected.length));
    }
    private static void save(Path attempt,String prefix,boolean restart) throws Exception {
        Files.write(attempt.resolve(prefix+"-result.json"),RecoveryControlJson.encode(SubmissionJson.object().put("pid",ProcessHandle.current().pid())
            .put("actualProductPost",true).put("originalBearerAndReceipt",true).put("databaseUnchanged",true).put("restart",restart)
            .put("requestTrustIgnored",true).put("mayResumeScanning",false).put("localDeletionAllowed",false)));
    }
    private static void negatives(Path attempt,Request request) throws Exception {
        var command=request.command(); String epoch=text(command,"transportRecoveryEpoch");
        refused(attempt,"missing-bearer",post(new Request(request.path(),null,command),command,epoch),401,"UNAUTHENTICATED");
        refused(attempt,"old-epoch",post(request,command,text(command,"sourceRecoveryEpoch")),409,"RECOVERED_CONFIRMATION_CHANGED");
        var changed=command.deepCopy().put("commandId",UUID.randomUUID().toString());
        refused(attempt,"changed-command",post(request,changed,epoch),409,"IDEMPOTENCY_CONTENT_MISMATCH");
        changed=command.deepCopy().put("caseId",UUID.randomUUID().toString());
        refused(attempt,"wrong-case",post(new Request("/handovers/"+text(changed,"caseId")+"/recovered-confirmation",request.token(),changed),changed,epoch),404,"HANDOVER_NOT_FOUND");
        refused(attempt,"unconfirmed",post(request,command.deepCopy().put("physicalConfirmed",false),epoch),400,"INVALID_RECOVERED_CONFIRMATION");
        refused(attempt,"changed-case-version",post(request,command.deepCopy().put("expectedCaseVersion","5"),epoch),409,"RECOVERED_CONFIRMATION_CHANGED");
        refused(attempt,"extra-field",post(request,command.deepCopy().put("mayResumeScanning",true),epoch),400,"INVALID_RECOVERED_CONFIRMATION");
        Path input=attempt.resolve("download-http-input.json"); byte[] original=Files.readAllBytes(input);
        try { Files.writeString(input,"bad configured confirmation input");
            refused(attempt,"tampered-input",post(request,command,epoch),503,"RECOVERY_DOWNLOAD_INPUT_PIN_MISMATCH"); }
        finally { Files.write(input,original); }
    }
    private static void concurrent(Path attempt,Request request,byte[] expected) throws Exception {
        var barrier=new CyclicBarrier(2); var outcomes=SubmissionJson.object().putArray("outcomes");
        try(var workers=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<HttpResponse<byte[]>>>();
            for(int i=0;i<2;i++) calls.add(workers.submit(()->{barrier.await(10,TimeUnit.SECONDS);return post(request,request.command(),text(request.command(),"transportRecoveryEpoch"));}));
            for(var call:calls) {
                var response=call.get(90,TimeUnit.SECONDS); if(response.statusCode()==200) assertThat(response.body()).isEqualTo(expected);
                else { assertThat(response.statusCode()).isEqualTo(503); assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("CONTROL_COORDINATOR_BUSY"); }
                outcomes.addObject().put("status",response.statusCode()).put("bodySha256",sha256(response.body()));
            }
        }
        assertThat(outcomes).anyMatch(v->v.path("status").asInt()==200); Files.write(attempt.resolve("confirmation-http-concurrent.json"),RecoveryControlJson.encode(outcomes));
    }
    static HttpResponse<byte[]> post(Request request,ObjectNode command,String epoch) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda"+request.path())).timeout(Duration.ofSeconds(45))
            .header("Content-Type","application/json").header("X-Server-Recovery-Epoch",epoch)
            .POST(HttpRequest.BodyPublishers.ofByteArray(RecoveryReceivedConfirmationChecks.encode(command)));
        if(request.token()!=null) builder.header("Authorization","Bearer "+request.token());
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    static void refused(Path attempt,String name,HttpResponse<byte[]> response,int status,String code) throws Exception {
        Files.write(attempt.resolve("confirmation-http-"+name+".json"),response.body());
        assertThat(response.statusCode()).as(name).isEqualTo(status); assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(code);
    }
    record Request(String path,String token,ObjectNode command) { }
    private RecoveryReceivedConfirmationHttpChecks() { }
}
