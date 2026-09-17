package com.acrylic.outbound.pda;

import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssignmentStore {
    private final JdbcTemplate jdbc;
    public AssignmentStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String SELECT="""
        SELECT BIN_TO_UUID(a.id),BIN_TO_UUID(a.order_id),a.revision,BIN_TO_UUID(a.device_id),d.device_code,
          BIN_TO_UUID(a.operator_id),u.display_name,a.epoch,a.recovery_epoch,BIN_TO_UUID(a.inventory_snapshot_id),
          a.plan_sha256,a.inventory_sha256,a.start_order_scan_seq,a.allocation_version,a.status,a.area_note,
          a.issued_at,a.expires_at,a.download_ack_at,o.signed_token,BIN_TO_UUID(o.id)
        FROM order_assignment a INNER JOIN device d ON d.id=a.device_id INNER JOIN app_user u ON u.id=a.operator_id
        INNER JOIN offline_authorization o ON o.assignment_id=a.id
        """;
    public View get(UUID id) {
        return jdbc.queryForObject(SELECT+" WHERE a.id=?",this::map,Ids.bytes(id));
    }
    public boolean credentialActive(UUID assignment) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM offline_authorization WHERE assignment_id=? AND revoked_at IS NULL AND expires_at>UTC_TIMESTAMP(3)",
            Integer.class,Ids.bytes(assignment))==1;
    }
    public View active(UUID order) {
        var rows=jdbc.query(SELECT+" WHERE a.order_id=? AND a.active_marker=1",this::map,Ids.bytes(order));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Page<View> mine(PdaSessions.Principal principal,String before,int pageSize) {
        int limit=Page.checkedLimit(pageSize);UUID cursor;
        try { cursor=before.isEmpty()?new UUID(-1L,-1L):UUID.fromString(before); }
        catch(IllegalArgumentException error) { throw new BusinessException(400,"INVALID_CURSOR","授权分页游标无效"); }
        var rows=jdbc.query(SELECT+" WHERE a.device_id=? AND a.active_marker=1 AND a.id<? ORDER BY a.id DESC LIMIT ?",this::map,
            Ids.bytes(principal.deviceId()),Ids.bytes(cursor),limit+1);
        return Page.from(rows.stream().map(view->view.forOperator(principal.userId())).toList(),limit,View::id);
    }
    public Publication publication(UUID order,int revision) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(p.id),BIN_TO_UUID(r.inventory_snapshot_id),BIN_TO_UUID(p.policy_id),p.recovery_epoch,p.snapshot_json,
              s.manifest_sha256,r.mapping_revision,v.version,p.auto_deduct_inventory
            FROM order_publication p INNER JOIN order_revision r ON r.order_id=p.order_id AND r.revision=p.revision
            INNER JOIN inventory_snapshot s ON s.id=r.inventory_snapshot_id INNER JOIN policy_version v ON v.id=p.policy_id
            WHERE p.order_id=? AND p.revision=?
            """,(rs,n)->new Publication(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),
                rs.getString(7),rs.getString(8),rs.getBoolean(9)),Ids.bytes(order),revision);
        if(rows.isEmpty()) throw new BusinessException(409,"ORDER_STATE_CONFLICT","订单尚未发布");return rows.getFirst();
    }
    public long nextEpoch(UUID order) {
        return Math.addExact(jdbc.queryForObject("SELECT assignment_epoch FROM outbound_order WHERE id=?",Long.class,Ids.bytes(order)),1);
    }
    public void insert(Work work,Publication publication,OfflineSigner.Signed signed) {
        jdbc.update("""
            INSERT INTO order_assignment(id,warehouse_id,order_id,revision,publication_id,device_id,operator_id,epoch,recovery_epoch,
              inventory_snapshot_id,policy_id,plan_sha256,inventory_sha256,start_order_scan_seq,allocation_version,status,area_note,issued_at,expires_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,1,0,'ACTIVE',?,?,?)
            """,Ids.bytes(work.id()),Ids.bytes(work.principal().warehouseId()),Ids.bytes(work.order()),work.revision(),
            Ids.bytes(UUID.fromString(publication.id())),Ids.bytes(work.principal().deviceId()),Ids.bytes(work.principal().userId()),work.epoch(),
            Long.parseLong(publication.recoveryEpoch()),Ids.bytes(UUID.fromString(publication.snapshotId())),Ids.bytes(UUID.fromString(publication.policyId())),
            work.planSha(),publication.inventorySha(),work.areaNote(),java.sql.Timestamp.from(work.issued()),java.sql.Timestamp.from(work.expires()));
        jdbc.update("INSERT INTO offline_authorization VALUES(?,?,?,?,?,?,NULL,?,?)",Ids.bytes(work.authId()),Ids.bytes(work.id()),
            Ids.bytes(work.principal().userId()),Ids.bytes(work.principal().deviceId()),java.sql.Timestamp.from(work.issued()),
            java.sql.Timestamp.from(work.expires()),signed.keyId(),signed.token());
        jdbc.update("UPDATE outbound_order SET status='ASSIGNED',row_version=row_version+1,assignment_epoch=?,updated_at=UTC_TIMESTAMP(3) WHERE id=?",
            work.epoch(),Ids.bytes(work.order()));
    }
    private View map(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        var ack=rs.getTimestamp(19);
        return new View(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),
            rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getString(13),rs.getString(14),rs.getString(15),
            rs.getString(16),rs.getTimestamp(17).toInstant().toString(),rs.getTimestamp(18).toInstant().toString(),
            ack==null?null:ack.toInstant().toString(),rs.getString(20),rs.getString(21));
    }
    public record Publication(String id,String snapshotId,String policyId,String recoveryEpoch,String snapshotJson,String inventorySha,
        String mappingRevision,String policyVersion,boolean autoDeduct) { }
    public record Work(UUID id,UUID authId,UUID order,int revision,long epoch,PdaSessions.Principal principal,String planSha,
        String areaNote,java.time.Instant issued,java.time.Instant expires) { }
    public record View(String id,String orderId,int revision,String deviceId,String deviceCode,String operatorId,String operatorName,
        String epoch,String serverRecoveryEpoch,String inventorySnapshotId,String planSha256,String inventorySha256,
        String startOrderScanSeq,String allocationVersion,String status,String areaNote,String issuedAt,String expiresAt,
        String downloadAckAt,String offlineToken,String offlineAuthorizationId) {
        public View forOperator(UUID user) {
            if(operatorId.equals(user.toString())) return this;
            // The device still needs to show the held order, but another person cannot recover its credential.
            return new View(id,orderId,revision,deviceId,deviceCode,operatorId,operatorName,epoch,serverRecoveryEpoch,
                inventorySnapshotId,planSha256,inventorySha256,startOrderScanSeq,allocationVersion,status,areaNote,
                issuedAt,expiresAt,downloadAckAt,null,offlineAuthorizationId);
        }
    }
}
