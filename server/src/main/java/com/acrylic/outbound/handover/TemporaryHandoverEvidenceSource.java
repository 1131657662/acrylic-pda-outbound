package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.SubmissionStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Revalidates immutable registration, signed activation and original evidence before E2A consumes a prefix. */
@Component
public final class TemporaryHandoverEvidenceSource {
    private final TemporaryHandoverIntakeFacts facts;
    private final TemporaryHandoverPayload payloads;
    private final SubmissionStore submissions;

    TemporaryHandoverEvidenceSource(TemporaryHandoverIntakeFacts facts,TemporaryHandoverPayload payloads,
            SubmissionStore submissions) {
        this.facts=facts;this.payloads=payloads;this.submissions=submissions;
    }

    public JsonNode predecessor(UUID submissionId,JsonNode body) {
        var request=TemporaryHandoverControl.read(body);
        equal(request.control().id(),submissionId,"交接证据批次不符");
        equal(request.predecessor(),facts.reference(submissionId),"交接证据不可变登记不符");
        var source=facts.read(request);
        var head=submissions.required(submissionId);
        equal(head.actor(),source.ticket().targetUserId(),"交接证据原操作人不符");
        equal(head.warehouse(),source.warehouse(),"交接证据仓库不符");
        return payloads.validate(source,head,body).predecessor();
    }
}
