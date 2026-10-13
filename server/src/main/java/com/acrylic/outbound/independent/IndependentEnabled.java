package com.acrylic.outbound.independent;

import com.acrylic.outbound.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IndependentEnabled {
    private final boolean enabled;
    public IndependentEnabled(@Value("${outbound.independent.enabled:false}") boolean enabled) { this.enabled=enabled; }
    public void require() {
        if(!enabled) throw new BusinessException(409,"INDEPENDENT_CAPTURE_DISABLED","独立采集尚未开放；原件保留");
    }
}
