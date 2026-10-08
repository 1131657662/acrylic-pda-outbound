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
    private final TemporaryRoundHistory history;

    HandoverTemporaryBodyValidation(TemporarySubmissionPlan plans, TemporarySubmissionEvidence evidence,
            TemporarySubmissionSnapshot snapshots, TemporaryRoundHistory history) {
        this.plans = plans; this.evidence = evidence; this.snapshots = snapshots; this.history = history;
    }

    void require(SubmissionStore.Head head, JsonNode body) {
        var plan = plans.validate(head, body);
        evidence.validate(head, plan, body, history.read(head, body, plan));
        snapshots.validate(plan, body);
    }
}
