package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.handover.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Creates frozen commands from the real source/case expected context. */
final class HandoverRollbackCommands {
    private final HandoverRollbackFixture fixture;
    private final HandoverStore store;
    private final HandoverCommandService service;

    HandoverRollbackCommands(HandoverRollbackFixture fixture, HandoverStore store, HandoverCommandService service) {
        this.fixture = fixture; this.store = store; this.service = service;
    }

    void prepare() {
        var request = base("REQUEST").put("scopeId", fixture.scope.toString());
        var expected = request.putObject("expected");
        var facts = store.scopeFacts(fixture.scope);
        for (String key : List.of("scopeId", "canonicalOrderId", "mappingVersion", "fromWorkRound")) expected.set(key, facts.get(key));
        expected.put("userAuthVersion", "1");
        var receipt = SubmissionJson.parse(call(request, fixture.requester));
        fixture.stopped(UUID.fromString(receipt.path("businessDecision").path("caseId").asText()));
        var approve = kase("APPROVE").put("stopState", "DEVICE_ACKED").put("integrityState", "COMPLETE")
            .put("originalResultState", "ABORTED").put("physicalState", "MATCHED").put("inputHighWatermark", "7");
        approve.putArray("checklist").addObject().put("baleId", fixture.bale.toString()).put("deliveryCode", "D1")
            .put("state", "RETAINED").put("dispatchCycle", Long.toString(fixture.cycle));
        call(approve, fixture.approver);
        call(target("PREPARE"), fixture.approver);
    }

    ObjectNode activate() { return target("ACTIVATE"); }

    byte[] call(ObjectNode command, SessionUser actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, "unused", List.of()));
        try {
            return service.execute(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8), "rollback-" + UUID.randomUUID());
        } finally { SecurityContextHolder.clearContext(); }
    }

    private ObjectNode base(String kind) {
        return SubmissionJson.object().put("commandVersion", 1).put("commandType", kind)
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("reason", "isolated atomicity test").put("warehouseId", fixture.warehouse.toString());
    }

    private ObjectNode kase(String kind) {
        var command = base(kind).put("caseId", fixture.caseId.toString());
        var expected = command.putObject("expected");
        var facts = store.caseFacts(fixture.caseId);
        for (String key : List.of("caseId", "caseVersion", "status")) expected.set(key, facts.get(key));
        expected.put("userAuthVersion", "1");
        return command;
    }

    private ObjectNode target(String kind) {
        return kase(kind).put("targetDeviceId", fixture.device.toString()).put("targetUserId", fixture.operator.toString());
    }
}
