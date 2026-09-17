package com.acrylic.outbound.pairing;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PairingAuthority {
    private final JdbcTemplate jdbc;private final IdentityStore identities;
    private final WarehouseBaseline warehouses;private final AuditWriter audit;
    public PairingAuthority(JdbcTemplate jdbc,IdentityStore identities,WarehouseBaseline warehouses,AuditWriter audit) {
        this.jdbc=jdbc;this.identities=identities;this.warehouses=warehouses;this.audit=audit;
    }
    public void lockWarehouse(PairingStore.Row row) { if(row.approval()!=null) warehouses.read(row.approval().warehouseId(),true); }
    public String warehouseName(UUID warehouse) {
        return jdbc.queryForObject("SELECT name FROM warehouse WHERE id=? AND status='ACTIVE'",String.class,Ids.bytes(warehouse));
    }
    public void requireApprover(PairingStore.Row row) {
        var valid=jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND auth_version=? AND status='ACTIVE'",
            Integer.class,Ids.bytes(row.actor()),row.actorVersion());
        boolean allowed=valid==1 && identities.permissions(row.actor()).contains("device:manage") &&
            identities.warehouses(row.actor()).stream().anyMatch(w->w.id().equals(row.approval().warehouseId()));
        if(!allowed) throw new BusinessException(403,"PAIRING_APPROVER_REVOKED","管理员授权已变化，请取消原申请并重新核对登记");
    }
    public void audit(PairingStore.Row row,String event,String requestId) {
        // Only identifiers and the state are audited; no code, credential, signature or token.
        audit.append(new AuditWriter.Event(row.actor(),event,"DEVICE_PAIRING",row.id(),
            "{\"status\":\""+row.status()+"\"}"),requestId);
    }
}
