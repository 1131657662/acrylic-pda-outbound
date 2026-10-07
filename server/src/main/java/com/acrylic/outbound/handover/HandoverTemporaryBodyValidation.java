package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.temporary.continuation.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Reuses original TEMP qualification and frozen-plan validation for first and subsequent source rounds. */
@Component
final class HandoverTemporaryBodyValidation {
    private final TemporarySubmissionPlan plans;
    private final TemporarySubmissionEvidence evidence;
    private final TemporarySubmissionSnapshot snapshots;
    private final TemporaryRoundFacts rounds;
    private final TemporaryRoundPayload payloads;

    HandoverTemporaryBodyValidation(TemporarySubmissionPlan plans, TemporarySubmissionEvidence evidence,
            TemporarySubmissionSnapshot snapshots, TemporaryRoundFacts rounds, TemporaryRoundPayload payloads) {
        this.plans = plans; this.evidence = evidence; this.snapshots = snapshots; this.rounds = rounds; this.payloads = payloads;
    }

    void require(SubmissionStore.Head head, JsonNode body) {
        if (head.control().round() == 1) {
            var plan = plans.validate(head, body); evidence.validate(head, plan, body); snapshots.validate(plan, body);
            return;
        }
        var request = TemporaryRoundControl.read(body);
        equal(request.predecessor(), rounds.reference(head.control().id()), "原临时续作不可变引用不符");
        var source = rounds.read(request, new SubmissionAccess.Actor(head.actor(), head.warehouse(), ""));
        payloads.validate(new TemporaryRoundPayload.Input(request, source, head, rounds.retained(request, source)), body);
    }
}
