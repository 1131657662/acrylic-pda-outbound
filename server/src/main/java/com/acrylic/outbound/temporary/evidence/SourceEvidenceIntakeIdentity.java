package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.pda.PdaAccess;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SourceEvidenceIntakeIdentity {
    private final PdaAccess pda;
    private final IdentityStore identities;

    public SourceEvidenceIntakeIdentity(PdaAccess pda,IdentityStore identities) {
        this.pda=pda;this.identities=identities;
    }

    public CommandIdentity.Actor current(UUID warehouseId) {
        var principal=pda.current();
        if(!principal.warehouseId().equals(warehouseId)) throw missing();
        var permissions=identities.permissions(principal.userId());
        var warehouses=identities.warehouses(principal.userId()).stream().map(w->w.id().toString()).sorted().toList();
        if(!warehouses.contains(warehouseId.toString())) throw missing();
        return new CommandIdentity.Actor(principal.userId(),warehouseId,principal.userAuthVersion(),principal,permissions,warehouses);
    }

    public void requireRecovery(CommandIdentity.Actor actor) {
        if(actor.pda()==null || !actor.permissions().contains("pda:recover")) throw denied();
    }

    private static BusinessException missing() {
        return new BusinessException(404,"E2A_SOURCE_NOT_FOUND","原来源不存在或无权访问");
    }

    private static BusinessException denied() {
        return new BusinessException(403,"E2A_RECOVERY_PERMISSION_DENIED","没有原机证据接收权限");
    }
}
