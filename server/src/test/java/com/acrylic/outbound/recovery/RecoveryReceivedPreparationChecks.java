package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.handover.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual PREPARE consumes the new qualification while preserving all original source facts. */
public final class RecoveryReceivedPreparationChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var f=new RecoveryQualificationFixture(app,attempt,original); var signed=f.sign(f.body);
        var qualification=RecoveryTemporaryQualification.verify(signed,f.admission); f.issue(qualification); f.save(attempt,signed);
        var snapshots=new E2aMigrationSnapshot(f.enrollment.jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        var command=command(f); negatives(f,qualification,command); lastSql(f,qualification,command);
        HandoverRecoveredPreparationTailChecks.verify(app,new HandoverRecoveredPreparationTailChecks.Input(
            qualification,f.enrollment.setup.request(),f.enrollment.setup.supervisor(),command));
        materialTail(f,qualification,command,signed.file());
        sameRows(before,snapshots.capture());
        byte[] receipt=concurrent(f,qualification,command); var parsed=SubmissionJson.parse(receipt);
        assertThat(parsed.path("businessDecision").path("status").asText()).isEqualTo("TARGET_PREPARED");
        assertThat(parsed.path("businessDecision").path("temporaryQualification")).isEqualTo(qualification.preparationDescriptor());
        var after=snapshots.capture(); conserved(before,after); assertThat(prepare(f,qualification,command)).isEqualTo(receipt);
        var changed=command.deepCopy(); ((ObjectNode)changed.path("expected")).put("caseVersion","4");
        assertThatThrownBy(()->prepare(f,qualification,changed)).isInstanceOf(com.acrylic.outbound.common.BusinessException.class)
            .extracting("code").isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        oldActivation(f,qualification,command);
        assertThatThrownBy(()->prepare(f,qualification,command.deepCopy().put("targetUserId",Ids.next().toString())))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        assertThat(snapshots.capture()).isEqualTo(after);
        Files.write(attempt.resolve("preparation-command.json"),RecoveryControlJson.encode(command));
        Files.write(attempt.resolve("preparation-receipt.json"),receipt);
        Files.writeString(attempt.resolve("preparation-supervisor.txt"),f.enrollment.setup.supervisor().username());
        Files.writeString(attempt.resolve("preparation-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("qualification-after.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("preparation-after.json"),after.toPrettyString());
    }
    private static void materialTail(RecoveryQualificationFixture f,RecoveryTemporaryQualification q,ObjectNode command,Path file) throws Exception {
        byte[] original=Files.readAllBytes(file);
        try {
            HandoverRecoveredPreparationTailChecks.reject(f.enrollment.app,new HandoverRecoveredPreparationTailChecks.Input(
                q,f.enrollment.setup.request(),f.enrollment.setup.supervisor(),command),()->{
                    try { Files.write(file,new byte[]{0}); } catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                });
        } finally { Files.write(file,original); }
    }
    private static void oldActivation(RecoveryQualificationFixture f,RecoveryTemporaryQualification q,ObjectNode command) {
        RecoveryRecipientFixture.authenticate(f.enrollment.setup.supervisor());
        try {
            var activation=command.deepCopy().put("commandType","ACTIVATE").put("commandId",Ids.next().toString());
            assertThatThrownBy(()->f.enrollment.app.getBean(HandoverCommandService.class).execute(
                RecoveryControlJson.encode(activation),"prepare-old-activation")).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
            assertThatThrownBy(()->prepare(f,q,activation)).isInstanceOf(RecoveryControlException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }
    static ObjectNode command(RecoveryQualificationFixture f) {
        var q=f.body; var supervisor=f.enrollment.setup.supervisor();
        var command=SubmissionJson.object().put("commandVersion",1).put("commandType","PREPARE").put("commandId",Ids.next().toString())
            .put("caseId",text(q,"caseId")).put("warehouseId",text(q,"warehouseId"))
            .put("transportRecoveryEpoch",text(q.path("control"),"transportRecoveryEpoch"))
            .put("targetDeviceId",text(q.path("recipient"),"deviceId")).put("targetUserId",text(q.path("recipient"),"userId"))
            .put("targetQualificationId",text(q,"qualificationId"));
        command.putObject("expected").put("caseId",text(q,"caseId")).put("caseVersion","3").put("status","APPROVED")
            .put("userAuthVersion",Long.toString(supervisor.authVersion())); return command;
    }
    static byte[] prepare(RecoveryQualificationFixture f,RecoveryTemporaryQualification q,ObjectNode command) {
        RecoveryRecipientFixture.authenticate(f.enrollment.setup.supervisor());
        try { return f.enrollment.app.getBean(HandoverRecoveredTemporaryPreparation.class).prepare(
            RecoveryControlJson.encode(command),"prepare-qualification",q,f.enrollment.setup.request()); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private static void negatives(RecoveryQualificationFixture f,RecoveryTemporaryQualification q,ObjectNode command) {
        for(String field:List.of("targetDeviceId","targetUserId","caseId","warehouseId","targetQualificationId"))
            assertThatThrownBy(()->prepare(f,q,command.deepCopy().put(field,Ids.next().toString())))
                .isInstanceOfAny(RecoveryControlException.class,com.acrylic.outbound.common.BusinessException.class);
        assertThatThrownBy(()->prepare(f,q,command.deepCopy().put("transportRecoveryEpoch",text(f.body.path("source"),"sourceRecoveryEpoch"))))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        var old=command.deepCopy(); old.remove("targetQualificationId"); old.put("targetAuthorizationId",text(f.body.path("source"),"authorizationId"));
        assertThatThrownBy(()->prepare(f,q,old)).isInstanceOf(RecoveryControlException.class);
        RecoveryRecipientFixture.authenticate(f.enrollment.setup.registrar());
        try {
            assertThatThrownBy(()->f.enrollment.app.getBean(HandoverRecoveredTemporaryPreparation.class).prepare(
                RecoveryControlJson.encode(command),"prepare-denied",q,f.enrollment.setup.request())).hasMessage("RECOVERY_PREPARATION_ACTOR");
        } finally { SecurityContextHolder.clearContext(); }
    }
    private static void lastSql(RecoveryQualificationFixture f,RecoveryTemporaryQualification q,ObjectNode command) {
        f.enrollment.jdbc.execute("CREATE TRIGGER test_prepare_tail BEFORE INSERT ON operation_event FOR EACH ROW BEGIN IF NEW.event_type='HANDOVER_PREPARE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='prepare final audit'; END IF; END");
        try { assertThatThrownBy(()->prepare(f,q,command)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { f.enrollment.jdbc.execute("DROP TRIGGER test_prepare_tail"); }
    }
    private static byte[] concurrent(RecoveryQualificationFixture f,RecoveryTemporaryQualification q,ObjectNode command) throws Exception {
        var barrier=new CyclicBarrier(2);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<byte[]>>();
            for(int i=0;i<2;i++) calls.add(workers.submit(()->{
                barrier.await(10,TimeUnit.SECONDS);
                try { return prepare(f,q,command); }
                catch(RecoveryControlException error) { assertThat(error.getMessage()).isEqualTo("CONTROL_COORDINATOR_BUSY"); return null; }
            }));
            var committed=new ArrayList<byte[]>(); var outcomes=SubmissionJson.object().putArray("outcomes");
            for(var call:calls) { var result=call.get(60,TimeUnit.SECONDS); outcomes.add(result==null?"CONTROL_COORDINATOR_BUSY":"COMMITTED"); if(result!=null) committed.add(result); }
            assertThat(committed).isNotEmpty(); byte[] receipt=committed.getFirst();
            for(byte[] result:committed) assertThat(result).isEqualTo(receipt);
            assertThat(prepare(f,q,command)).isEqualTo(receipt);
            Files.write(f.enrollment.signer.directory.resolve("preparation-first-concurrency.json"),RecoveryControlJson.encode(outcomes));
            return receipt;
        }
    }
    private static void conserved(JsonNode before,JsonNode after) {
        var changed=Set.of("handover_case","handover_command_receipt","api_idempotency_request","operation_event");
        assertThat(after.path("tables").size()).isEqualTo(before.path("tables").size());
        before.path("tables").fields().forEachRemaining(e->{if(!changed.contains(e.getKey()))
            assertThat(after.path("tables").path(e.getKey())).as(e.getKey()).isEqualTo(e.getValue());});
        for(String table:List.of("handover_command_receipt","api_idempotency_request","operation_event"))
            assertThat(after.path("tables").path(table).path("rows").size()).isEqualTo(before.path("tables").path(table).path("rows").size()+1);
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
        schema(before,after);
    }
    private static void sameRows(JsonNode before,JsonNode after) {
        assertThat(after.path("tables")).isEqualTo(before.path("tables"));
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
        schema(before,after);
    }
    private static void schema(JsonNode before,JsonNode after) {
        before.path("schema").fields().forEachRemaining(e->{
            String expected=e.getValue().asText(),actual=after.path("schema").path(e.getKey()).asText();
            if(e.getKey().equals("operation_event")) {
                expected=expected.replaceAll(" AUTO_INCREMENT=[0-9]+",""); actual=actual.replaceAll(" AUTO_INCREMENT=[0-9]+","");
            }
            assertThat(actual).as(e.getKey()).isEqualTo(expected);
        });
    }
    private RecoveryReceivedPreparationChecks() { }
}
