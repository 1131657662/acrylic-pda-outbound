package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.TemporaryStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual new qualification, with full original rows and first-round isolation preserved. */
public final class RecoveryReceivedQualificationChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var f=new RecoveryQualificationFixture(app,attempt,original);
        var snapshots=new E2aMigrationSnapshot(f.enrollment.jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        var request=f.sign(f.body); var qualification=RecoveryTemporaryQualification.verify(request,f.admission);
        negatives(f); lastSql(f,qualification); RecoveryQualificationTailChecks.verify(f,qualification);
        assertThat(snapshots.capture()).isEqualTo(before);
        var result=f.issue(qualification); assertThat(result.signedSha256()).isEqualTo(request.independentSha256());
        assertThat(result.signedBytes()).isEqualTo(Files.readAllBytes(request.file()));
        assertThat(result.firstRoundAllowed()).isFalse(); assertThat(result.mayResumeScanning()).isFalse();
        assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
        assertThatThrownBy(()->app.getBean(TemporaryStore.class).get(uuid(f.body,"qualificationId")))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class).hasMessageContaining("临时准备资料不存在");
        var after=snapshots.capture(); conserved(before,after);
        assertThat(f.issue(qualification).signedBytes()).isEqualTo(result.signedBytes()); concurrent(f,qualification);
        var different=f.body.deepCopy().put("expiresAt",Instant.parse(text(f.body,"expiresAt")).minusSeconds(1).toString());
        var conflict=RecoveryTemporaryQualification.verify(f.sign(different),f.admission);
        assertThatThrownBy(()->f.issue(conflict)).hasMessage("RECOVERY_QUALIFICATION_IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(()->f.enrollment.jdbc.update("UPDATE recovery_recipient_qualification SET recorded_at=UTC_TIMESTAMP(3)"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->f.enrollment.jdbc.update("DELETE FROM recovery_recipient_qualification"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(snapshots.capture()).isEqualTo(after); f.save(attempt,request);
        Files.writeString(attempt.resolve("qualification-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("qualification-after.json"),after.toPrettyString());
        Files.write(attempt.resolve("qualification-statement.json"),RecoveryControlJson.encode(qualification.facts()));
        Files.write(attempt.resolve("qualification-checks.json"),RecoveryControlJson.encode(SubmissionJson.object()
            .put("lastSqlRollback",true).put("signatureTailRollback",true).put("originalRowTailRollback",true)
            .put("sameKeyReplay",true).put("signedConflictRefused",true).put("ordinaryAuthorizationMissing",true)));
    }
    private static void negatives(RecoveryQualificationFixture f) throws Exception {
        for(var change:Map.of("purpose","TEMP_WORK","scopeId",UUID.randomUUID().toString(),"admissionSha256","a".repeat(64),
                "releaseCommitSha256","b".repeat(64),"caseId",UUID.randomUUID().toString()).entrySet()) {
            var request=f.sign(f.body.deepCopy().put(change.getKey(),change.getValue()));
            assertThatThrownBy(()->RecoveryTemporaryQualification.verify(request,f.admission)).isInstanceOf(RecoveryControlException.class);
        }
        var capabilities=f.body.deepCopy(); capabilities.withArray("capabilities").add("SCAN");
        assertThatThrownBy(()->RecoveryTemporaryQualification.verify(f.sign(capabilities),f.admission)).hasMessage("RECOVERY_QUALIFICATION_CAPABILITIES");
        var source=f.body.deepCopy(); ((ObjectNode)source.path("source")).put("sourceRecoveryEpoch",text(source.path("control"),"transportRecoveryEpoch"));
        assertThatThrownBy(()->RecoveryTemporaryQualification.verify(f.sign(source),f.admission)).hasMessage("RECOVERY_QUALIFICATION_NEW_EPOCH_REQUIRED");
        var baseline=f.body.deepCopy(); ((ObjectNode)baseline.path("baseline")).put("inventorySha256","c".repeat(64));
        var denied=RecoveryTemporaryQualification.verify(f.sign(baseline),f.admission);
        assertThatThrownBy(()->f.issue(denied)).hasMessage("RECOVERY_QUALIFICATION_BASELINE_CHANGED");
        var changed=f.body.deepCopy(); ((ObjectNode)changed.path("source")).put("payloadSha256","d".repeat(64));
        var invalid=RecoveryTemporaryQualification.verify(f.sign(changed),f.admission);
        assertThatThrownBy(()->f.issue(invalid)).hasMessage("RECOVERY_QUALIFICATION_ORIGINAL_SOURCE");
        Instant past=Instant.now().minusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var old=f.body.deepCopy().put("requestedAt",past.toString()).put("expiresAt",past.plusSeconds(300).toString());
        var expired=RecoveryTemporaryQualification.verify(f.sign(old),f.admission);
        assertThatThrownBy(()->f.issue(expired)).hasMessage("RECOVERY_QUALIFICATION_EXPIRED");
        var retained=RecoveryTemporaryQualification.reopen(f.sign(f.body),f.admission);
        assertThatThrownBy(()->f.issue(retained)).hasMessage("RECOVERY_QUALIFICATION_RETAINED_REQUIRED");
    }
    private static void lastSql(RecoveryQualificationFixture f,RecoveryTemporaryQualification qualification) {
        f.enrollment.jdbc.execute("CREATE TRIGGER test_qualification_tail BEFORE INSERT ON recovery_recipient_qualification FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='qualification final SQL'");
        try { assertThatThrownBy(()->f.issue(qualification)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { f.enrollment.jdbc.execute("DROP TRIGGER test_qualification_tail"); }
    }
    private static void concurrent(RecoveryQualificationFixture f,RecoveryTemporaryQualification qualification) throws Exception {
        var barrier=new CyclicBarrier(2);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<String>>();
            for(int i=0;i<2;i++) calls.add(workers.submit(()->{
                barrier.await(10,TimeUnit.SECONDS);
                try { f.issue(qualification); return "COMMITTED"; }
                catch(RecoveryControlException error) { return error.getMessage(); }
            }));
            for(var call:calls) assertThat(call.get(60,TimeUnit.SECONDS)).isIn("COMMITTED","CONTROL_COORDINATOR_BUSY");
            f.issue(qualification);
        }
    }
    private static void conserved(JsonNode before,JsonNode after) {
        assertThat(after.path("tables").size()).isEqualTo(before.path("tables").size());
        before.path("tables").fields().forEachRemaining(entry->{
            if(!entry.getKey().equals("recovery_recipient_qualification")) assertThat(after.path("tables").path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        });
        assertThat(after.path("tables").path("recovery_recipient_qualification").path("rows").size()).isEqualTo(1);
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
    }
    private RecoveryReceivedQualificationChecks() { }
}
