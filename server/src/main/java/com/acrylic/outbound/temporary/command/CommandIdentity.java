package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CommandIdentity {
    private final Access web;private final PdaAccess pda;private final IdentityStore identities;private final JdbcTemplate jdbc;
    public CommandIdentity(Access web,PdaAccess pda,IdentityStore identities,JdbcTemplate jdbc) {
        this.web=web;this.pda=pda;this.identities=identities;this.jdbc=jdbc;
    }
    public Actor current(UUID warehouse) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        PdaSessions.Principal principal=auth!=null && auth.getPrincipal() instanceof PdaSessions.Principal?pda.current():null;
        var user=principal==null?web.current():identities.loadUserByUsername(principal.username());
        if(principal!=null && !principal.warehouseId().equals(warehouse))throw CommandErrors.missing();
        var warehouses=identities.warehouses(user.id()).stream().map(w->w.id().toString()).sorted().toList();
        if(!warehouses.contains(warehouse.toString()))throw CommandErrors.missing();
        var permissions=identities.permissions(user.id());
        if(!CommandPermissions.mayEnter(permissions))throw CommandErrors.missing();
        return new Actor(user.id(),warehouse,Long.toString(user.authVersion()),principal,permissions,warehouses);
    }
    public ObjectNode snapshot(Actor actor,String kind) {
        var p=actor.pda();var node=SubmissionJson.object().put("principalId",actor.id().toString()).put("userAuthVersion",actor.authVersion())
            .put("deviceId",p==null?null:p.deviceId().toString()).put("sessionId",p==null?null:p.sessionId().toString())
            .put("deviceVersion",p==null?null:p.deviceVersion());
        var permissions=node.putObject("permissionSnapshot");
        var required=permissions.putArray("requiredPermissions");String permission=CommandPermissions.execution(kind);
        if(permission!=null)required.add(permission);
        actor.permissions().stream().sorted().forEach(permissions.putArray("effectivePermissions")::add);
        actor.warehouses().forEach(permissions.putArray("warehouseIds")::add);
        var roles=jdbc.queryForList("SELECT BIN_TO_UUID(role_id) FROM user_role WHERE user_id=? ORDER BY role_id",String.class,Ids.bytes(actor.id()));
        roles.forEach(permissions.putArray("roleIds")::add);return node;
    }
    public record Actor(UUID id,UUID warehouse,String authVersion,PdaSessions.Principal pda,List<String> permissions,List<String> warehouses) {
        public Actor { permissions=List.copyOf(permissions);warehouses=List.copyOf(warehouses); }
    }
}
