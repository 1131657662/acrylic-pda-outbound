package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.temporary.continuation.TemporaryHistoryStep;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Builds one handover history frame without calling the approval or cumulative payload consumers. */
@Component
public final class TemporaryHandoverHistory {
    private final TemporaryHandoverIntakeFacts facts;
    private final TemporaryHandoverPredecessor predecessors;
    private final SubmissionStore submissions;
    private final TemporarySubmissionPlan plans;
    private final TemporaryHandoverEvidence evidence;

    TemporaryHandoverHistory(TemporaryHandoverIntakeFacts facts, TemporaryHandoverPredecessor predecessors,
            SubmissionStore submissions, TemporarySubmissionPlan plans, TemporaryHandoverEvidence evidence) {
        this.facts = facts; this.predecessors = predecessors; this.submissions = submissions;
        this.plans = plans; this.evidence = evidence;
    }

    public TemporaryHistoryStep step(SubmissionStore.Head head, JsonNode body) {
        var request = TemporaryHandoverControl.read(body);
        equal(request.control(), head.control(), "历史交接控制头不符");
        equal(request.predecessor(), facts.reference(head.control().id()), "历史交接不可变引用不符");
        var source = facts.readHistory(request);
        equal(head.actor(), source.ticket().targetUserId(), "历史交接操作人不符");
        equal(head.warehouse(), source.warehouse(), "历史交接仓库不符");
        var original = predecessors.read(source); var previous = submissions.required(source.ticket().submissionId());
        var input = new TemporaryHandoverEvidence.Input(previous, head, plans.validate(previous, original),
            plans.validate(head, body), original);
        return new Step(source.ticket().scopeId(), input, body, evidence);
    }

    TemporaryHandoverEvidence.Input input(TemporaryHandoverIntakeFacts.Facts source, SubmissionStore.Head head, JsonNode body) {
        var original = predecessors.read(source); var previous = submissions.required(source.ticket().submissionId());
        return new TemporaryHandoverEvidence.Input(previous, head, plans.validate(previous, original), plans.validate(head, body), original);
    }

    private record Step(java.util.UUID scope, TemporaryHandoverEvidence.Input input, JsonNode body, TemporaryHandoverEvidence evidence)
            implements TemporaryHistoryStep {
        public SubmissionStore.Head previous() { return input.previous(); }
        public JsonNode original() { return input.original(); }
        public TemporarySubmissionPlan.Plan oldPlan() { return input.oldPlan(); }
        public TemporaryEvidenceOrigins validate(TemporaryEvidenceOrigins origins) { return evidence.validate(input, body, origins); }
    }
}
