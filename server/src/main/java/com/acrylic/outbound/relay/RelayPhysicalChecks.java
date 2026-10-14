package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Records physical assertions and references only; later association must recheck all live facts. */
@Service
public class RelayPhysicalChecks {
    private final RelayPhysicalAccess access;private final RelayPhysicalStore store;private final RelayPhysicalSignature signature;
    private final RelayIndependentReconciliation reconciliation;private final RelayJournal journal;
    public RelayPhysicalChecks(RelayPhysicalAccess access,RelayPhysicalStore store,RelayPhysicalSignature signature,
        RelayIndependentReconciliation reconciliation,RelayJournal journal) {
        this.access=access;this.store=store;this.signature=signature;this.reconciliation=reconciliation;this.journal=journal;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode record(RelayIntake.Context c,UUID relay,JsonNode body) {
        access.authorize(c.warehouse());RelayPhysicalPolicy.validate(body);UUID actor=access.write(c.warehouse(),c.epoch());
        var command=new RelayJournal.Command(new IdempotencyRequests.Key(actor,"relay.physical.check",c.key()),
            Map.of("relayId",relay.toString(),"warehouseId",c.warehouse().toString(),"requestSha256",sha(body)));
        UUID replay=journal.replay(command);if(replay!=null)return verified(c.warehouse(),relay,replay);
        var scope=new RelayPhysicalStore.Scope(c.warehouse(),relay,actor);var context=reconciliation.snapshot(c.warehouse(),relay);
        var at=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);RelayPhysicalPolicy.complete(body,context,actor,at);
        if(canonical(context).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16*1024*1024-1)
            throw RelayPhysicalPolicy.invalid("完整实物核对上下文超过资源上限，原件保留");
        var attachments=store.attachments(scope,body.path("attachments"));UUID id=Ids.next();
        var receipt=signature.create(id,scope,body,context,at);store.insert(new RelayPhysicalStore.Check(id,scope,body,context,receipt,at),attachments);
        journal.record(command,id,c.requestId());return verified(c.warehouse(),relay,id);
    }
    @Transactional(readOnly=true)
    public JsonNode read(UUID warehouse,UUID relay,UUID id) { access.read(warehouse);return verified(warehouse,relay,id); }
    private JsonNode verified(UUID warehouse,UUID relay,UUID id) {
        var row=store.check(warehouse,id);if(!row.scope().relay().equals(relay))throw RelayPhysicalStore.missing();
        signature.verify(row);store.references(row);var result=object();result.set("receipt",row.receipt());
        result.set("evidence",row.request());result.set("originalContext",row.context());return result;
    }
}
