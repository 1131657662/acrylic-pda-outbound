package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.JsonText;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PublicationMappings {
    private final MappingStore store;
    private final AuditWriter audit;
    private final JsonText json;
    public PublicationMappings(MappingStore store, AuditWriter audit, JsonText json) {
        this.store = store; this.audit = audit; this.json = json;
    }
    public Map<String, UUID> apply(PublicationPreview.Preview preview, MutationAccess.Context context, UUID actor) {
        for (var change : preview.mappingChanges()) {
            long version = change.previous() == null ? 1 : Math.addExact(Long.parseLong(change.previous().version()), 1);
            store.revise(new MappingStore.Change(context.warehouse(), change.deliveryCode(), version, change.model(), change.name(),
                change.enabled(), preview.imported().id(), actor, "库存发布时确认候选映射"));
            audit.append(new AuditWriter.Event(actor, "MAPPING_CONFIRMED", "INVENTORY_IMPORT", preview.imported().id(), json.encode(change)), context.requestId());
        }
        if (!preview.mappingChanges().isEmpty()) store.advance(context.warehouse());
        Map<String, UUID> products = new HashMap<>();
        store.current(context.warehouse()).forEach(m -> products.put(m.deliveryCode(), UUID.fromString(m.productId())));
        return products;
    }
}
