package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.*;
import com.acrylic.outbound.policies.PolicyStore;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TemporaryBaseline {
    private final WarehouseBaseline warehouses;private final PolicyStore policies;private final JdbcTemplate jdbc;
    public TemporaryBaseline(WarehouseBaseline warehouses,PolicyStore policies,JdbcTemplate jdbc) {
        this.warehouses=warehouses;this.policies=policies;this.jdbc=jdbc;
    }
    public View read(UUID warehouse) {
        var baseline=warehouses.read(warehouse,false);
        if(baseline.activeSnapshotId()==null) throw new BusinessException(409,"INVENTORY_REQUIRED","请先发布有效库存");
        var snapshot=jdbc.queryForObject("SELECT version,row_count,manifest_sha256,status FROM inventory_snapshot WHERE warehouse_id=? AND id=?",
            (rs,n)->new Snapshot(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getString(4)),Ids.bytes(warehouse),Ids.bytes(UUID.fromString(baseline.activeSnapshotId())));
        if(!snapshot.status().equals("ACTIVE")) throw new BusinessException(409,"INVENTORY_REQUIRED","当前库存不可用于临时作业");
        var policy=policies.current(warehouse);
        if(policy==null) throw new BusinessException(409,"POLICY_REQUIRED","请先配置出库政策");
        return new View(warehouse.toString(),baseline.activeSnapshotId(),snapshot.version(),baseline.mappingRevision(),snapshot.rows(),snapshot.sha(),policy);
    }
    public View require(UUID warehouse,TemporaryCommands.Prepare command) {
        var current=read(warehouse);
        if(!current.inventorySnapshotId().equals(command.inventorySnapshotId().toString()) || !current.mappingRevision().equals(command.mappingRevision())
            || !current.policy().version().equals(command.policyVersion()))
            throw new BusinessException(409,"TEMPORARY_BASELINE_CHANGED","库存、映射或政策已变化，请重新核对准备资料");
        return current;
    }
    private record Snapshot(String version,int rows,String sha,String status) { }
    public record View(String warehouseId,String inventorySnapshotId,String inventoryVersion,String mappingRevision,
        int inventoryRows,String inventorySha256,PolicyStore.Policy policy) { }
}
