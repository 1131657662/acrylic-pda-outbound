package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.handover.TemporaryHandoverEvidenceSource;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.continuation.TemporaryRoundEvidenceSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Component;

/** Separates a revalidated mixed-round prefix from this device's own STOP facts. */
@Component
public final class SourceEvidenceIntakePayload {
    private final TemporaryHandoverEvidenceSource handover;
    private final TemporaryRoundEvidenceSource continuation;

    public SourceEvidenceIntakePayload(TemporaryHandoverEvidenceSource handover,TemporaryRoundEvidenceSource continuation) {
        this.handover=handover;this.continuation=continuation;
    }

    Checked require(SourceEvidenceIntakePageObjects.Binding binding,JsonNode body) {
        var prefix=body.has("temporaryHandover")?handover.predecessor(binding.submissionId(),body)
            :body.has("temporaryContinuation")?continuation.predecessor(binding.submissionId(),body):null;
        long high=SourceEvidenceIntakeOrderScanSequence.require(body,binding.creatorDeviceId(),prefix);
        if(prefix==null)return new Checked(body,high);
        ObjectNode own=body.deepCopy();
        for(String field:List.of("captures","scans","events")) {
            var current=SubmissionJson.array(body,field);var original=SubmissionJson.array(prefix,field);
            var suffix=own.putArray(field);
            for(int index=original.size();index<current.size();index++)suffix.add(current.get(index).deepCopy());
        }
        return new Checked(own,high);
    }

    record Checked(JsonNode own,long orderHigh) { }
}
