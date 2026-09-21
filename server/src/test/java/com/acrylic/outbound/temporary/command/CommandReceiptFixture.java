package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

final class CommandReceiptFixture {
    private CommandReceiptFixture() { }
    static CommandWrite write() {
        var c=SubmissionJson.object();
        for(String key:List.of("scopeId","warehouseId","creatorDeviceId","clientOrderId","submissionId","authorizationId","originalOperatorId","mappingId"))
            c.put(key,UUID.randomUUID().toString());
        c.set("freezeDeviceId",c.get("creatorDeviceId"));c.set("currentSubmissionId",c.get("submissionId"));
        for(String key:List.of("workRound","authorizationEpoch","authorizationUserVersion","authorizationDeviceVersion","sourceRecoveryEpoch","scopeRound"))c.put(key,"1");
        c.put("mappingVersion","0").put("decisionVersion","0").put("status","ABORTED");
        for(String key:List.of("canonicalOrderId","mappingOrderId","slotVersion","slotScopeId","slotSubmissionId","slotRound","targetRevision",
            "receivedPayloadSha256","declaredUploadSha256","planSha256"))c.putNull(key);
        var context=new CommandContext(c);var command=command(context);var actor=actor(c);
        return new CommandWrite(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Instant.parse("2026-09-21T00:00:00.123Z"),
            CommandInput.parse(command.toString().getBytes(StandardCharsets.UTF_8)),context,actor);
    }
    private static ObjectNode command(CommandContext c) {
        var body=SubmissionJson.object().put("commandVersion",1).put("commandType","NORMAL_CONFIRM")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","");
        for(String key:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))body.set(key,c.json().get(key));
        body.putObject("source").put("kind","TEMP").put("creatorDeviceId",c.text("creatorDeviceId")).put("clientOrderId",c.text("clientOrderId"));
        body.set("expected",CommandExpected.current(c,"1"));return body;
    }
    private static ObjectNode actor(ObjectNode context) {
        var actor=SubmissionJson.object().put("principalId",UUID.randomUUID().toString()).put("userAuthVersion","1")
            .putNull("deviceId").putNull("sessionId").putNull("deviceVersion");
        var permissions=actor.putObject("permissionSnapshot");permissions.putArray("requiredPermissions").add("temporary_order:confirm");
        permissions.putArray("effectivePermissions").add("temporary_order:confirm");permissions.putArray("warehouseIds").add(context.get("warehouseId").textValue());
        permissions.putArray("roleIds").add(UUID.randomUUID().toString());return actor;
    }
}
