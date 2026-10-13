package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RelayEnabled {
    private final boolean enabled;
    public RelayEnabled(@Value("${outbound.rescue.relay-enabled:false}") boolean enabled) { this.enabled=enabled; }
    public void require() {
        if(!enabled)throw new BusinessException(409,"ORIGINAL_RELAY_DISABLED","原件中转尚未开放，原记录保留");
    }
}
