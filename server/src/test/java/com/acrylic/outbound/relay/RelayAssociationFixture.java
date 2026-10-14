package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

final class RelayAssociationFixture {
    static final Instant NOW=RelayPhysicalFixture.NOW;
    final RelayPhysicalFixture physical=new RelayPhysicalFixture();
    final UUID target=UUID.randomUUID(),check=UUID.randomUUID(),snapshot=UUID.randomUUID();
    final ObjectNode context=physical.context,request=object();
    RelayAssociationFixture() {
        ((ObjectNode)context.path("original")).put("deviceId",UUID.randomUUID().toString()).putObject("task").put("vehicleNo","合成车01");
        ((ObjectNode)context.path("original")).putObject("closure").put("plannedTotal","1").put("active","1").put("shortfall","0").put("shortfallReason","");
        var inventory=(ObjectNode)context.path("inventory").get(0);inventory.putArray("blockers");
        inventory.putObject("original").put("deliveryCode","001").put("model","M").put("name","合成纤维");
        inventory.putObject("server").put("snapshotId",snapshot.toString());context.putArray("peers");
        var candidate=context.putObject("candidates").putArray("items").addObject();
        candidate.put("id",target.toString()).put("source","WEB").put("status","READY").put("vehicleNo","合成车01")
            .put("inventorySnapshotId",snapshot.toString()).put("plannedTotal","1");candidate.putArray("blockers");candidate.putArray("workFacts");
        candidate.putArray("products").addObject().put("deliveryCode","001").put("model","M").put("name","合成纤维").put("plannedCount","1");
        request.put("targetOrderId",target.toString()).put("physicalCheckId",check.toString()).put("reason","合成独立人工关联");
        request.putArray("candidateExclusions");refresh();
    }
    void refresh() { request.put("contextSha256",sha(context));physical.request.put("contextSha256",sha(context)); }
    ObjectNode candidate() { return (ObjectNode)context.path("candidates").path("items").get(0); }
    void complete() { RelayAssociationPolicy.validate(request);RelayAssociationPolicy.complete(request,context,physical.request,physical.supervisor,NOW); }
    RelayAssociationStore.Row row() {
        var original=context.path("original");var scope=new RelayAssociationStore.Scope(physical.scope.warehouse(),physical.scope.relay(),
            uuid(original,"deviceId"),uuid(original,"taskId"),target,physical.supervisor,check);
        return new RelayAssociationStore.Row(UUID.randomUUID(),scope,request,context,null,NOW);
    }
}
