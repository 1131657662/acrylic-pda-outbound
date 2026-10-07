package com.acrylic.outbound.handover;

import com.acrylic.outbound.submissions.SubmissionStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Supplies the verified original TEMP body to supervisor consumers without granting a new work qualification. */
@Component
final class HandoverTemporaryApprovalEvidence {
    private final HandoverTemporaryStopEvidence stopped;
    private final HandoverTemporaryRoots roots;
    private final HandoverTemporaryPayload payload;
    private final HandoverTemporaryBodyValidation validation;
    private final SubmissionStore submissions;

    HandoverTemporaryApprovalEvidence(HandoverTemporaryStopEvidence stopped, HandoverTemporaryRoots roots,
            HandoverTemporaryPayload payload, HandoverTemporaryBodyValidation validation, SubmissionStore submissions) {
        this.stopped = stopped; this.roots = roots; this.payload = payload; this.validation = validation; this.submissions = submissions;
    }

    JsonNode read(HandoverStopCommand command, HandoverStopStore.Origin origin) {
        stopped.verify(command, origin);
        var verified = roots.require(command, origin);
        var body = payload.read(verified, origin);
        if (number(body, "captureHighWatermark") != command.inputHigh()) throw HandoverTemporaryRoots.changed();
        roots.events(body, command, origin);
        validation.require(submissions.required(origin.submission()), body);
        return body;
    }
}
