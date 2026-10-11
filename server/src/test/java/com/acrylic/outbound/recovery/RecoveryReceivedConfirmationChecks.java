package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** The original source and actual recorded LOGIN reach one immutable, narrowly signed confirmation. */
public final class RecoveryReceivedConfirmationChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        RecoveryReceivedSessionChecks.verify(app,attempt,original);
        var jdbc=app.getBean(JdbcTemplate.class); var input=RecoveryReceivedSessionRestart.reload(jdbc,attempt,original);
        RecoveryReceivedDownloadChecks.authenticate(app,attempt);
        HandoverRecoveredTemporaryDownload.Result document;
        try { document=app.getBean(HandoverRecoveredTemporaryDownload.class).read(input.session(),input.request(),"confirmation-original"); }
        finally { SecurityContextHolder.clearContext(); }
        var command=command(input.session(),document);
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        RecoveryConfirmationNegativeChecks.verify(app,attempt,input,command);
        RecoveryConfirmationTailChecks.verify(app,attempt,input,command);
        assertThat(snapshots.capture()).isEqualTo(before);
        byte[] response=concurrent(app,attempt,input,command);
        var after=snapshots.capture(); conserved(before,after);
        // The client discards the committed response and retries the same durable bytes.
        assertThat(confirm(app,attempt,input,command)).isEqualTo(response); assertThat(snapshots.capture()).isEqualTo(after);
        assertThatThrownBy(()->jdbc.update("UPDATE recovery_handover_confirmation SET recorded_at=UTC_TIMESTAMP(3)"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM recovery_handover_confirmation")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(snapshots.capture()).isEqualTo(after);
        Files.write(attempt.resolve("confirmation-command.json"),encode(command)); Files.write(attempt.resolve("confirmation-receipt.json"),response);
        Files.write(attempt.resolve("confirmation-manifest.json"),document.manifest()); Files.write(attempt.resolve("confirmation-payload.json"),document.recoveryPackage());
        Files.writeString(attempt.resolve("confirmation-before.json"),before.toPrettyString()); Files.writeString(attempt.resolve("confirmation-after.json"),after.toPrettyString());
        Files.write(attempt.resolve("confirmation-result.json"),encode(SubmissionJson.object().put("sourcePid",ProcessHandle.current().pid())
            .put("originalSourceAndMapping",true).put("actualLogin",true).put("sameKeyReplay",true).put("lostResponseRetry",true)
            .put("lastSqlRollback",true).put("originalRowTailRollback",true).put("identityTailRollback",true).put("materialTailRollback",true)
            .put("immutableConfirmation",true).put("fullOriginalRowsConserved",true).put("mayResumeScanning",false).put("localDeletionAllowed",false)));
    }
    static ObjectNode command(RecoveryRecipientSession session,HandoverRecoveredTemporaryDownload.Result document) {
        var q=session.qualification().facts(); var manifest=SubmissionJson.parse(document.manifest());
        return SubmissionJson.object().put("commandVersion",1).put("commandType","RECOVERED_TEMP_HANDOVER_CONFIRM")
            .put("commandId",Ids.next().toString()).put("caseId",text(q,"caseId")).put("qualificationId",text(q,"qualificationId"))
            .put("transportRecoveryEpoch",text(q.path("control"),"transportRecoveryEpoch"))
            .put("sourceRecoveryEpoch",text(q.path("source"),"sourceRecoveryEpoch")).put("expectedCaseVersion","4")
            .put("qualificationSha256",session.qualification().sha256()).put("recipientSessionSha256",session.sha256())
            .put("manifestSha256",sha256(document.manifest())).put("preparedTicketSha256",text(manifest,"preparedTicketSha256"))
            .put("payloadSha256",sha256(document.recoveryPackage())).put("checklistSha256",SubmissionJson.sha(manifest.path("checklist")))
            .put("physicalConfirmed",true);
    }
    static byte[] confirm(WebApplicationContext app,Path attempt,RecoveryReceivedSessionRestart.Reloaded input,JsonNode command) throws Exception {
        RecoveryReceivedDownloadChecks.authenticate(app,attempt);
        try { return app.getBean(HandoverRecoveredTemporaryConfirmation.class).confirm(encode(command),
            new RecoveryRecipientDownloadInput.Context(input.session(),input.request(),()->{}),"recovered-confirmation"); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private static byte[] concurrent(WebApplicationContext app,Path attempt,RecoveryReceivedSessionRestart.Reloaded input,JsonNode command) throws Exception {
        var barrier=new CyclicBarrier(2); var outcomes=new ArrayList<String>(); var responses=new ArrayList<byte[]>();
        try(var workers=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<byte[]>>();
            for(int i=0;i<2;i++) calls.add(workers.submit(()->{barrier.await(10,TimeUnit.SECONDS);
                try { return confirm(app,attempt,input,command); }
                catch(RecoveryControlException error) { assertThat(error.getMessage()).isEqualTo("CONTROL_COORDINATOR_BUSY"); return null; }
            }));
            for(var call:calls) { byte[] bytes=call.get(90,TimeUnit.SECONDS); outcomes.add(bytes==null?"CONTROL_COORDINATOR_BUSY":"COMMITTED"); if(bytes!=null) responses.add(bytes); }
        }
        assertThat(responses).isNotEmpty(); for(byte[] bytes:responses) assertThat(bytes).isEqualTo(responses.getFirst());
        Files.write(attempt.resolve("confirmation-first-concurrent.json"),encode(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(outcomes)));
        return responses.getFirst();
    }
    private static void conserved(JsonNode before,JsonNode after) {
        assertThat(after.path("tables").size()).isEqualTo(before.path("tables").size());
        before.path("tables").fields().forEachRemaining(e->{if(!e.getKey().equals("recovery_handover_confirmation"))
            assertThat(after.path("tables").path(e.getKey())).as(e.getKey()).isEqualTo(e.getValue());});
        assertThat(after.path("tables").path("recovery_handover_confirmation").path("rows").size()).isEqualTo(1);
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
    }
    static byte[] encode(JsonNode value) { return SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8); }
    private RecoveryReceivedConfirmationChecks() { }
}
