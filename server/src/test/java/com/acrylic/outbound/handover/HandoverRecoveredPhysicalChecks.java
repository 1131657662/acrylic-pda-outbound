package com.acrylic.outbound.handover;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.recovery.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Actual service/DB approval with a synthetic supervisor checklist; not field physical evidence. */
public final class HandoverRecoveredPhysicalChecks {
    public static RecoveryOperationsApproval.Context verify(WebApplicationContext app,Path attempt,HandoverRecoveredTemporaryBinding binding,
            RecoveryReleaseReconciliation.Request input) throws Exception {
        var f=new HandoverRecoveredTemporaryFixture(app,attempt,binding);
        var request=f.request(); byte[] requested=f.execute(f.requester,request);
        var fault=f.fault(uuid(parse(requested).path("businessDecision"),"caseId")); byte[] fenced=f.execute(f.supervisor,fault);
        Files.write(f.directory.resolve("request-command.json"),HandoverReceiptCodec.bytes(request)); Files.write(f.directory.resolve("request-receipt.json"),requested);
        Files.write(f.directory.resolve("fault-command.json"),HandoverReceiptCodec.bytes(fault)); Files.write(f.directory.resolve("fault-receipt.json"),fenced);
        var service=app.getBean(HandoverRecoveredPhysicalReview.class);
        var context=new HandoverRecoveredPhysicalReview.Request(HandoverReceiptCodec.bytes(fault),binding,input);
        var approval=approval(f,fault,app); var before=f.snapshot("physical-before");
        history(app,f,context);
        HandoverRecoveredPhysicalRefusals.verify(f,service,context,approval);
        var result=execute(service,f.supervisor,approval,context);
        assertThat(result.proof().facts().path("physicalState").asText()).isEqualTo("MATCHED");
        assertThat(result.proof().releaseAllowed()).isFalse(); assertThat(result.proof().ordinaryWritesAllowed()).isFalse();
        assertThat(result.proof().localDeletionAllowed()).isFalse();
        var after=f.snapshot("physical-after"); preserved(before,after);
        assertThat(execute(service,f.supervisor,approval,context).receipt()).isEqualTo(result.receipt());
        assertThat(f.snapshot("physical-replay")).isEqualTo(after);
        HandoverRecoveredPhysicalTailChecks.verify(app,f,context);
        HandoverRecoveredPhysicalConcurrency.verify(f,service,context,approval,result.receipt());
        evidence(app,f,context,approval,result,after);
        assertThat(f.jdbc.queryForObject("SELECT service_mode FROM server_state WHERE id=1",String.class)).isEqualTo("RECOVERY_PENDING");
        HandoverRecoveredTemporaryFixture.authenticate(f.supervisor);
        try {
            return new RecoveryOperationsApproval.Context(result.proof(),
                app.getBean(HandoverRecoveredTemporaryReview.class).inspect(context.originalFault(),binding));
        } finally { SecurityContextHolder.clearContext(); }
    }
    private static void evidence(WebApplicationContext app,HandoverRecoveredTemporaryFixture f,
            HandoverRecoveredPhysicalReview.Request context,JsonNode approval,HandoverRecoveredPhysicalReview.Result result,
            JsonNode after) throws Exception {
        HandoverRecoveredTemporaryFixture.authenticate(f.supervisor);
        try {
            var inventory=app.getBean(HandoverRecoveredTemporaryReview.class).inspect(context.originalFault(),f.binding);
            Files.writeString(f.directory.resolve("release-inventory.json"),canonical(inventory.facts())+"\n");
            RecoveryReceivedPhysicalSemanticChecks.verify(parse(Files.readAllBytes(context.reconciliation().history().end().file())).path("tables"),
                after.path("tables"),new RecoveryReceivedPhysicalSemanticChecks.Input(inventory,context.reconciliation().targetKeys()));
        } finally { SecurityContextHolder.clearContext(); }
        var service=app.getBean(HandoverRecoveredPhysicalReview.class);
        assertThatThrownBy(()->execute(service,f.supervisor,((ObjectNode)approval.deepCopy()).put("reason","different"),context))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,e->assertThat(e.code()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH"));
        Files.write(f.directory.resolve("approval-command.json"),HandoverReceiptCodec.bytes(approval)); Files.write(f.directory.resolve("approval-receipt.json"),result.receipt());
        var attempt=f.directory.getParent();
        Files.writeString(attempt.resolve("release-physical-review.json"),canonical(result.proof().facts())+"\n");
        Files.writeString(attempt.resolve("release-physical-review.sha256"),result.proof().sha256());
        Files.writeString(attempt.resolve("release-physical-review-checks.json"),object().put("deniedApprovals",6)
            .put("auditSqlRollback",true).put("unknownScopeRollback",true).put("tailRollbacks",2).put("semanticNegativeChecks",8).put("concurrentReplayRecovered",true)
            .put("syntheticPhysicalDeclaration",true).put("releaseAllowed",false).toPrettyString());
    }
    static HandoverRecoveredPhysicalReview.Result execute(HandoverRecoveredPhysicalReview service,SessionUser actor,
            JsonNode command,HandoverRecoveredPhysicalReview.Request context) {
        HandoverRecoveredTemporaryFixture.authenticate(actor);
        try { return service.approve(HandoverReceiptCodec.bytes(command),"received-physical",context); }
        finally { SecurityContextHolder.clearContext(); }
    }
    static ObjectNode approval(HandoverRecoveredTemporaryFixture f,JsonNode fault,WebApplicationContext app) {
        var command=object().put("commandVersion",1).put("commandType","APPROVE").put("commandId",UUID.randomUUID().toString())
            .put("warehouseId",text(fault,"warehouseId")).put("transportRecoveryEpoch",text(fault,"transportRecoveryEpoch"))
            .put("caseId",text(fault,"caseId")).put("stopState","FAULT_FENCED").put("integrityState","COMPLETE")
            .put("originalResultState","ABORTED").put("physicalState","MATCHED")
            .put("inputHighWatermark",text(fault.path("archivedEvidence"),"orderScanHighWatermark"));
        command.set("expected",HandoverCommand.Expected.forCase(f.cases.caseFacts(uuid(fault,"caseId")),f.supervisor.authVersion()));
        var store=app.getBean(com.acrylic.outbound.submissions.SubmissionStore.class);
        var files=app.getBean(com.acrylic.outbound.submissions.SubmissionFiles.class);
        var body=parse(files.read(store.payloadHash(f.binding.request().evidence().expected().submissionId())));
        var lines=command.putArray("checklist");
        for(var scan:com.acrylic.outbound.continuation.DispositionProofs.effective(body))
            lines.addObject().put("baleId",text(scan,"baleId")).put("deliveryCode",text(scan,"deliveryCode"))
                .put("dispatchCycle",text(scan,"expectedDispatchCycle")).put("state","RETAINED");
        return command;
    }
    private static void preserved(JsonNode before,JsonNode after) {
        var changed=Set.of("handover_case","handover_checklist","handover_command_receipt","api_idempotency_request","operation_event");
        before.path("tables").fields().forEachRemaining(e->{if(!changed.contains(e.getKey())) assertThat(after.path("tables").path(e.getKey())).as(e.getKey()).isEqualTo(e.getValue());});
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
    }
    private static void history(WebApplicationContext app,HandoverRecoveredTemporaryFixture f,HandoverRecoveredPhysicalReview.Request request) throws Exception {
        HandoverRecoveredTemporaryFixture.authenticate(f.supervisor);
        try {
            var inventory=app.getBean(HandoverRecoveredTemporaryReview.class).inspect(request.originalFault(),f.binding);
            var proof=RecoveryReleaseNumberHistory.verify(request.reconciliation().authority(),inventory,request.reconciliation().history());
            Files.writeString(f.directory.resolve("physical-before-inventory.json"),canonical(inventory.facts())+"\n");
            Files.writeString(f.directory.resolve("physical-number-history.json"),canonical(proof.facts())+"\n");
        } finally { SecurityContextHolder.clearContext(); }
    }
    private HandoverRecoveredPhysicalChecks() { }
}
