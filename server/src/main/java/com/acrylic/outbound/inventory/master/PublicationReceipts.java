package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.audit.AuditWriter;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PublicationReceipts {
    private final IdempotencyRequests requests;
    private final SnapshotQueries snapshots;
    private final JsonText json;
    private final AuditWriter audit;
    public PublicationReceipts(IdempotencyRequests requests, SnapshotQueries snapshots, JsonText json, AuditWriter audit) {
        this.requests = requests; this.snapshots = snapshots; this.json = json; this.audit = audit;
    }
    public SnapshotQueries.Receipt get(UUID warehouse, UUID id) { return snapshots.byId(warehouse, id); }
    public void save(MutationAccess.Context context, PublicationService.Command input, PublicationPreparation.Work work, UUID id) {
        requests.record(new IdempotencyRequests.Key(work.actor(), "inventory.publish", context.key()), work.digest(), id);
        audit.append(new AuditWriter.Event(work.actor(), "INVENTORY_PUBLISHED", "INVENTORY_SNAPSHOT", id, json.encode(input)), context.requestId());
    }
}
