package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

public final class CommandExpected {
    private static final List<String> BASE=List.of("canonicalOrderId","mappingVersion","currentSubmissionId","scopeRound",
        "slotVersion","decisionVersion","receivedPayloadSha256","declaredUploadSha256","planSha256","targetRevision");
    private static final List<String> UNIMPLEMENTED=List.of("candidateContextSha256","evidenceSetId","evidenceSetSha256","verificationId",
        "verificationVersion","verificationAlgorithmVersion","dispositionManifestSha256","dispositionManifestVersion","retainedManifestSha256","retainedManifestVersion");
    private CommandExpected() { }
    public static ObjectNode current(CommandContext facts,String userVersion) {
        var result=SubmissionJson.object();var context=facts.json();BASE.forEach(k->result.set(k,context.get(k)));
        result.put("userAuthVersion",userVersion);UNIMPLEMENTED.forEach(result::putNull);return result;
    }
    static void validate(CommandInput input,CommandContext facts,CommandIdentity.Actor actor) {
        if(!Set.of("NORMAL_CONFIRM","SHORT_APPROVE").contains(input.kind()))throw CommandErrors.unsupported();
        for(String key:UNIMPLEMENTED)if(!input.expected().get(key).isNull())throw CommandErrors.unsupported();
        if(!current(facts,actor.authVersion()).equals(input.expected()))throw CommandErrors.stale();
        var body=input.body();var context=facts.json();
        for(String key:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))
            if(!body.get(key).equals(context.get(key)))throw CommandErrors.stale();
        for(String key:List.of("creatorDeviceId","clientOrderId"))
            if(!body.get("source").get(key).equals(context.get(key)))throw CommandErrors.stale();
        if(!facts.id("warehouseId").equals(actor.warehouse()) || !facts.id("submissionId").equals(facts.id("currentSubmissionId"))
            || !facts.text("workRound").equals(facts.text("scopeRound")))throw CommandErrors.stale();
        if(facts.id("canonicalOrderId")!=null && (!Objects.equals(facts.id("scopeId"),facts.id("slotScopeId"))
            || !Objects.equals(facts.id("submissionId"),facts.id("slotSubmissionId"))
            || !Objects.equals(facts.text("workRound"),facts.text("slotRound"))))throw CommandErrors.stale();
    }
}
