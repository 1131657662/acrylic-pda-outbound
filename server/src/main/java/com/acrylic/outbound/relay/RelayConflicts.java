package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class RelayConflicts {
    private final RelayConflictAccess access;private final RelayConflictStore store;private final RelayConflictSignature signature;
    private final RelayIndependentReconciliation reconciliation;private final RelayPhysicalChecks physical;private final RelayJournal journal;
    public RelayConflicts(RelayConflictAccess access,RelayConflictStore store,RelayConflictSignature signature,
        RelayIndependentReconciliation reconciliation,RelayPhysicalChecks physical,RelayJournal journal) {
        this.access=access;this.store=store;this.signature=signature;this.reconciliation=reconciliation;this.physical=physical;this.journal=journal;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode record(RelayIntake.Context c,UUID relay,JsonNode body) {
        access.authorize(c.warehouse());RelayConflictPolicy.validate(body);UUID actor=access.write(c.warehouse(),c.epoch());
        var command=new RelayJournal.Command(new IdempotencyRequests.Key(actor,"relay.independent.conflict",c.key()),
            Map.of("relayId",relay.toString(),"warehouseId",c.warehouse().toString(),"requestSha256",sha(body)));
        UUID replay=journal.replay(command);if(replay!=null)return verified(c.warehouse(),relay,replay);
        var at=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);var context=context(new Build(c.warehouse(),relay,body,actor,at));
        var ancestors=extension(c.warehouse(),relay,body,context);
        store.available(c.warehouse(),context.path("claims"),ancestors);var original=context.path("selected").path("context").path("original");
        var scope=new RelayConflictStore.Scope(c.warehouse(),relay,uuid(original,"deviceId"),uuid(original,"taskId"),actor);UUID id=Ids.next();
        var row=new RelayConflictStore.Row(id,scope,body,context,null,at);var receipt=signature.create(row);
        store.insert(new RelayConflictStore.Row(id,scope,body,context,receipt,at));journal.record(command,id,c.requestId());
        return verified(c.warehouse(),relay,id);
    }
    private Set<UUID> extension(UUID warehouse,UUID relay,JsonNode body,ObjectNode context) {
        if(!body.has("predecessorDecisionId"))return Set.of();access.extension();
        var id=uuid(body,"predecessorDecisionId");var rows=lineage(warehouse,relay,id);store.latest(id);
        if(rows.size()>=RelayConflictExtensionPolicy.MAX_CHAIN)throw RelayConflictPolicy.blocked("责任链达到有界容量，请保全专门核对");
        RelayConflictExtensionPolicy.complete(context,rows.getFirst());context.set("predecessorReceipt",rows.getFirst().receipt());
        capacity(context,Map.of());var result=new HashSet<UUID>();rows.forEach(row->result.add(row.id()));return Set.copyOf(result);
    }
    private record Build(UUID warehouse,UUID relay,JsonNode body,UUID actor,Instant at) { }
    private ObjectNode context(Build input) {
        var warehouse=input.warehouse();var relay=input.relay();var body=input.body();var reviewer=new RelayConflictPolicy.Reviewer(input.actor(),input.at());
        var selected=reconciliation.snapshot(warehouse,relay);var groups=RelayConflictPolicy.peers(body,selected);
        var check=physical.read(warehouse,relay,RelayPhysicalPolicy.identifier(body,"physicalCheckId"));
        RelayConflictPolicy.check(selected,check,reviewer);var peers=new TreeMap<String,JsonNode>();
        var result=object();var subject=result.putObject("selected");subject.set("context",selected);subject.set("physical",check);
        for(var reference:body.path("peerChecks")) {
            var group=RelayConflictPolicy.peerFor(reference,groups);var key=text(group,"deviceId")+"/"+text(group,"taskId");
            if(peers.containsKey(key))throw RelayConflictPolicy.invalid("同一冲突任务不得重复引用导出原件");
            var id=RelayPhysicalPolicy.identifier(reference,"relayId");var context=reconciliation.snapshot(warehouse,id);
            RelayConflictPolicy.peerIdentity(context,group);var evidence=physical.read(warehouse,id,RelayPhysicalPolicy.identifier(reference,"physicalCheckId"));
            RelayConflictPolicy.check(context,evidence,reviewer);var row=object();row.set("group",group);row.set("context",context);row.set("physical",evidence);
            peers.put(key,row);capacity(result,peers);
        }
        var peerRows=result.putArray("peers");peers.values().forEach(peerRows::add);
        var claims=result.putArray("claims");RelayConflictPolicy.claims(selected,check,peers).forEach(claims::add);capacity(result,Map.of());return result;
    }
    private void capacity(JsonNode result,Map<String,JsonNode> peers) {
        long size=canonical(result).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        for(var peer:peers.values())size+=canonical(peer).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if(size>16*1024*1024-1)throw RelayConflictPolicy.invalid("完整冲突证据超过资源上限，原件保留");
    }
    @Transactional(readOnly=true)
    public JsonNode read(UUID warehouse,UUID relay,UUID id) { access.read(warehouse);return verified(warehouse,relay,id); }
    public JsonNode current(UUID warehouse,UUID relay,UUID id) {
        var result=read(warehouse,relay,id);store.latest(id);return result;
    }
    private JsonNode verified(UUID warehouse,UUID relay,UUID id) {
        var row=lineage(warehouse,relay,id).getFirst();
        var result=object();result.set("receipt",row.receipt());result.set("evidence",row.request());result.set("originalContext",row.context());return result;
    }
    private List<RelayConflictStore.Row> lineage(UUID warehouse,UUID relay,UUID id) {
        var rows=new ArrayList<RelayConflictStore.Row>();var seen=new HashSet<UUID>();
        while(id!=null) {
            if(!seen.add(id) || rows.size()>=RelayConflictExtensionPolicy.MAX_CHAIN)throw RelayConflictStore.corrupt();
            var row=store.read(warehouse,id);if(!row.scope().relay().equals(relay))throw RelayConflictStore.missing();
            signature.verify(row);store.references(row);verifyChecks(warehouse,row);rows.add(row);id=store.predecessor(row);
        }
        for(int i=0;i<rows.size()-1;i++)RelayConflictExtensionPolicy.reference(rows.get(i),rows.get(i+1));
        var ancestors=new HashSet<UUID>();
        for(int i=rows.size()-1;i>=0;i--) { var row=rows.get(i);ancestors.add(row.id());store.available(warehouse,row.context().path("claims"),ancestors); }
        return List.copyOf(rows);
    }
    private void verifyChecks(UUID warehouse,RelayConflictStore.Row row) {
        for(var check:RelayConflictStore.checks(row.context())) {
            var r=check.path("receipt");var actual=physical.read(warehouse,uuid(r,"relayId"),uuid(r,"receiptId"));
            if(!actual.equals(check))throw RelayConflictStore.corrupt();
        }
    }
}
