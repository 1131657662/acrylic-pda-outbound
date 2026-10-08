package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.*;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Revalidates both qualifications and the exact immutable predecessor before accepting any new fact. */
@Component
final class TemporaryHandoverPayload {
    private final TemporaryHandoverHistory handovers;
    private final com.acrylic.outbound.temporary.continuation.TemporaryRoundHistory history;
    private final TemporaryHandoverEvidence evidence;
    private final TemporarySubmissionSnapshot snapshots;
    TemporaryHandoverPayload(TemporaryHandoverHistory handovers,
        com.acrylic.outbound.temporary.continuation.TemporaryRoundHistory history,
        TemporaryHandoverEvidence evidence,TemporarySubmissionSnapshot snapshots) {
        this.handovers=handovers;this.history=history;this.evidence=evidence;this.snapshots=snapshots;
    }
    Checked validate(TemporaryHandoverIntakeFacts.Facts facts,SubmissionStore.Head head,JsonNode body) {
        var input=handovers.input(facts,head,body);
        var origins=history.read(input.previous(),input.original(),input.oldPlan());
        evidence.validate(input,body,origins);
        return new Checked(input.plan(),snapshots.validate(input.plan(),body),input.original().deepCopy());
    }
    record Checked(TemporarySubmissionPlan.Plan plan,SubmissionSnapshot.Validated snapshot,JsonNode predecessor) { }
}
