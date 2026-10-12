package com.acrylic.outbound.rescue;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Returns original authorization/revocation material to the original PDA or a scoped supervisor. */
@Service
@Transactional(readOnly=true)
public class RescueMaterials {
    private final RescueAccess access;private final RescueStore store;private final RescueEnabled enabled;
    public RescueMaterials(RescueAccess access,RescueStore store,RescueEnabled enabled) {
        this.access=access;this.store=store;this.enabled=enabled;
    }
    public RescueCommands.Material supervisor(UUID warehouse,UUID id) {
        enabled.require();var request=store.request(id,false);access.webRead(request,warehouse);return material(request);
    }
    public RescueCommands.Material own(UUID id) {
        enabled.require();var request=store.request(id,false);access.own(request);return material(request);
    }
    public RescueCommands.Material delegated(UUID id) {
        enabled.require();var request=store.request(store.requestForGrant(id),false);
        access.rescuer(request);return material(request);
    }
    private RescueCommands.Material material(RescueStore.Request request) {
        UUID id=store.grantForRequest(request.id());
        var grant=id==null?null:store.grantDetails(id);var revoked=id==null?null:store.revocation(id);
        String status=grant==null?"REQUESTED":status(grant,revoked);
        return new RescueCommands.Material(request.receipt(),request.originalText(),request.signature(),store.scope(request.id()),
            status,id==null?null:store.grant(id),revoked,false);
    }
    private String status(RescueStore.Grant grant,RescueCommands.RevocationReceipt revoked) {
        if(revoked!=null) return "REVOKED";
        if(grant.expiresAt()!=null && !Instant.now().isBefore(grant.expiresAt())) return "EXPIRED";
        return "AUTHORIZED";
    }
}
