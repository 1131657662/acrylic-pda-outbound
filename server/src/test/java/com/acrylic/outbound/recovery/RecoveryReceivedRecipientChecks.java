package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual original-checkpoint enrollment. New key and offline trust are explicitly synthetic development inputs. */
public final class RecoveryReceivedRecipientChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var f=new RecoveryRecipientFixture(app,attempt,original);
        var snapshots=new E2aMigrationSnapshot(f.jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        var request=f.sign(f.body); var admission=RecoveryRecipientAdmission.verify(request,f.store);
        RecoveryRecipientSignatureChecks.before(f);
        negatives(f,request,admission,before,snapshots);
        lastSql(f,admission,before,snapshots);
        com.acrylic.outbound.handover.HandoverRecoveredRecipientTailChecks.verify(app,admission,f.setup.request(),f.setup.registrar());
        assertThat(snapshots.capture()).isEqualTo(before);
        var result=f.admit(admission); assertThat(result.signedSha256()).isEqualTo(request.independentSha256());
        assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
        assertThat(result.newQualificationIssued()).isFalse(); var after=snapshots.capture(); conserved(before,after,admission.facts());
        assertThat(f.admit(admission).facts()).isEqualTo(result.facts()); assertThat(snapshots.capture()).isEqualTo(after);
        concurrent(f,admission); assertThat(snapshots.capture()).isEqualTo(after);
        signedConflict(f); RecoveryRecipientSignatureChecks.after(f); immutable(f); assertThat(snapshots.capture()).isEqualTo(after);
        Files.writeString(attempt.resolve("recipient-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("recipient-after.json"),after.toPrettyString());
        var facts=SubmissionJson.object().put("syntheticReceivingKey",true).put("syntheticOperationsSigner",true)
            .put("sourcePid",ProcessHandle.current().pid()).put("admissionFile",request.file().toString())
            .put("admissionSha256",request.independentSha256()).put("identityFile",f.signer.identityFile.toString())
            .put("identitySha256",f.signer.authority().sha256()).put("registrarUsername",f.setup.registrar().username())
            .put("releaseRoot",f.store.intent().commit().path("commandId").asText()).put("newQualificationIssued",false)
            .put("ordinaryWritesAllowed",false).put("localDeletionAllowed",false).put("lastSqlRollback",true)
            .put("tailRollback",true).put("sameKeyReplay",true).put("signedConflictRefused",true);
        Files.write(attempt.resolve("recipient-checks.json"),RecoveryControlJson.encode(facts));
        Files.writeString(attempt.resolve("recipient-statement.json"),canonical(admission.facts()));
    }
    private static void negatives(RecoveryRecipientFixture f,RecoveryRecipientAdmission.Request request,RecoveryRecipientAdmission admission,
            JsonNode before,E2aMigrationSnapshot snapshots) throws Exception {
        var changes=Map.of("purpose","NORMAL","scopeId",UUID.randomUUID().toString(),"caseId",UUID.randomUUID().toString(),
            "releaseCommitSha256","b".repeat(64),"sourceManifestSha256","a".repeat(64),"supervisorId",UUID.randomUUID().toString());
        for(var change:changes.entrySet()) {
            var signed=f.sign(f.body.deepCopy().put(change.getKey(),change.getValue()));
            assertThatThrownBy(()->RecoveryRecipientAdmission.verify(signed,f.store)).isInstanceOf(RecoveryControlException.class);
        }
        var wrongPin=new RecoveryRecipientAdmission.Request(request.authority(),request.file(),"b".repeat(64));
        assertThatThrownBy(()->RecoveryRecipientAdmission.verify(wrongPin,f.store)).hasMessage("RECOVERY_RECIPIENT_PIN_MISMATCH");
        var old=f.body.deepCopy(); ((ObjectNode)old.path("recipient")).put("deviceId",f.signer.closed.sourceIdentity().deviceId().toString());
        var oldRequest=f.sign(old);
        assertThatThrownBy(()->RecoveryRecipientAdmission.verify(oldRequest,f.store)).hasMessage("RECOVERY_RECIPIENT_ORIGINAL_DEVICE");
        var changed=f.body.deepCopy(); ((ObjectNode)changed.path("recipient")).put("userAuthVersion","999999");
        var denied=RecoveryRecipientAdmission.verify(f.sign(changed),f.store);
        assertThatThrownBy(()->f.admit(denied)).hasMessage("RECOVERY_RECIPIENT_USER_CHANGED");
        var tx=new TransactionTemplate(new DataSourceTransactionManager(f.jdbc.getDataSource()));
        assertThatThrownBy(()->tx.executeWithoutResult(s->admission.lockIdentity(f.jdbc.getDataSource())))
            .hasMessage("CONTROL_CLOSED_OPERATION_REQUIRED");
        assertThat(snapshots.capture()).isEqualTo(before);
    }
    private static void lastSql(RecoveryRecipientFixture f,RecoveryRecipientAdmission admission,JsonNode before,E2aMigrationSnapshot snapshots) throws Exception {
        f.jdbc.execute("CREATE TRIGGER test_recipient_tail BEFORE INSERT ON recovery_recipient_admission FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='recipient last SQL failure'");
        try { assertThatThrownBy(()->f.admit(admission)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { f.jdbc.execute("DROP TRIGGER test_recipient_tail"); }
        assertThat(snapshots.capture()).isEqualTo(before);
    }
    private static void signedConflict(RecoveryRecipientFixture f) throws Exception {
        var changed=f.body.deepCopy(); ((ObjectNode)changed.path("recipient")).put("name","同键不同登记");
        var admission=RecoveryRecipientAdmission.verify(f.sign(changed),f.store);
        assertThatThrownBy(()->f.admit(admission)).isInstanceOf(RecoveryControlException.class);
    }
    private static void immutable(RecoveryRecipientFixture f) {
        assertThatThrownBy(()->f.jdbc.update("UPDATE recovery_recipient_admission SET recorded_at=UTC_TIMESTAMP(3)"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.update("DELETE FROM recovery_recipient_admission")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    private static void concurrent(RecoveryRecipientFixture f,RecoveryRecipientAdmission admission) throws Exception {
        var barrier=new CyclicBarrier(2);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<String>>();
            for(int i=0;i<2;i++) calls.add(workers.submit(()->{
                barrier.await(10,TimeUnit.SECONDS);
                try { f.admit(admission); return "COMMITTED"; }
                catch(RecoveryControlException error) { return error.getMessage(); }
            }));
            var codes=new ArrayList<String>(); for(var call:calls) codes.add(call.get(60,TimeUnit.SECONDS));
            assertThat(codes).allMatch(code->Set.of("COMMITTED","CONTROL_COORDINATOR_BUSY").contains(code));
            f.admit(admission);
        }
    }
    private static void conserved(JsonNode before,JsonNode after,JsonNode statement) {
        var normalized=RecoveryRecipientDeviceEffect.before(after.path("tables"),statement);
        before.path("tables").fields().forEachRemaining(entry->{
            if(entry.getKey().equals("recovery_recipient_admission")) return;
            var expected=entry.getValue(); var actual=normalized.path(entry.getKey());
            assertThat(actual.path("columns")).as(entry.getKey()).isEqualTo(expected.path("columns"));
            assertThat(sorted(actual.path("rows"))).as(entry.getKey()).isEqualTo(sorted(expected.path("rows")));
        });
        assertThat(after.path("tables").path("recovery_recipient_admission").path("rows").size()).isEqualTo(1);
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
    }
    private static List<String> sorted(JsonNode rows) {
        var result=new ArrayList<String>(); rows.forEach(row->result.add(row.toString())); Collections.sort(result); return result;
    }
    private RecoveryReceivedRecipientChecks() { }
}
