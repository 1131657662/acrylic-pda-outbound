package com.acrylic.outbound.temporary;

import com.acrylic.outbound.submissions.SubmissionStore;
import com.fasterxml.jackson.databind.JsonNode;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** The exact prefix boundary is established by TemporaryRoundEvidence before validating a continuation. */
public record TemporaryEvidenceOrigins(Origin current,Origin previous,int captures,int events) {
    public static TemporaryEvidenceOrigins initial(SubmissionStore.Head head,TemporarySubmissionPlan.Plan plan) {
        return new TemporaryEvidenceOrigins(new Origin(head.control().source(),plan.authorization()),null,0,0);
    }
    public Origin capture(int index) { return index<captures?previous:current; }
    public Origin event(int index) { return index<events?previous:current; }
    public TemporaryStore.Stored authorization(JsonNode capture) {
        String id=text(capture,"authorizationId");
        if(id.equals(current.authorization().id().toString()))return current.authorization();
        require(previous!=null && id.equals(previous.authorization().id().toString()),"保存原资格不属于本轮证据链");
        return previous.authorization();
    }
    public record Origin(JsonNode source,TemporaryStore.Stored authorization) { }
}
