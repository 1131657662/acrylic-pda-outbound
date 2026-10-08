package com.acrylic.outbound.temporary.continuation;

import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Revalidates the immutable R06 registration and full history before E2A separates its prefix. */
@Component
public final class TemporaryRoundEvidenceSource {
    private final TemporaryRoundFacts facts;
    private final TemporaryRoundPayload payloads;
    private final TemporaryRoundPredecessor originals;
    private final SubmissionStore submissions;

    public TemporaryRoundEvidenceSource(TemporaryRoundFacts facts,TemporaryRoundPayload payloads,
            TemporaryRoundPredecessor originals,SubmissionStore submissions) {
        this.facts=facts;this.payloads=payloads;this.originals=originals;this.submissions=submissions;
    }

    public JsonNode predecessor(UUID submissionId,JsonNode body) {
        var request=TemporaryRoundControl.read(body);
        equal(request.control().id(),submissionId,"续作证据批次不符");
        equal(request.predecessor(),facts.reference(submissionId),"续作证据不可变登记不符");
        var head=submissions.required(submissionId);
        equal(request.control(),head.control(),"续作证据原控制头不符");
        var source=facts.read(request,new SubmissionAccess.Actor(head.actor(),head.warehouse(),""));
        var input=new TemporaryRoundPayload.Input(request,source,head,facts.retained(request,source));
        payloads.validate(input,body);
        return originals.read(request,source);
    }
}
