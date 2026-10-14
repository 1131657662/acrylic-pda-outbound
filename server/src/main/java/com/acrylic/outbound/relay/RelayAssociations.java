package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class RelayAssociations {
    private final RelayAssociationAccess access;private final RelayAssociationStore store;private final RelayAssociationSignature signature;
    private final RelayIndependentReconciliation reconciliation;private final RelayPhysicalChecks physical;private final RelayJournal journal;
    private final RelayAssociationConflicts conflicts;
    public RelayAssociations(RelayAssociationAccess access,RelayAssociationStore store,RelayAssociationSignature signature,
        RelayIndependentReconciliation reconciliation,RelayPhysicalChecks physical,RelayJournal journal,RelayAssociationConflicts conflicts) {
        this.access=access;this.store=store;this.signature=signature;this.reconciliation=reconciliation;this.physical=physical;this.journal=journal;this.conflicts=conflicts;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode record(RelayIntake.Context c,UUID relay,JsonNode body) {
        access.authorize(c.warehouse());RelayAssociationPolicy.validate(body);UUID actor=access.write(c.warehouse(),c.epoch());
        var command=new RelayJournal.Command(new IdempotencyRequests.Key(actor,"relay.independent.associate",c.key()),
            Map.of("relayId",relay.toString(),"warehouseId",c.warehouse().toString(),"requestSha256",sha(body)));
        UUID replay=journal.replay(command);if(replay!=null)return verified(c.warehouse(),relay,replay);
        UUID target=RelayPhysicalPolicy.identifier(body,"targetOrderId");store.lockTarget(c.warehouse(),target);
        var context=reconciliation.snapshot(c.warehouse(),relay);UUID check=RelayPhysicalPolicy.identifier(body,"physicalCheckId");
        var evidence=physical.read(c.warehouse(),relay,check);var at=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var conflict=conflicts.resolve(c.warehouse(),relay,body);
        RelayAssociationPolicy.complete(body,context,evidence.path("evidence"),actor,at,conflict);
        if(canonical(context).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16*1024*1024-1)
            throw RelayAssociationPolicy.invalid("完整关联上下文超过资源上限，原件保留");
        var original=context.path("original");var scope=new RelayAssociationStore.Scope(c.warehouse(),relay,uuid(original,"deviceId"),
            uuid(original,"taskId"),target,actor,check);store.available(scope);UUID id=Ids.next();
        var row=new RelayAssociationStore.Row(id,scope,body,context,null,at);var receipt=signature.create(row);
        var recorded=new RelayAssociationStore.Row(id,scope,body,context,receipt,at);store.insert(recorded);conflicts.insert(recorded);
        journal.record(command,id,c.requestId());
        return verified(c.warehouse(),relay,id);
    }
    @Transactional(readOnly=true)
    public JsonNode read(UUID warehouse,UUID relay,UUID id) { access.read(warehouse);return verified(warehouse,relay,id); }
    @Transactional(readOnly=true)
    public JsonNode task(UUID warehouse,UUID relay) {
        access.read(warehouse);return verified(warehouse,null,store.byOriginal(warehouse,relay));
    }
    private JsonNode verified(UUID warehouse,UUID relay,UUID id) {
        var row=store.read(warehouse,id);if(relay!=null && !row.scope().relay().equals(relay))throw RelayAssociationStore.missing();
        signature.verify(row);var check=physical.read(warehouse,row.scope().relay(),row.scope().physical());
        if(!check.path("originalContext").equals(row.context()))throw RelayAssociationStore.corrupt();
        var result=object();result.set("receipt",row.receipt());result.set("evidence",row.request());result.set("originalContext",row.context());
        result.set("physicalEvidence",check);var conflict=conflicts.read(row);if(conflict!=null)result.set("conflictEvidence",conflict);return result;
    }
}
