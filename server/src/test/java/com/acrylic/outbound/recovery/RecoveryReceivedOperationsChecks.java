package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual restored DB context with explicitly synthetic offline operations trust and signature. */
public final class RecoveryReceivedOperationsChecks {
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        var initial=RecoveryReceivedPhysicalReviewChecks.verify(app,attempt,original);
        var reviewed=RecoveryReceivedOperationsTransactionChecks.inspect(app,attempt,initial);
        var result=new RecoveryReceivedPhysicalReviewChecks.Result(initial.authority(),reviewed,initial.request());
        var f=new RecoveryOperationsFixture(result.authority()); var context=result.reviewed();
        var jdbc=app.getBean(JdbcTemplate.class); var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage"));
        var before=snapshots.capture(); var body=approval(f,context); var request=f.request(body);
        var approval=RecoveryOperationsApproval.verify(request,context);
        assertThat(approval.facts()).isEqualTo(body); assertThat(approval.sha256()).isEqualTo(request.independentSha256());
        assertThat(approval.releaseAllowed()).isFalse(); assertThat(approval.ordinaryWritesAllowed()).isFalse(); assertThat(approval.localDeletionAllowed()).isFalse();
        assertThat(RecoveryOperationsApproval.verify(request,context).facts()).isEqualTo(approval.facts());
        fieldRefusals(f,body,context); materialRefusals(f,request,context,approval);
        scopeRefusals(f,body,context); transactionRefusals(jdbc,request,context,approval);
        RecoveryReceivedOperationsTransactionChecks.verify(app,attempt,result,request);
        assertThat(snapshots.capture()).isEqualTo(before);
        Files.writeString(attempt.resolve("operations-database-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("operations-database-after.json"),snapshots.capture().toPrettyString());
        Files.writeString(attempt.resolve("operations-physical-review.json"),SubmissionJson.canonical(context.physical().facts())+"\n");
        var numbers=RecoveryReleaseNumberHistory.verify(result.authority(),context.inventory(),result.request().reconciliation().history());
        assertThat(numbers.sha256()).isEqualTo(text(context.physical().facts(),"numberHistorySha256"));
        Files.writeString(attempt.resolve("operations-number-history.json"),SubmissionJson.canonical(numbers.facts())+"\n");
        var evidence=SubmissionJson.object().put("identityFile",f.identityFile.toString()).put("identitySha256",hash(f.identity))
            .put("approvalFile",request.file().toString()).put("approvalSha256",approval.sha256())
            .put("syntheticOperationsIdentity",true).put("fieldRefusals",11).put("sourceBindings",5)
            .put("warehouseRefused",true).put("fileTailRefused",true).put("identityTailRefused",true)
            .put("transactionPreflightRefused",true).put("databaseUnchanged",true).put("releaseAllowed",false);
        Files.write(attempt.resolve("operations-approval-checks.json"),RecoveryControlJson.encode(evidence));
    }
    private static ObjectNode approval(RecoveryOperationsFixture f,RecoveryOperationsApproval.Context context) {
        var inventory=context.inventory().facts(); var fault=inventory.path("faultReceipt");
        String caseId=text(fault.path("businessDecision"),"caseId");
        var row=RecoveryReleaseRows.decode(inventory.path("tables").path("handover_command_receipt")).stream()
            .filter(value->RecoveryReleaseRows.text(value,"command_type").equals("APPROVE")).findFirst().orElseThrow();
        var receipt=SubmissionJson.parse(RecoveryReleaseRows.bytes(row.get("response_bytes")));
        var value=SubmissionJson.object().put("formatVersion",1).put("purpose","RECOVERED_TEMP_NEW_QUALIFICATION")
            .put("commandId",UUID.randomUUID().toString()).put("authoritySha256",hash(f.identity)).put("operatorId",text(f.identity,"operatorId"))
            .put("requestedAt",Instant.now().truncatedTo(ChronoUnit.MILLIS).toString())
            .put("recoverySessionId",f.closed.control().recoverySessionId().toString()).put("caseId",caseId)
            .put("supervisorId",text(receipt.path("actor"),"principalId"));
        value.set("control",inventory.path("control")); value.set("physicalReview",context.physical().facts()); return value;
    }
    private static void fieldRefusals(RecoveryOperationsFixture f,ObjectNode body,RecoveryOperationsApproval.Context context) throws Exception {
        var changes=Map.of("purpose","NORMAL","authoritySha256","a".repeat(64),"operatorId",UUID.randomUUID().toString(),
            "caseId",UUID.randomUUID().toString(),"supervisorId",UUID.randomUUID().toString(),
            "recoverySessionId",UUID.randomUUID().toString(),"requestedAt",Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS).toString());
        for(var change:changes.entrySet()) rejects(f,body.deepCopy().put(change.getKey(),change.getValue()),context);
        rejects(f,body.deepCopy().put("requestedAt","2020-01-01T00:00:00Z"),context);
        var revision=body.deepCopy(); ((ObjectNode)revision.path("control")).put("controlRevision","99999"); rejects(f,revision,context);
        var physical=body.deepCopy(); ((ObjectNode)physical.path("physicalReview")).put("scopeId",UUID.randomUUID().toString()); rejects(f,physical,context);
        var missing=body.deepCopy(); missing.remove("commandId"); rejects(f,missing,context);
    }
    private static void rejects(RecoveryOperationsFixture f,ObjectNode body,RecoveryOperationsApproval.Context context) throws Exception {
        var request=f.request(body); assertThatThrownBy(()->RecoveryOperationsApproval.verify(request,context)).isInstanceOf(RecoveryControlException.class);
    }
    private static void materialRefusals(RecoveryOperationsFixture f,RecoveryOperationsApproval.Request request,
            RecoveryOperationsApproval.Context context,RecoveryOperationsApproval verified) throws Exception {
        var wrongPin=new RecoveryOperationsApproval.Request(request.authority(),request.file(),"b".repeat(64));
        assertThatThrownBy(()->RecoveryOperationsApproval.verify(wrongPin,context)).hasMessage("RECOVERY_OPERATIONS_APPROVAL_PIN_MISMATCH");
        byte[] original=Files.readAllBytes(request.file()); Files.writeString(request.file(),"changed");
        try { assertThatThrownBy(()->verified.requireCurrent(context)).hasMessage("RECOVERY_OPERATIONS_APPROVAL_PIN_MISMATCH"); }
        finally { Files.write(request.file(),original); }
        byte[] identity=Files.readAllBytes(f.identityFile); Files.writeString(f.identityFile,"changed");
        try { assertThatThrownBy(()->verified.requireCurrent(context)).hasMessage("RECOVERY_OPERATIONS_PIN_MISMATCH"); }
        finally { Files.write(f.identityFile,identity); }
        verified.requireCurrent(context);
    }
    private static void scopeRefusals(RecoveryOperationsFixture f,ObjectNode body,RecoveryOperationsApproval.Context context) throws Exception {
        for(String field:List.of("warehouseId","scopeId","submissionId","deviceId","sourceManifestSha256")) {
            ObjectNode altered=(ObjectNode)context.inventory().facts();
            altered.put(field,field.equals("sourceManifestSha256")?"b".repeat(64):UUID.randomUUID().toString());
            var inventory=new RecoveryReleaseInventory(altered,context.inventory().gaps()); var request=f.request(body);
            assertThatThrownBy(()->RecoveryOperationsApproval.verify(request,new RecoveryOperationsApproval.Context(context.physical(),inventory)))
                .isInstanceOf(RecoveryControlException.class);
        }
        var original=f.identity.deepCopy(); f.identity.putArray("warehouseIds").add(UUID.randomUUID().toString()); f.publish();
        try {
            var request=f.request(body.deepCopy().put("authoritySha256",hash(f.identity)));
            assertThatThrownBy(()->RecoveryOperationsApproval.verify(request,context)).hasMessage("RECOVERY_OPERATIONS_WAREHOUSE_FORBIDDEN");
        } finally { f.identity.setAll(original); f.publish(); }
    }
    private static void transactionRefusals(JdbcTemplate jdbc,RecoveryOperationsApproval.Request request,
            RecoveryOperationsApproval.Context context,RecoveryOperationsApproval approval) {
        var tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        tx.executeWithoutResult(status->{
            assertThatThrownBy(()->RecoveryOperationsApproval.verify(request,context)).hasMessage("RECOVERY_OPERATIONS_PREFLIGHT_OUTSIDE_TRANSACTION");
            approval.requireCurrent(context); status.setRollbackOnly();
        });
    }
    private RecoveryReceivedOperationsChecks() { }
}
