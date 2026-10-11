package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverRecoveredTemporaryQualification;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.policies.PolicyStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.TemporaryBaseline;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** New isolated target, original source, and explicitly synthetic operations/receiving identities. */
final class RecoveryQualificationFixture {
    final RecoveryRecipientFixture enrollment;
    final RecoveryRecipientAdmission.Request admissionRequest;
    final RecoveryRecipientAdmission admission;
    final ObjectNode body;
    RecoveryQualificationFixture(WebApplicationContext app,Path attempt,Path original) throws Exception {
        enrollment=new RecoveryRecipientFixture(app,attempt,original);
        admissionRequest=enrollment.sign(enrollment.body);
        admission=RecoveryRecipientAdmission.verify(admissionRequest,enrollment.store); enrollment.admit(admission); body=statement();
    }
    private ObjectNode statement() {
        var input=admission.facts(); var result=SubmissionJson.object().put("formatVersion",1)
            .put("purpose","RECOVERED_TEMP_HANDOVER_QUALIFICATION").put("commandId",Ids.next().toString())
            .put("qualificationId",Ids.next().toString()).put("admissionSha256",admission.sha256());
        for(String field:List.of("authoritySha256","operatorId","releaseCommitSha256","sourceManifestSha256","control","warehouseId","scopeId","caseId"))
            result.set(field,input.path(field));
        var target=result.putObject("recipient");
        for(String field:List.of("deviceId","userId","userAuthVersion","keySha256")) target.set(field,input.path("recipient").path(field));
        target.put("deviceVersion","1"); var source=enrollment.signer.closed.sourceIdentity();
        String payload=enrollment.jdbc.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?",String.class,Ids.bytes(source.submissionId()));
        result.putObject("source").put("authorizationId",source.qualificationId().toString()).put("clientOrderId",source.clientOrderId().toString())
            .put("submissionId",source.submissionId().toString()).put("sourceRecoveryEpoch",source.sourceRecoveryEpoch())
            .put("workRound",Long.toString(source.workRound())).put("inputHighWatermark",Long.toString(source.orderScanHighWatermark())).put("payloadSha256",payload);
        var baseline=new TemporaryBaseline(new WarehouseBaseline(enrollment.jdbc),new PolicyStore(enrollment.jdbc),enrollment.jdbc).read(source.warehouseId());
        result.putObject("baseline").put("inventorySnapshotId",baseline.inventorySnapshotId()).put("inventoryVersion",baseline.inventoryVersion())
            .put("inventorySha256",baseline.inventorySha256()).put("mappingRevision",baseline.mappingRevision())
            .put("policyId",baseline.policy().id()).put("policyVersion",baseline.policy().version());
        Instant now=Instant.now().minusMillis(100).truncatedTo(ChronoUnit.MILLIS);
        result.put("requestedAt",now.toString()).put("expiresAt",now.plusSeconds(172800).toString());
        result.putArray("capabilities").add("PREPARE").add("DOWNLOAD").add("CONFIRM"); return result;
    }
    RecoveryTemporaryQualification.Request sign(ObjectNode value) throws Exception {
        byte[] bytes=enrollment.signer.sign(value); Path file=enrollment.signer.directory.resolve("qualification-"+UUID.randomUUID()+".json");
        Files.write(file,bytes); return new RecoveryTemporaryQualification.Request(enrollment.signer.authority(),file,sha256(bytes));
    }
    RecoveryTemporaryQualification.Result issue(RecoveryTemporaryQualification qualification) {
        RecoveryRecipientFixture.authenticate(enrollment.setup.registrar());
        try { return enrollment.app.getBean(HandoverRecoveredTemporaryQualification.class).issue(qualification,enrollment.setup.request()); }
        finally { SecurityContextHolder.clearContext(); }
    }
    void save(Path attempt,RecoveryTemporaryQualification.Request request) throws Exception {
        var value=SubmissionJson.object().put("sourcePid",ProcessHandle.current().pid()).put("identityFile",enrollment.signer.identityFile.toString())
            .put("identitySha256",enrollment.signer.authority().sha256()).put("admissionFile",admissionRequest.file().toString())
            .put("admissionSha256",admissionRequest.independentSha256()).put("qualificationFile",request.file().toString())
            .put("qualificationSha256",request.independentSha256()).put("registrarUsername",enrollment.setup.registrar().username())
            .put("syntheticOperationsSigner",true).put("syntheticReceivingKey",true).put("firstRoundAllowed",false).put("mayResumeScanning",false);
        Files.write(attempt.resolve("qualification-restart-input.json"),RecoveryControlJson.encode(value));
        Files.writeString(attempt.resolve("qualification-restart-input.sha256"),hash(value));
    }
}
