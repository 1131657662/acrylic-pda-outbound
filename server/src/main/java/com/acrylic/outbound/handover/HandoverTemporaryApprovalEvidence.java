package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.SubmissionStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Supplies the verified original TEMP body to supervisor consumers without granting a new work qualification. */
@Component
final class HandoverTemporaryApprovalEvidence {
    private final HandoverTemporaryOriginal originals;
    private final HandoverTemporaryBodyValidation validation;
    private final SubmissionStore submissions;

    HandoverTemporaryApprovalEvidence(HandoverTemporaryOriginal originals,
            HandoverTemporaryBodyValidation validation, SubmissionStore submissions) {
        this.originals = originals; this.validation = validation; this.submissions = submissions;
    }

    JsonNode read(HandoverStopCommand command, HandoverStopStore.Origin origin) {
        var body = originals.read(command, origin);
        require(origin, body);
        return body;
    }

    void require(HandoverStopStore.Origin origin, JsonNode body) {
        validation.require(submissions.required(origin.submission()), body);
    }
}
