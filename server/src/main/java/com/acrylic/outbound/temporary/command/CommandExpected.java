package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

public final class CommandExpected {
    private static final List<String> BASE = List.of("canonicalOrderId","mappingVersion","currentSubmissionId","scopeRound",
        "slotVersion","decisionVersion","receivedPayloadSha256","declaredUploadSha256","planSha256","targetRevision");
    private static final List<String> OPTIONAL = List.of("candidateContextSha256","evidenceSetId","evidenceSetSha256","verificationId",
        "verificationVersion","verificationAlgorithmVersion","dispositionManifestSha256","dispositionManifestVersion","retainedManifestSha256","retainedManifestVersion");
    private static final List<String> EVIDENCE = List.of("evidenceSetId","evidenceSetSha256","verificationId",
        "verificationVersion","verificationAlgorithmVersion");
    private static final List<String> MANIFEST = List.of("dispositionManifestSha256","dispositionManifestVersion");
    private CommandExpected() { }

    public static ObjectNode current(CommandContext facts,String userVersion) {
        var result = SubmissionJson.object(); var context = facts.json();
        BASE.forEach(key -> result.set(key,context.get(key)));
        result.put("userAuthVersion",userVersion); OPTIONAL.forEach(result::putNull); return result;
    }
    static void validate(CommandInput input,CommandContext facts,CommandIdentity.Actor actor) {
        validate(input,facts,actor,false);
    }
    static void validate(CommandInput input,CommandContext facts,CommandIdentity.Actor actor,boolean candidateContext) {
        applicability(input,candidateContext);
        var expected = current(facts,actor.authVersion());
        if (input.kind().equals("PHYSICAL_DISPOSITION")) {
            EVIDENCE.forEach(key -> expected.set(key,facts.json().get(key)));
            // Manifest contents and digest are checked by the disposition operation, not by source facts.
            MANIFEST.forEach(key -> expected.set(key,input.expected().get(key)));
        }
        if (candidateContext) {
            if (!input.expected().path("candidateContextSha256").isTextual()) throw CommandErrors.confirmationRequired();
            expected.set("candidateContextSha256",input.expected().get("candidateContextSha256"));
        }
        if (!expected.equals(input.expected())) throw CommandErrors.stale();
        source(input,facts,actor);
    }

    private static void applicability(CommandInput input,boolean candidateContext) {
        if (!Set.of("NORMAL_CONFIRM","SHORT_APPROVE","LINK","REJECT","PHYSICAL_DISPOSITION").contains(input.kind()))
            throw CommandErrors.unsupported();
        boolean physical = input.kind().equals("PHYSICAL_DISPOSITION");
        for (String key : OPTIONAL) {
            boolean evidence = EVIDENCE.contains(key) || MANIFEST.contains(key);
            if (evidence && physical == input.expected().get(key).isNull()) throw CommandErrors.unsupported();
            boolean applicable = physical && evidence || candidateContext && key.equals("candidateContextSha256");
            if (!applicable && !input.expected().get(key).isNull()) throw CommandErrors.unsupported();
        }
    }

    private static void source(CommandInput input,CommandContext facts,CommandIdentity.Actor actor) {
        var body = input.body(); var context = facts.json();
        for (String key : List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))
            if (!body.get(key).equals(context.get(key))) throw CommandErrors.stale();
        for (String key : List.of("creatorDeviceId","clientOrderId"))
            if (!body.get("source").get(key).equals(context.get(key))) throw CommandErrors.stale();
        if (!facts.id("warehouseId").equals(actor.warehouse()) || !facts.id("submissionId").equals(facts.id("currentSubmissionId"))
            || !facts.text("workRound").equals(facts.text("scopeRound"))) throw CommandErrors.stale();
        boolean absentSlot = input.kind().equals("PHYSICAL_DISPOSITION") && facts.text("slotVersion") == null
            && facts.id("slotScopeId") == null && facts.id("slotSubmissionId") == null && facts.text("slotRound") == null;
        if (facts.id("canonicalOrderId") != null && !absentSlot
            && (!Objects.equals(facts.id("scopeId"),facts.id("slotScopeId"))
            || !Objects.equals(facts.id("submissionId"),facts.id("slotSubmissionId"))
            || !Objects.equals(facts.text("workRound"),facts.text("slotRound")))) throw CommandErrors.stale();
    }
}
