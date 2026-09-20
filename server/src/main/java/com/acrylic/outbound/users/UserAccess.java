package com.acrylic.outbound.users;

import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserAccess {
    private final Access access; private final IdentityStore identities; private final ServerState state; private final JdbcTemplate jdbc;
    public UserAccess(Access access,IdentityStore identities,ServerState state,JdbcTemplate jdbc) {
        this.access=access;this.identities=identities;this.state=state;this.jdbc=jdbc;
    }
    public Scope read(UUID warehouse) {
        var actor=access.require("user:manage",warehouse);
        return new Scope(actor.id(),manageablePermissions(actor.id()),
            identities.warehouses(actor.id()).stream().map(w->new UserCommands.Warehouse(w.id(),w.code(),w.name())).toList());
    }
    private Set<String> manageablePermissions(UUID actor) {
        var permissions=new HashSet<>(identities.permissions(actor));
        var temporary=Set.of("temporary_order:create","temporary_order:confirm","temporary_order:link");
        if(permissions.contains("temporary_order:manage"))
            permissions.addAll(temporary);
        else permissions.removeAll(temporary);
        return permissions;
    }
    public Scope lock(MutationAccess.Context context) {
        access.require("user:manage",context.warehouse());state.lockWritable(context.epoch());
        jdbc.queryForObject("SELECT id FROM user_management_guard WHERE id=1 FOR UPDATE",Integer.class);
        return read(context.warehouse());
    }
    public void target(Scope scope,UserCommands.Item user,UUID warehouse) {
        var ids=user.warehouses().stream().map(UserCommands.Warehouse::id).toList();
        if(!ids.contains(warehouse) || !scope.warehouseIds().containsAll(ids)) throw missing();
        if(!scope.manageablePermissions().containsAll(identities.permissions(user.id()))) throw missing();
    }
    public void grants(Scope scope,UUID warehouse,Grants input) {
        if(!input.warehouses().contains(warehouse) || !scope.warehouseIds().containsAll(input.warehouses()))
            throw new BusinessException(403,"USER_WAREHOUSE_DENIED","只能分配本人可管理的仓库，且须保留当前仓库");
        if(new HashSet<>(input.warehouses()).size()!=input.warehouses().size() || new HashSet<>(input.roles()).size()!=input.roles().size())
            throw new BusinessException(400,"INVALID_REQUEST","角色和仓库不能重复");
        var allowed=input.catalog().stream().filter(r->scope.manageablePermissions().containsAll(r.permissions())).map(UserCommands.Role::code).toList();
        if(!allowed.containsAll(input.roles())) throw new BusinessException(403,"USER_ROLE_DENIED","不能授予超出当前管理员可管理范围的角色权限");
    }
    public static BusinessException missing() { return new BusinessException(404,"USER_NOT_FOUND","人员不存在或不在可管理范围内"); }
    public record Scope(UUID actor,Set<String> manageablePermissions,List<UserCommands.Warehouse> warehouses) {
        public Set<UUID> warehouseIds() { return new HashSet<>(warehouses.stream().map(UserCommands.Warehouse::id).toList()); }
    }
    public record Grants(List<String> roles,List<UUID> warehouses,List<UserCommands.Role> catalog) { }
}
