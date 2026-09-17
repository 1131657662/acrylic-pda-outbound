package com.acrylic.outbound.policies;

import com.acrylic.outbound.common.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PolicyStore {
    private final JdbcTemplate jdbc;
    public PolicyStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String SELECT="""
        SELECT BIN_TO_UUID(p.id),p.version,p.auto_deduct_inventory,p.schema_version,p.offline_hours,
          p.short_shipment_policy,p.concurrency_mode,p.scan_mode,p.reason,u.display_name,p.published_at
        FROM policy_version p INNER JOIN app_user u ON u.id=p.published_by WHERE p.warehouse_id=?
        """;
    public Policy current(UUID warehouse) {
        var rows=jdbc.query(SELECT+" ORDER BY p.version DESC LIMIT 1",this::map,Ids.bytes(warehouse));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Policy byId(UUID warehouse,UUID id) {
        return jdbc.queryForObject(SELECT+" AND p.id=?",this::map,Ids.bytes(warehouse),Ids.bytes(id));
    }
    public Page<Policy> history(UUID warehouse,long before,int pageSize) {
        int limit=Page.checkedLimit(pageSize);
        if(before<=0) throw new BusinessException(400,"INVALID_CURSOR","政策版本游标无效");
        return Page.from(jdbc.query(SELECT+" AND p.version<? ORDER BY p.version DESC LIMIT ?",this::map,
            Ids.bytes(warehouse),before,limit+1),limit,Policy::version);
    }
    public Policy require(UUID warehouse,String expected) {
        if(expected==null) throw new BusinessException(400,"INVALID_REQUEST","发布必须确认政策版本");
        var policy=current(warehouse);
        if(policy==null) throw new BusinessException(409,"POLICY_REQUIRED","请管理员先明确配置自动扣库存政策");
        if(!policy.version().equals(expected)) throw new BusinessException(409,"POLICY_VERSION_CONFLICT","政策已变化，请重新核对后发布");
        return policy;
    }
    public Policy insert(Work work,PolicyService.Command input) {
        UUID id=Ids.next();
        jdbc.update("""
            INSERT INTO policy_version(id,warehouse_id,version,auto_deduct_inventory,schema_version,offline_hours,
              short_shipment_policy,concurrency_mode,scan_mode,reason,published_by,published_at)
            VALUES(?,?,?,?,1,48,'SUPERVISOR_APPROVAL','PHYSICAL_SEPARATION','AUTO_SCAN_MANUAL_CONFIRM',?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(work.warehouse()),work.version(),input.autoDeductInventory(),input.reason().strip(),Ids.bytes(work.actor()));
        return byId(work.warehouse(),id);
    }
    private Policy map(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        return new Policy(rs.getString(1),rs.getString(2),rs.getBoolean(3),rs.getInt(4),rs.getInt(5),rs.getString(6),
            rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getTimestamp(11).toInstant().toString());
    }
    public record Work(UUID warehouse,UUID actor,long version) { }
    public record Policy(String id,String version,boolean autoDeductInventory,int schemaVersion,int offlineHours,
        String shortShipmentPolicy,String concurrencyMode,String scanMode,String reason,String publishedBy,String publishedAt) { }
    public record Current(Policy policy) { }
}
