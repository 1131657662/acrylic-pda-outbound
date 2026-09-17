package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.orders.OrderWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PreparationAccess {
    private final PdaAccess access;private final AssignmentStore store;private final OrderWriter orders;private final ServerState state;private final ObjectMapper json;
    public PreparationAccess(PdaAccess access,AssignmentStore store,OrderWriter orders,ServerState state,ObjectMapper json) {
        this.access=access;this.store=store;this.orders=orders;this.state=state;this.json=json;
    }
    public AssignmentStore.View lock(UUID id,String epoch) {
        var principal=access.lock(epoch);var assignment=owned(id,principal);
        orders.lock(principal.warehouseId(),UUID.fromString(assignment.orderId()));
        return valid(owned(id,principal),epoch);
    }
    public AssignmentStore.View read(UUID id) {
        var principal=access.require("pda:scan");var assignment=owned(id,principal);
        state.requireWritable(assignment.serverRecoveryEpoch());return valid(assignment,assignment.serverRecoveryEpoch());
    }
    private AssignmentStore.View owned(UUID id,PdaSessions.Principal principal) {
        AssignmentStore.View view;
        try { view=store.get(id); }
        catch(org.springframework.dao.EmptyResultDataAccessException error) { throw missing(); }
        if(!view.deviceId().equals(principal.deviceId().toString()) || !view.operatorId().equals(principal.userId().toString())) throw missing();
        if(!store.credentialActive(id)) throw new BusinessException(409,"OFFLINE_AUTH_EXPIRED","离线凭据到期或已撤销，保留占用并联系主管");
        requireVersions(view,principal);
        return view;
    }
    private void requireVersions(AssignmentStore.View view,PdaSessions.Principal principal) {
        try {
            // Token comes only from our immutable authorization row, never from request input.
            var claims=json.readTree(java.util.Base64.getUrlDecoder().decode(view.offlineToken().split("\\.")[1]));
            if(!claims.path("userAuthVersion").asText().equals(principal.userAuthVersion()) ||
                !claims.path("deviceVersion").asText().equals(principal.deviceVersion()))
                throw new BusinessException(409,"OFFLINE_AUTH_REAUTHORIZATION_REQUIRED","人员或设备授权版本已变化，保留占用并联系主管重新授权");
        } catch(java.io.IOException | IllegalArgumentException error) { throw new IllegalStateException("Stored authorization is invalid",error); }
    }
    private AssignmentStore.View valid(AssignmentStore.View view,String epoch) {
        if(!view.serverRecoveryEpoch().equals(epoch)) throw new BusinessException(409,"ORDER_RECOVERY_REQUIRED","授权来自旧恢复代次，保留资料并对账");
        if(!view.status().equals("ACTIVE")) throw new BusinessException(409,"ASSIGNMENT_NOT_ACTIVE","授权已停止，请保留资料并联系主管");
        if(!Instant.parse(view.expiresAt()).isAfter(Instant.now())) throw new BusinessException(409,"OFFLINE_AUTH_EXPIRED","离线授权已到期，保留占用并联系主管");
        return view;
    }
    private BusinessException missing() { return new BusinessException(404,"ASSIGNMENT_NOT_FOUND","授权不存在或不属于当前人员及设备"); }
}
