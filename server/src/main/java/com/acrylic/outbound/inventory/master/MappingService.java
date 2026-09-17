package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.IdempotencyRequests;
import com.acrylic.outbound.common.JsonText;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class MappingService {
    private final MutationAccess access;
    private final MappingStore store;
    private final IdempotencyRequests requests;
    private final JsonText json;
    private final AuditWriter audit;
    public MappingService(MutationAccess access, MappingStore store, IdempotencyRequests requests, JsonText json, AuditWriter audit) {
        this.access = access; this.store = store; this.requests = requests; this.json = json; this.audit = audit;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public UUID revise(MutationAccess.Context context, Revision input) {
        var locked = access.lock(context, "mapping:maintain");
        var key = new IdempotencyRequests.Key(locked.actor(), "mapping.revise", context.key());
        String digest = json.digest(new Payload(context.warehouse(), input));
        UUID replay = requests.find(key, digest);
        if (replay != null) return replay;
        var old = store.current(context.warehouse()).stream().filter(m -> m.deliveryCode().equals(input.deliveryCode())).findFirst().orElse(null);
        String version = old == null ? "0" : old.version();
        if (!version.equals(input.expectedVersion()))
            throw new BusinessException(409, "MAPPING_VERSION_CONFLICT", "代码映射已被其他人修改，请刷新后核对");
        store.revise(new MappingStore.Change(context.warehouse(), input.deliveryCode(), Math.addExact(Long.parseLong(version), 1),
            input.model().strip(), input.name().strip(), input.enabled(), null, locked.actor(), input.reason().strip()));
        store.advance(context.warehouse());
        UUID id = UUID.fromString(store.current(context.warehouse()).stream().filter(m -> m.deliveryCode().equals(input.deliveryCode())).findFirst().orElseThrow().id());
        requests.record(key, digest, id);
        audit.append(new AuditWriter.Event(locked.actor(), "MAPPING_REVISED", "DELIVERY_MAPPING", id, json.encode(new ChangeAudit(old, input))), context.requestId());
        return id;
    }
    public record Revision(@Pattern(regexp="[A-Z0-9]{3}") @NotNull String deliveryCode,
        @Pattern(regexp="[0-9]{1,18}") @NotNull String expectedVersion,
        @NotBlank @Size(max=100) String model, @NotBlank @Size(max=100) String name,
        @NotNull Boolean enabled, @NotBlank @Size(max=500) String reason) { }
    private record Payload(UUID warehouse, Revision revision) { }
    private record ChangeAudit(MappingStore.Mapping previous, Revision revision) { }
}
