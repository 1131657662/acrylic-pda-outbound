package com.acrylic.outbound.handover;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Reads attested TEMP original bytes without recursively invoking qualification-history validation. */
@Component
final class HandoverTemporaryOriginal {
    private final HandoverTemporaryStopEvidence stopped;
    private final HandoverTemporaryRoots roots;
    private final HandoverTemporaryPayload payload;

    HandoverTemporaryOriginal(HandoverTemporaryStopEvidence stopped, HandoverTemporaryRoots roots,
            HandoverTemporaryPayload payload) {
        this.stopped = stopped; this.roots = roots; this.payload = payload;
    }

    JsonNode read(HandoverStopCommand command, HandoverStopStore.Origin origin) {
        stopped.verify(command, origin);
        var body = payload.read(roots.require(command, origin), origin);
        if (number(body, "captureHighWatermark") != command.inputHigh()) throw HandoverTemporaryRoots.changed();
        roots.events(body, command, origin);
        return body;
    }
}
