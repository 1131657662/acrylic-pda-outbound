package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.TemporaryStore;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Component;

/** Current observations never revoke a committed accounting fact or grant a production capability. */
@Component
public class CommandDecisionEffect {
    private final TemporaryDecisionCandidates candidates;private final SubmissionFiles files;
    private final TemporaryStore authorizations;private final TemporaryConfirmationSource source;
    public CommandDecisionEffect(TemporaryDecisionCandidates candidates,SubmissionFiles files,
        TemporaryStore authorizations,TemporaryConfirmationSource source) {
        this.candidates=candidates;this.files=files;this.authorizations=authorizations;this.source=source;
    }
    void observations(CommandContext now,JsonNode original,List<String> reasons) {
        try { reasons.addAll(source.reasons(authorizations.get(now.id("authorizationId")))); }
        catch(RuntimeException unavailable) { reasons.add("SOURCE_ELIGIBILITY_UNAVAILABLE"); }
        try {
            var body=SubmissionJson.parse(files.read(original.path("originalContext").path("receivedPayloadSha256").asText()));
            var current=candidates.snapshot(now.id("warehouseId"),now.id("submissionId"),body);
            var stored=original.path("businessDecision").path("candidates");
            if(CommandDecision.kind(original).equals("LINK") && original.at("/businessDecision/submissionReceipt/applyState").asText().equals("APPLIED")) {
                // The applied link itself makes its own target carry this source, which moves that candidate's
                // readiness fingerprint. Only a change of the reviewed extent is a new duplicate to look at.
                for(String key:List.of("policyVersion","fromDate","throughDate","total"))
                    if(!current.path(key).equals(stored.path(key)))reasons.add("CANDIDATE_CONTEXT_CHANGED:"+key);
            } else if(!current.equals(stored)) reasons.add("CANDIDATE_CONTEXT_CHANGED");
        } catch(RuntimeException unavailable) { reasons.add("CANDIDATE_CONTEXT_UNAVAILABLE"); }
        if(CommandDecision.kind(original).equals("LINK")) {
            var link=original.path("businessDecision").path("submissionReceipt");
            if(link.path("applyState").asText().equals("APPLIED")
                && (!now.text("mappingVersion").equals(link.path("mappingVersion").asText())
                    || !java.util.Objects.equals(now.text("canonicalOrderId"),link.path("targetOrderId").asText())))
                reasons.add("LINK_SUPERSEDED_BY_NEW_MAPPING");
        }
    }
    void complete(JsonNode original,ObjectNode result,List<String> reasons) {
        String kind=CommandDecision.kind(original);
        boolean applied=kind.equals("LINK") && original.at("/businessDecision/submissionReceipt/applyState").asText().equals("APPLIED");
        // A link recorded from Web and later read from a PDA only differs in the reading session, and an applied
        // link changes exactly the canonical/mapping/slot context it recorded; neither loses the decision's force.
        boolean superseded=reasons.stream().anyMatch(reason->!reason.startsWith("ACTOR_CONTEXT_CHANGED:")
            && !(applied && reason.startsWith("CONTEXT_CHANGED:")));
        String evaluation=kind.equals("NORMAL_CONFIRM")?"COMMITTED":kind.equals("LINK")
            ?(superseded?"CONTEXT_CHANGED":applied?"LINK_IN_FORCE":"LINK_DECISION_CURRENT")
            :reasons.isEmpty()?"CURRENT_CONTEXT_MATCHES":"CONTEXT_CHANGED";
        result.put("evaluation",evaluation);
        if(evaluation.equals("COMMITTED"))reasons.add("ORIGINAL_SUCCESS_COMMITTED");
        if(kind.equals("LINK") && !applied)reasons.add("LINK_POINTER_SWITCH_NOT_APPLIED");
        reasons.add("PRODUCTION_OPERATIONS_DISABLED");
        reasons.add("EVIDENCE_EVALUATION_NOT_IMPLEMENTED");
    }
}
