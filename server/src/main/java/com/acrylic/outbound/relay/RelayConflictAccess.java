package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RelayConflictAccess {
    private final RelayEnabled relay;private final RelayAccess access;private final boolean enabled;
    public RelayConflictAccess(RelayEnabled relay,RelayAccess access,@Value("${outbound.rescue.independent-conflict-enabled:false}") boolean enabled) {
        this.relay=relay;this.access=access;this.enabled=enabled;
    }
    public void read(UUID warehouse) { relay.require();access.reviewIndependent(warehouse); }
    public void authorize(UUID warehouse) {
        read(warehouse);if(!enabled)throw new BusinessException(409,"INDEPENDENT_CONFLICT_DISABLED","独立采集冲突决定尚未开放，原事实保留");
    }
    public UUID write(UUID warehouse,String epoch) {
        authorize(warehouse);UUID actor=access.write(warehouse,epoch,null);access.reviewIndependent(warehouse);return actor;
    }
}
