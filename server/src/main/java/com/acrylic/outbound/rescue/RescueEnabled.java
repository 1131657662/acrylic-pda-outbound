package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RescueEnabled {
    private final boolean enabled;
    public RescueEnabled(@Value("${outbound.rescue.delegation-enabled:false}") boolean enabled) { this.enabled=enabled; }
    public void require() {
        if(!enabled) throw new BusinessException(409,"RESCUE_DELEGATION_DISABLED","主管预授权救援尚未开放，原记录保留");
    }
}
