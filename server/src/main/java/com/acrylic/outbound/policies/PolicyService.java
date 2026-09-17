package com.acrylic.outbound.policies;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PolicyService {
    private final MutationAccess access;
    private final PolicyStore policies;
    private final IdempotencyRequests requests;
    private final JsonText json;
    private final AuditWriter audit;
    public PolicyService(MutationAccess access,PolicyStore policies,IdempotencyRequests requests,JsonText json,AuditWriter audit) {
        this.access=access;this.policies=policies;this.requests=requests;this.json=json;this.audit=audit;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PolicyStore.Policy revise(MutationAccess.Context context,Command input) {
        var locked=access.lock(context,"policy:manage");
        var key=new IdempotencyRequests.Key(locked.actor(),"policy.revise",context.key());
        String digest=json.digest(new Body(context.warehouse(),input));
        UUID existing=requests.find(key,digest);
        if(existing!=null) return policies.byId(context.warehouse(),existing);
        var current=policies.current(context.warehouse());
        String version=current==null?"0":current.version();
        if(!version.equals(input.expectedVersion())) throw new BusinessException(409,"POLICY_VERSION_CONFLICT","政策已变化，请重新核对");
        var result=policies.insert(new PolicyStore.Work(context.warehouse(),locked.actor(),Math.addExact(Long.parseLong(version),1)),input);
        UUID id=UUID.fromString(result.id());requests.record(key,digest,id);
        audit.append(new AuditWriter.Event(locked.actor(),"POLICY_REVISED","WAREHOUSE",context.warehouse(),json.encode(result)),context.requestId());
        return result;
    }
    public record Command(@NotNull @Pattern(regexp="[0-9]{1,18}") String expectedVersion,
        @NotNull Boolean autoDeductInventory,@NotBlank @Size(max=500) String reason) { }
    private record Body(UUID warehouse,Command command) { }
}
