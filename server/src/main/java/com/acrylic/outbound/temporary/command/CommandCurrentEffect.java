package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CommandCurrentEffect {
    private final CommandFacts facts;private final ServerState state;private final CommandIdentity identities;
    private final CommandSyntheticEligibility eligibility;
    public CommandCurrentEffect(CommandFacts facts,ServerState state,CommandIdentity identities,CommandSyntheticEligibility eligibility) {
        this.facts=facts;this.state=state;this.identities=identities;this.eligibility=eligibility;
    }
    public JsonNode evaluate(CommandRecord row,CommandIdentity.Actor actor) {
        var original=SubmissionJson.parse(row.bytes("original_response_bytes"));var server=state.current();
        var result=SubmissionJson.object().put("evaluatedAt",Instant.now().toString()).put("evaluation","UNKNOWN").put("applicable",false)
            .put("serverRecoveryEpoch",server.epoch()).put("serviceMode",server.mode());
        var reasons=new ArrayList<String>();
        if(!server.mode().equals("NORMAL"))reasons.add("SERVER_NOT_NORMAL");
        var command=CommandInput.parse(row.bytes("canonical_command_bytes"));
        if(!server.epoch().equals(command.text("transportRecoveryEpoch")))reasons.add("RECOVERY_EPOCH_CHANGED");
        if(!actor.authVersion().equals(original.path("actor").path("userAuthVersion").asText()))reasons.add("ACTOR_AUTH_VERSION_CHANGED");
        var currentActor=identities.snapshot(actor,command.kind());
        if(!Objects.equals(currentActor.get("permissionSnapshot"),original.path("actor").get("permissionSnapshot")))
            reasons.add("ACTOR_PERMISSIONS_CHANGED");
        for(String field:List.of("sessionId","deviceId","deviceVersion"))
            if(!Objects.equals(currentActor.get(field),original.path("actor").get(field)))reasons.add("ACTOR_CONTEXT_CHANGED:"+field);
        String required=CommandPermissions.execution(command.kind());
        if(required==null)reasons.add("COMMAND_EXECUTION_NOT_IMPLEMENTED");
        else if(!actor.permissions().contains(required))reasons.add("EXECUTION_PERMISSION_MISSING");
        current(row,original,result,reasons);
        reasons.addAll(List.of("BUSINESS_EVALUATION_NOT_IMPLEMENTED","CANDIDATE_EVALUATION_NOT_IMPLEMENTED","EVIDENCE_EVALUATION_NOT_IMPLEMENTED"));
        reasons.forEach(result.putArray("reasons")::add);result.set("capabilities",CommandReceiptCodec.capabilities());return result;
    }
    private void current(CommandRecord row,JsonNode original,ObjectNode result,List<String> reasons) {
        try {
            var now=facts.read(row.id("submission_id"));result.set("currentContext",now.json());var before=original.path("originalContext");
            reasons.addAll(eligibility.reasons(now));
            for(String field:List.of("canonicalOrderId","mappingVersion","currentSubmissionId","scopeRound","slotVersion",
                "receivedPayloadSha256","declaredUploadSha256","planSha256","targetRevision"))
                if(!Objects.equals(now.json().get(field),before.get(field)))reasons.add("CONTEXT_CHANGED:"+field);
            if(!now.text("decisionVersion").equals(original.path("resultingDecisionVersion").asText()))reasons.add("DECISION_VERSION_CHANGED");
            if(!now.text("submissionId").equals(now.text("currentSubmissionId")))reasons.add("SUBMISSION_NOT_CURRENT");
            if(Set.of("ABORTED","REJECTED","SUCCEEDED").contains(now.text("status")))reasons.add("SOURCE_TERMINAL");
        } catch(RuntimeException unavailable) { result.putNull("currentContext");reasons.add("CURRENT_CONTEXT_UNAVAILABLE"); }
    }
}
