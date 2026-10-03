package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.handover.HandoverStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;

/** Frozen commands for source revalidation, based on the actual original stop package. */
final class HandoverSourceCommands {
    private final HandoverRollbackFixture fixture;
    private final HandoverStore store;
    HandoverSourceCommands(HandoverRollbackFixture fixture, HandoverStore store) { this.fixture = fixture; this.store = store; }
    ObjectNode request() {
        var command = base("REQUEST").put("scopeId", fixture.scope.toString()); var facts = store.scopeFacts(fixture.scope);
        var expected = command.putObject("expected");
        for (String key : List.of("scopeId", "canonicalOrderId", "mappingVersion", "fromWorkRound")) expected.set(key, facts.get(key));
        expected.put("userAuthVersion", "1"); return command;
    }
    ObjectNode command(String kind) {
        var command = base(kind).put("caseId", fixture.caseId.toString()); var facts = store.caseFacts(fixture.caseId);
        var expected = command.putObject("expected");
        for (String key : List.of("caseId", "caseVersion", "status")) expected.set(key, facts.get(key));
        expected.put("userAuthVersion", "1");
        if (kind.equals("APPROVE")) fixture.source.approveFields(command);
        else command.put("targetDeviceId", fixture.device.toString()).put("targetUserId", fixture.operator.toString());
        return command;
    }
    private ObjectNode base(String kind) {
        return SubmissionJson.object().put("commandVersion", 1).put("commandType", kind)
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("reason", "isolated source revalidation test").put("warehouseId", fixture.warehouse.toString());
    }
}
