package com.acrylic.outbound.recovery;

import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** E/G -> ordered users -> warehouse -> ordered devices/sessions; no ordinary-write gate is opened. */
final class RecoveryRecipientIdentity {
    private final JdbcTemplate jdbc;
    private final JsonNode body;
    private final RecoveryScopeReleaseStore store;
    private final boolean preparation;
    RecoveryRecipientIdentity(JdbcTemplate jdbc,JsonNode body,RecoveryScopeReleaseStore store) {
        this(jdbc,body,store,false);
    }
    RecoveryRecipientIdentity(JdbcTemplate jdbc,JsonNode body,RecoveryScopeReleaseStore store,boolean preparation) {
        this.jdbc=jdbc; this.body=body.deepCopy(); this.store=store; this.preparation=preparation;
    }
    void lock() {
        var guard=new IdentityWriteGuard(null,jdbc); guard.authentication();
        guard.users(List.of(uuid(body,"supervisorId"),uuid(body,"registrarId"),uuid(body.path("recipient"),"userId")));
        new WarehouseBaseline(jdbc).read(uuid(body,"warehouseId"),true);
        var source=store.approval().closed().sourceIdentity().deviceId();
        List.of(source,uuid(body.path("recipient"),"deviceId")).stream().sorted(Comparator.comparing(UUID::toString))
            .forEach(id->jdbc.queryForList("SELECT id FROM device WHERE id=? FOR UPDATE",Ids.bytes(id)));
        new com.acrylic.outbound.pda.PdaSessionLocks(jdbc).device(source);
        requireCurrent();
    }
    void requireCurrent() {
        var identities=new IdentityStore(jdbc); var access=new Access(identities); var warehouse=uuid(body,"warehouseId");
        actor(identities,access,warehouse);
        var supervisor=uuid(body,"supervisorId"); user(supervisor,revision(body,"supervisorAuthVersion"));
        require(identities.warehouses(supervisor).stream().anyMatch(w->w.id().equals(warehouse)),"RECOVERY_RECIPIENT_SUPERVISOR_WAREHOUSE");
        var permissions=identities.permissions(supervisor);
        require(permissions.contains("order:handover") || permissions.contains("order:resume"),"RECOVERY_RECIPIENT_APPROVAL_PERMISSION");
        var recipient=body.path("recipient"); var target=uuid(recipient,"userId");
        user(target,revision(recipient,"userAuthVersion"));
        require(identities.permissions(target).containsAll(List.of("pda:login","pda:scan","temporary_order:create"))
            && identities.warehouses(target).stream().anyMatch(w->w.id().equals(warehouse)),"RECOVERY_RECIPIENT_PERMISSION");
        var source=Ids.bytes(store.approval().closed().sourceIdentity().deviceId());
        var devices=jdbc.queryForList("SELECT status FROM device WHERE id=? AND warehouse_id=?",source,Ids.bytes(warehouse));
        require(devices.size()==1 && "DISABLED".equals(devices.getFirst().get("status")),"RECOVERY_RECIPIENT_SOURCE_FENCE");
        require(jdbc.queryForList("SELECT revoked_at FROM pda_session WHERE device_id=?",source).stream()
            .allMatch(row->row.get("revoked_at")!=null),"RECOVERY_RECIPIENT_SOURCE_SESSION");
    }
    private void actor(IdentityStore identities,Access access,UUID warehouse) {
        if(preparation) {
            var actor=access.current();
            require(actor.id().equals(uuid(body,"supervisorId")) && actor.authVersion()==revision(body,"supervisorAuthVersion"),"RECOVERY_PREPARATION_ACTOR");
        } else {
            var registrar=access.require("device:manage",warehouse); access.require("temporary_order:manage",warehouse);
            require(registrar.id().equals(uuid(body,"registrarId")) && registrar.authVersion()==revision(body,"registrarAuthVersion"),"RECOVERY_RECIPIENT_ACTOR");
        }
        var registrarId=uuid(body,"registrarId"); user(registrarId,revision(body,"registrarAuthVersion"));
        require(identities.permissions(registrarId).containsAll(List.of("device:manage","temporary_order:manage"))
            && identities.warehouses(registrarId).stream().anyMatch(w->w.id().equals(warehouse)),"RECOVERY_RECIPIENT_REGISTRAR_CHANGED");
    }
    private void user(UUID id,long version) {
        var users=jdbc.queryForList("SELECT status,auth_version FROM app_user WHERE id=?",Ids.bytes(id));
        require(users.size()==1 && "ACTIVE".equals(users.getFirst().get("status"))
            && ((Number)users.getFirst().get("auth_version")).longValue()==version,"RECOVERY_RECIPIENT_USER_CHANGED");
    }
}
