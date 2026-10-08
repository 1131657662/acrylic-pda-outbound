package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.devices.DeviceStore;
import com.fasterxml.jackson.databind.JsonNode;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Supervisor fault fence on the existing command transaction; does not issue an original-device acknowledgement. */
final class HandoverFaultOperation implements HandoverCommand.Operation {
    private final HandoverStopStore origins;
    private final HandoverFaultArchive archives;
    private final HandoverTemporaryApprovalEvidence validation;
    private final HandoverFaultStore fences;
    private final HandoverStore cases;
    private final DeviceStore devices;

    HandoverFaultOperation(HandoverStopStore origins, HandoverFaultArchive archives,
            HandoverTemporaryApprovalEvidence validation, HandoverFaultStore fences, HandoverStore cases, DeviceStore devices) {
        this.origins = origins; this.archives = archives; this.validation = validation;
        this.fences = fences; this.cases = cases; this.devices = devices;
    }

    @Override public String commandType() { return "FAULT_FENCE"; }
    @Override public boolean requiresApproval() { return true; }

    @Override public JsonNode execute(HandoverCommand.Write write) {
        var facts = write.caseFacts(); var origin = origins.origin(write.caseId());
        var input = HandoverFaultInput.read(write.command().body(), write.recordedAt());
        requireActor(write, origin);
        if (!"TEMP".equals(text(facts, "sourceKind")) || !"REQUESTED".equals(text(facts, "stopState"))
                || !"EVIDENCE_PENDING".equals(text(facts, "status")) || !input.device().equals(origin.device()))
            throw HandoverCommand.stale();
        var body = archives.read(input, origin); validation.require(origin, body);
        var device = devices.find(origin.device(), false);
        if (!device.warehouseId().equals(origin.warehouse())) throw HandoverCommand.missing();
        devices.status(device, "DISABLED");
        long version = Math.addExact(Long.parseLong(device.rowVersion()), 1);
        fences.insert(write, origin, version);
        cases.advanceCase(write.caseId(), write.caseVersion(), "EVIDENCE_PENDING", null, input.archive().inputHigh(),
            "FAULT_FENCED", "COMPLETE", "ABORTED", null, null, null, null, null, uuid(write.actor(), "principalId"), write.recordedAt());
        var decision = object().put("caseId", write.caseId().toString()).put("status", "FAULT_FENCED").put("stopKind", "FAULT_FENCE")
            .put("sourceDeviceId", origin.device().toString()).put("disabledDeviceVersion", Long.toString(version))
            .put("oldDeviceAcknowledged", false).put("payloadSha256", sha(body));
        decision.set("source", source(origin)); decision.set("archivedEvidence", input.archive().descriptor(origin));
        decision.set("physicalControl", write.command().body().path("physicalControl"));
        notify(write, origin, decision);
        return decision;
    }

    private void requireActor(HandoverCommand.Write write, HandoverStopStore.Origin origin) {
        boolean supervisor = false;
        for (var permission : write.actor().path("permissionSnapshot").path("effectivePermissions"))
            supervisor |= "order:handover".equals(permission.asText());
        if (!supervisor || write.actor().hasNonNull("deviceId")) throw HandoverCommand.denied();
        String actor = text(write.actor(), "principalId");
        if (actor.equals(text(write.caseFacts(), "requestedBy")) || actor.equals(origin.operator().toString()))
            throw new BusinessException(403, "HANDOVER_SELF_APPROVAL_DENIED", "故障换机须由独立主管确认");
    }

    private JsonNode source(HandoverStopStore.Origin origin) {
        return object().put("sourceKind", "TEMP").putNull("assignmentId").put("warehouseId", origin.warehouse().toString())
            .put("scopeId", origin.scope().toString()).put("submissionId", origin.submission().toString()).put("orderId", origin.order().toString())
            .put("deviceId", origin.device().toString()).put("operatorId", origin.operator().toString()).put("sourceRecoveryEpoch", origin.epoch())
            .put("workRound", Long.toString(origin.round())).put("authorizationId", origin.temporary().authorization().toString())
            .put("creatorDeviceId", origin.temporary().creator().toString()).put("clientOrderId", origin.temporary().client().toString());
    }

    private void notify(HandoverCommand.Write write, HandoverStopStore.Origin origin, JsonNode decision) {
        var payload = object().put("caseId", write.caseId().toString()).put("orderId", origin.order().toString())
            .put("scopeId", origin.scope().toString()).put("status", cases.orderStatus(origin.order()))
            .put("stopKind", "FAULT_FENCE").put("sourceDeviceId", origin.device().toString()).put("oldDeviceAcknowledged", false);
        long epoch = cases.serverRecoveryEpoch();
        cases.insertOutbox(origin.order(), epoch, cases.nextOutboxEntityVersion(origin.order(), epoch), "HANDOVER_FAULT_FENCED",
            canonical(payload), write.recordedAt());
    }
}
