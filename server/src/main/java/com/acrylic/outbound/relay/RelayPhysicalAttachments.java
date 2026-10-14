package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.ImportFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RelayPhysicalAttachments {
    private final RelayPhysicalAccess access;private final RelayPhysicalStore store;private final RelayOriginals originals;private final RelayJournal journal;
    private final RelayIndependentReview review;
    public RelayPhysicalAttachments(RelayPhysicalAccess access,RelayPhysicalStore store,RelayOriginals originals,RelayJournal journal,RelayIndependentReview review) {
        this.access=access;this.store=store;this.originals=originals;this.journal=journal;this.review=review;
    }
    public void authorize(UUID warehouse) { access.authorize(warehouse); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode upload(RelayIntake.Context c,UUID relay,String digest,byte[] bytes) {
        access.authorize(c.warehouse());RelayPhysicalPolicy.bytes(bytes,digest);UUID actor=access.write(c.warehouse(),c.epoch());
        var command=new RelayJournal.Command(new IdempotencyRequests.Key(actor,"relay.physical.attachment",c.key()),
            Map.of("relayId",relay.toString(),"warehouseId",c.warehouse().toString(),"sha256",digest,"sizeBytes",Integer.toString(bytes.length)));
        UUID replay=journal.replay(command);if(replay!=null)return store.attachment(c.warehouse(),replay).metadata();
        RelayIndependentReconciliation.eligible(review.read(c.warehouse(),relay));
        var row=new RelayPhysicalStore.Attachment(Ids.next(),relay,actor,digest,bytes);
        store.attachment(new RelayPhysicalStore.Scope(c.warehouse(),relay,actor),row);journal.record(command,row.id(),c.requestId());return row.metadata();
    }
    @Transactional(readOnly=true)
    public byte[] read(UUID warehouse,UUID relay,UUID id) {
        access.read(warehouse);originals.read(warehouse,relay);var row=store.attachment(warehouse,id);
        if(!row.relay().equals(relay))throw RelayPhysicalStore.missing();return row.bytes();
    }
}
