package com.acrylic.outbound.relay;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

final class RelayConflictFixture {
    final RelayPhysicalFixture selected=new RelayPhysicalFixture(),peer=new RelayPhysicalFixture();
    final UUID warehouse=selected.scope.warehouse(),relay=selected.scope.relay(),other=peer.scope.relay();
    final ObjectNode group=object(),request=object();
    RelayConflictFixture() {
        prepare(selected,relay);prepare(peer,other);peer.observation().put("status","UNLOADED").put("note","另一任务误扫，已卸下并逐包核验");
        var original=peer.context.path("original");group.put("deviceId",text(original,"deviceId")).put("taskId",text(original,"taskId"))
            .put("crossTaskConflict",true).put("closureDivergence",false);group.putArray("overlappingBarcodes").add("001ABCDEFGH");
        group.putArray("originals").addObject().put("relayId",other.toString()).put("sourceTrust","TRUSTED").put("completeBoundaryVerified",true);
        selected.context.putArray("peers").add(group);peer.context.putArray("peers");
        request.put("physicalCheckId",UUID.randomUUID().toString()).put("reason","合成各方实物归属核对");
        request.putArray("peerChecks").addObject().put("relayId",other.toString()).put("physicalCheckId",UUID.randomUUID().toString());refresh();
    }
    private void prepare(RelayPhysicalFixture f,UUID id) {
        ((ObjectNode)f.context.path("original")).put("warehouseId",warehouse.toString()).put("relayId",id.toString()).put("deviceId",UUID.randomUUID().toString());
        var stock=(ObjectNode)f.context.path("inventory").get(0);stock.put("barcode","001ABCDEFGH");stock.putArray("blockers");
        stock.putObject("server").put("baleId",UUID.randomUUID().toString()).put("dispatchCycle","1");
    }
    void refresh() {
        request.put("contextSha256",sha(selected.context));selected.request.put("contextSha256",sha(selected.context));peer.request.put("contextSha256",sha(peer.context));
    }
    ObjectNode physical(RelayPhysicalFixture f) {
        var result=object();result.set("evidence",f.request);result.set("originalContext",f.context.deepCopy());
        result.putObject("receipt").put("relayId",text(f.context.path("original"),"relayId")).put("warehouseId",warehouse.toString())
            .put("receiptId",UUID.randomUUID().toString());return result;
    }
    Map<String,com.fasterxml.jackson.databind.JsonNode> peers() {
        var row=object();row.set("group",group);row.set("context",peer.context);row.set("physical",physical(peer));return Map.of(other.toString(),row);
    }
    void complete() {
        RelayConflictPolicy.validate(request);RelayConflictPolicy.peers(request,selected.context);
        var reviewer=new RelayConflictPolicy.Reviewer(selected.supervisor,RelayPhysicalFixture.NOW);
        RelayConflictPolicy.check(selected.context,physical(selected),reviewer);RelayConflictPolicy.check(peer.context,physical(peer),reviewer);
        RelayConflictPolicy.peerIdentity(peer.context,group);RelayConflictPolicy.claims(selected.context,physical(selected),peers());
    }
    RelayConflictStore.Row row() {
        var context=object();var subject=context.putObject("selected");subject.set("context",selected.context);subject.set("physical",physical(selected));
        var rows=context.putArray("peers");peers().values().forEach(rows::add);
        var claims=context.putArray("claims");RelayConflictPolicy.claims(selected.context,physical(selected),peers()).forEach(claims::add);
        var original=selected.context.path("original");var scope=new RelayConflictStore.Scope(warehouse,relay,uuid(original,"deviceId"),uuid(original,"taskId"),selected.supervisor);
        return new RelayConflictStore.Row(UUID.randomUUID(),scope,request,context,null,RelayPhysicalFixture.NOW);
    }
}
