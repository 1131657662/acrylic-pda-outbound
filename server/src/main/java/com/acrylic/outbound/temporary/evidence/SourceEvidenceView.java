package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.ServerState;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static com.acrylic.outbound.temporary.evidence.SourceEvidenceAccess.*;

final class SourceEvidenceView {
    private SourceEvidenceView() { }
    static JsonNode control(Context context) {
        var h=context.head();var c=h.control();var metadata=context.metadata();
        var result=object().put("submissionId",c.id().toString()).put("workRound",Long.toString(c.round()))
            .put("authorizationId",c.temporary().authorization().toString()).put("freezeDeviceId",c.device().toString())
            .put("sourceRecoveryEpoch",c.epoch()).putNull("assignmentId").putNull("continuationId").put("status",h.status());
        result.set("source",c.source());
        for(String key:List.of("scopeId","scopeRound","currentSubmissionId","registeredAt"))result.set(key,metadata.get(key));
        return result;
    }
    static JsonNode provenance(Context context) {
        var h=context.head();var result=object().put("warehouseId",h.warehouse().toString()).put("originalOperatorId",h.actor().toString())
            .put("originalDeviceId",h.control().temporary().creator().toString()).put("bindingVerified",true);
        for(String key:List.of("authorizationId","authorizationEpoch","issuedAt","expiresAt"))result.set(key,context.metadata().get(key));
        return result;
    }
    static JsonNode assessment(Context context,SourceEvidenceArtifacts.View artifacts,JsonNode proof,ServerState.State state) {
        var c=context.head().control();var result=object().put("evaluatedAt",Instant.now().toString())
            .put("readerPermissionVersion",context.readerPermissionVersion()).put("serverRecoveryEpoch",state.epoch()).put("serviceMode",state.mode());
        var gaps=result.putArray("gaps");gaps.add("PHYSICAL_SET_COMPLETENESS_UNPROVEN").add("AUDIT_DEVICE_COMPLETENESS_UNPROVEN");
        if(artifacts.payload()==null)gaps.add("RECEIVED_PAYLOAD_NOT_PRESENT");
        if(artifacts.upload().path("present").asBoolean() && !artifacts.upload().path("allDeclaredChunksReceived").asBoolean())gaps.add("DECLARED_UPLOAD_INCOMPLETE");
        if(!proof.path("verification").asText().equals("VERIFIED"))gaps.add("VERIFIED_TERMINAL_PROOF_NOT_AVAILABLE");
        if(!c.epoch().equals(state.epoch()))gaps.add("SOURCE_EPOCH_DIFFERS");
        if(!state.mode().equals("NORMAL"))gaps.add("SERVER_RECOVERY_REQUIRED");
        boolean current=context.metadata().path("currentSubmissionId").asText().equals(c.id().toString())
            && context.metadata().path("scopeRound").asText().equals(Long.toString(c.round()));
        result.put("currentSubmission",current);
        for(String key:List.of("writeAllowed","writeConfirmationAvailable","releaseAllowed","mayResumeScanning",
            "stockReleaseAllowed","localDeletionAllowed","evidenceUploadAllowed"))result.put(key,false);
        return result;
    }
}
