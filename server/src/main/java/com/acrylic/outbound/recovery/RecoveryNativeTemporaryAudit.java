package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Binds a native frozen boundary to the same pinned source and its next signed device STOP. */
final class RecoveryNativeTemporaryAudit {
    private RecoveryNativeTemporaryAudit() { }

    static List<JsonNode> verify(RecoveryPinnedSource source, RecoveryOriginalEvidence.Request evidence) {
        var id = source.identity();
        var stopIdentity = new RecoverySourceIdentity(id.sourceKind(), id.warehouseId(), id.scopeId(), id.submissionId(),
            id.deviceId(), id.operatorId(), id.qualificationId(), id.canonicalOrderId(), id.creatorDeviceId(), id.clientOrderId(),
            id.sourceRecoveryEpoch(), id.workRound(), id.captureHighWatermark(), Math.addExact(id.auditThroughSeq(), 1),
            id.orderScanHighWatermark());
        require(evidence.expected().equals(stopIdentity), "RECOVERY_INITIAL_NATIVE_IDENTITY");
        var proof = new RecoveryOriginalEvidence().read(evidence);
        var payload = SubmissionJson.parse(source.original("payload"));
        require(proof.payloadSha256().equals(SubmissionJson.sha(payload)), "RECOVERY_INITIAL_NATIVE_PAYLOAD");
        var events = SubmissionJson.array(payload, "events");
        require(events.size() == id.auditThroughSeq(), "RECOVERY_INITIAL_NATIVE_BOUNDARY");
        return java.util.stream.StreamSupport.stream(events.spliterator(), false).<JsonNode>map(JsonNode::deepCopy).toList();
    }
}
