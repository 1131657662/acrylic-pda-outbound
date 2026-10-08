package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.SubmissionStore;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** A verified immutable predecessor link whose evidence can be replayed in chronological order. */
public interface TemporaryHistoryStep {
    UUID scope();
    SubmissionStore.Head previous();
    JsonNode original();
    TemporarySubmissionPlan.Plan oldPlan();
    TemporaryEvidenceOrigins validate(TemporaryEvidenceOrigins origins);
}
