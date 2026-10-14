package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RelayAssociationShortfall {
    private final boolean enabled;
    public RelayAssociationShortfall(@Value("${outbound.rescue.independent-shortfall-enabled:false}") boolean enabled) { this.enabled=enabled; }
    public void require(JsonNode request) {
        if(request.has("shortfallApproval") && !enabled)
            throw new BusinessException(409,"INDEPENDENT_SHORTFALL_DISABLED","独立关联短装批准尚未开放，原事实保留");
    }
}
