package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.HandoverRecoveredRecipientSession;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual HTTP receiving identity is audited inside the closed source-preserving transaction. */
public final class RecoveryReceivedSessionChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var f=new RecoveryRecipientSessionFixture(app,attempt,original); var signed=f.sign(f.body);
        var grant=RecoveryRecipientSession.verify(signed,f.qualification);
        var snapshots=new E2aMigrationSnapshot(f.fixture.enrollment.jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        RecoverySessionRejectionChecks.verify(f,signed,grant); RecoverySessionTailChecks.verify(f,signed,grant);
        f.authenticate();
        try { assertThatThrownBy(()->app.getBean(com.acrylic.outbound.handover.HandoverRecoveredTemporaryDownload.class)
            .read(grant,f.fixture.enrollment.setup.request(),"missing-session-admission")).hasMessage("RECOVERY_SESSION_UNVERIFIED_ADDITIONS"); }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        assertThat(snapshots.capture()).isEqualTo(before);
        concurrent(f,grant,attempt); var result=f.admit(grant);
        assertThat(result.signedSha256()).isEqualTo(signed.independentSha256()); assertThat(result.ordinaryWritesAllowed()).isFalse();
        var after=snapshots.capture(); assertThat(f.admit(grant).signedSha256()).isEqualTo(result.signedSha256());
        assertThat(snapshots.capture()).isEqualTo(after); conserved(before,after);
        immutable(f,grant); assertThat(snapshots.capture()).isEqualTo(after);
        save(f,signed,attempt,before,after);
    }
    private static void conserved(JsonNode before,JsonNode after) {
        assertThat(after.path("tables").size()).isEqualTo(before.path("tables").size());
        before.path("tables").fields().forEachRemaining(e->{if(!e.getKey().equals("recovery_recipient_session"))
            assertThat(after.path("tables").path(e.getKey())).as(e.getKey()).isEqualTo(e.getValue());});
        assertThat(after.path("tables").path("recovery_recipient_session").path("rows").size()).isEqualTo(1);
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
    }
    private static void immutable(RecoveryRecipientSessionFixture f,RecoveryRecipientSession grant) throws Exception {
        var changed=f.body.deepCopy().put("expiresAt",java.time.Instant.parse(f.body.path("expiresAt").asText()).minusSeconds(1).toString());
        var conflict=RecoveryRecipientSession.verify(f.sign(changed),f.qualification);
        assertThatThrownBy(()->f.admit(conflict)).hasMessage("RECOVERY_SESSION_IDEMPOTENCY_CONFLICT");
        var jdbc=f.fixture.enrollment.jdbc;
        assertThatThrownBy(()->jdbc.update("UPDATE recovery_recipient_session SET recorded_at=UTC_TIMESTAMP(3)"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM recovery_recipient_session")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    private static void concurrent(RecoveryRecipientSessionFixture f,RecoveryRecipientSession grant,Path attempt) throws Exception {
        var barrier=new CyclicBarrier(2); var outcomes=new ArrayList<String>();
        try(var workers=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<String>>();
            for(int i=0;i<2;i++) calls.add(workers.submit(()->{
                barrier.await(10,TimeUnit.SECONDS);
                try { f.admit(grant); return "COMMITTED"; }
                catch(RecoveryControlException error) { return error.getMessage(); }
            }));
            for(var call:calls) outcomes.add(call.get(60,TimeUnit.SECONDS));
        }
        assertThat(outcomes).contains("COMMITTED").allMatch(v->Set.of("COMMITTED","CONTROL_COORDINATOR_BUSY").contains(v));
        Files.writeString(attempt.resolve("session-first-concurrent.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(outcomes));
    }
    private static void save(RecoveryRecipientSessionFixture f,RecoveryRecipientSession.Request request,Path attempt,JsonNode before,JsonNode after) throws Exception {
        Files.writeString(attempt.resolve("session-before.json"),before.toPrettyString()); Files.writeString(attempt.resolve("session-after.json"),after.toPrettyString());
        Files.writeString(attempt.resolve("session-input-file.txt"),request.file().toString());
        var input=SubmissionJson.object().put("sourcePid",ProcessHandle.current().pid()).put("sessionFile",request.file().toString()).put("sessionSha256",request.independentSha256());
        Files.write(attempt.resolve("session-restart-input.json"),RecoveryControlJson.encode(input)); Files.writeString(attempt.resolve("session-restart-input.sha256"),hash(input));
        Files.write(attempt.resolve("session-checks.json"),RecoveryControlJson.encode(SubmissionJson.object().put("actualHttpLogin",true)
            .put("pinScopeActorExpiryRefused",true).put("lastSqlRollback",true).put("originalRowTailRollback",true)
            .put("sessionTailRollback",true).put("deviceTimeTailRollback",true).put("identityTailRollback",true)
            .put("materialTailRollback",true).put("sameKeyReplay",true).put("signedConflictRefused",true).put("immutableAudit",true)));
    }
    private RecoveryReceivedSessionChecks() { }
}
