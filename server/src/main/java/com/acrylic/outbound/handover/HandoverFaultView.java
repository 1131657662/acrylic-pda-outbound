package com.acrylic.outbound.handover;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Verified fault evidence and an opt-in UI hint; command authority stays in the write guards. */
@Component
final class HandoverFaultView {
    private final HandoverStopStore origins;
    private final HandoverFaultArchive archives;
    private final boolean enabled;
    private final boolean attachmentWrites;

    HandoverFaultView(HandoverStopStore origins, HandoverFaultArchive archives,
            @Value("${outbound.handover.fault.enabled:false}") boolean enabled,
            @Value("${outbound.handover.attachments.enabled:false}") boolean attachmentWrites) {
        this.origins = origins; this.archives = archives; this.enabled = enabled;
        this.attachmentWrites = attachmentWrites;
    }

    JsonNode read(JsonNode context, HandoverCommand.Actor actor) {
        var result = object().put("entryAvailable", false).put("attachmentsAvailable", false).putNull("sourceDeviceId").putNull("receipt");
        if ("FAULT_FENCED".equals(text(context, "stopState"))) {
            var origin = origins.origin(uuid(context, "caseId"));
            result.set("receipt", archives.verify(context, origin, number(context, "inputHighWatermark")).receipt());
            result.put("sourceDeviceId", origin.device().toString());
        } else if (eligible(context, actor)) {
            var origin = origins.origin(uuid(context, "caseId"));
            result.put("sourceDeviceId", origin.device().toString());
            result.put("entryAvailable", !actor.id().equals(origin.operator()));
            result.put("attachmentsAvailable", attachmentWrites && !actor.id().equals(origin.operator()));
        }
        return result;
    }

    private boolean eligible(JsonNode context, HandoverCommand.Actor actor) {
        return enabled && actor.pda() == null && actor.permissions().contains("order:handover")
            && !actor.id().toString().equals(text(context, "requestedBy"))
            && java.util.Set.of("TEMP", "FORMAL").contains(text(context, "sourceKind")) && "REQUESTED".equals(text(context, "stopState"))
            && "EVIDENCE_PENDING".equals(text(context, "status"));
    }
}
