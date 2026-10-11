package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Separates the immutable original source epoch from the new target preparation epoch. */
final class RecoveryTemporaryQualificationFormat {
    static void verify(JsonNode value,RecoveryRecipientAdmission recipient) {
        fields(value,"formatVersion purpose commandId qualificationId authoritySha256 operatorId admissionSha256 releaseCommitSha256 sourceManifestSha256 control warehouseId scopeId caseId recipient source baseline requestedAt expiresAt capabilities");
        version(value); uuid(value,"commandId"); uuid(value,"qualificationId");
        require(text(value,"purpose").equals("RECOVERED_TEMP_HANDOVER_QUALIFICATION"),"RECOVERY_QUALIFICATION_PURPOSE");
        var admission=recipient.facts();
        for(String field:List.of("authoritySha256","operatorId","releaseCommitSha256","sourceManifestSha256","control","warehouseId","scopeId","caseId"))
            require(value.path(field).equals(admission.path(field)),"RECOVERY_QUALIFICATION_ADMISSION_BINDING");
        require(text(value,"admissionSha256").equals(recipient.sha256()),"RECOVERY_QUALIFICATION_ADMISSION_PIN");
        var target=value.path("recipient"); fields(target,"deviceId userId userAuthVersion deviceVersion keySha256");
        for(String field:List.of("deviceId","userId","userAuthVersion","keySha256"))
            require(target.path(field).equals(admission.path("recipient").path(field)),"RECOVERY_QUALIFICATION_RECIPIENT");
        require(text(target,"deviceVersion").equals("1"),"RECOVERY_QUALIFICATION_DEVICE_VERSION");
        var source=value.path("source");
        fields(source,"authorizationId clientOrderId submissionId sourceRecoveryEpoch workRound inputHighWatermark payloadSha256");
        for(String field:List.of("authorizationId","clientOrderId","submissionId")) uuid(source,field);
        require(revision(source,"workRound")>0 && revision(source,"sourceRecoveryEpoch")>0,"RECOVERY_QUALIFICATION_SOURCE");
        revision(source,"inputHighWatermark"); hashField(source,"payloadSha256");
        require(!text(source,"sourceRecoveryEpoch").equals(text(value.path("control"),"transportRecoveryEpoch")),"RECOVERY_QUALIFICATION_NEW_EPOCH_REQUIRED");
        baseline(value.path("baseline")); window(value);
        require(value.path("capabilities").equals(SubmissionJson.object().putArray("capabilities")
            .add("PREPARE").add("DOWNLOAD").add("CONFIRM")),"RECOVERY_QUALIFICATION_CAPABILITIES");
    }
    static void bindArchive(JsonNode value,RecoveryRecoveredTemporaryArchive archive) {
        var source=archive.source(); var expected=SubmissionJson.object().put("authorizationId",source.qualificationId().toString())
            .put("clientOrderId",source.clientOrderId().toString()).put("submissionId",source.submissionId().toString())
            .put("sourceRecoveryEpoch",source.sourceRecoveryEpoch()).put("workRound",Long.toString(source.workRound()))
            .put("inputHighWatermark",Long.toString(source.orderScanHighWatermark())).put("payloadSha256",archive.provenance().payloadSha256());
        require(expected.equals(value.path("source")),"RECOVERY_QUALIFICATION_ORIGINAL_SOURCE");
        require(text(value,"warehouseId").equals(source.warehouseId().toString())
            && text(value,"scopeId").equals(source.scopeId().toString())
            && text(value,"sourceManifestSha256").equals(archive.provenance().sourceManifestSha256()),"RECOVERY_QUALIFICATION_ARCHIVE");
        var payload=SubmissionJson.parse(archive.payload());
        for(String field:List.of("inventorySnapshotId","policyVersion"))
            require(value.path("baseline").path(field).equals(payload.path(field)),"RECOVERY_QUALIFICATION_SOURCE_BASELINE");
    }
    private static void baseline(JsonNode value) {
        fields(value,"inventorySnapshotId inventoryVersion inventorySha256 mappingRevision policyId policyVersion");
        uuid(value,"inventorySnapshotId"); uuid(value,"policyId"); hashField(value,"inventorySha256");
        require(revision(value,"inventoryVersion")>0 && revision(value,"policyVersion")>0,"RECOVERY_QUALIFICATION_BASELINE");
        revision(value,"mappingRevision");
    }
    private static void window(JsonNode value) {
        Instant issued=instant(value,"requestedAt"),expires=instant(value,"expiresAt");
        require(issued.getNano()%1_000_000==0 && expires.getNano()%1_000_000==0 && expires.isAfter(issued)
            && !expires.isAfter(issued.plusSeconds(172800)),"RECOVERY_QUALIFICATION_WINDOW");
    }
    private RecoveryTemporaryQualificationFormat() { }
}
