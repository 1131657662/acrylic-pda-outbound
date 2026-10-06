package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.temporary.command.TemporaryRetainedManifest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Component;

/** Rebuilds both frozen plans and snapshots from their independent fixed preparation data. */
@Component
public class TemporaryRoundPayload {
    private final TemporaryRoundPredecessor predecessor;private final SubmissionStore submissions;
    private final TemporarySubmissionPlan plans;private final TemporarySubmissionSnapshot snapshots;private final TemporaryRoundEvidence evidence;
    public TemporaryRoundPayload(TemporaryRoundPredecessor predecessor,SubmissionStore submissions,TemporarySubmissionPlan plans,
        TemporarySubmissionSnapshot snapshots,TemporaryRoundEvidence evidence) {
        this.predecessor=predecessor;this.submissions=submissions;this.plans=plans;this.snapshots=snapshots;this.evidence=evidence;
    }
    public Validated validate(Input input,JsonNode body) {
        var original=predecessor.read(input.request(),input.source());var previous=submissions.required(input.source().previous());
        var oldPlan=plans.validate(previous,original);var plan=plans.validate(input.head(),body);
        evidence.validate(new TemporaryRoundEvidence.Input(input.head(),previous,plan,oldPlan,original,input.source(),input.retained()),body);
        snapshots.validate(oldPlan,original);
        return new Validated(plan,snapshots.validate(plan,body));
    }
    public record Input(TemporaryRoundControl request,TemporaryRoundFacts.Source source,SubmissionStore.Head head,
        List<TemporaryRetainedManifest.Expected> retained) { }
    public record Validated(TemporarySubmissionPlan.Plan plan,SubmissionSnapshot.Validated snapshot) { }
}
