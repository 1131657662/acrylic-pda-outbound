package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RescueStore {
    private final JdbcTemplate jdbc;
    public RescueStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void request(UUID id,RescueCommands.Document doc,RescueCommands.Request input,String keyDigest) {
        jdbc.update("INSERT INTO rescue_request VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))",Ids.bytes(id),Ids.bytes(doc.warehouseId()),
            Ids.bytes(doc.deviceId()),Ids.bytes(doc.operatorId()),Ids.bytes(doc.rescuerId()),keyDigest,input.originalText(),input.signature());
        for(int index=0;index<doc.orderScope().size();index++) {
            var target=doc.orderScope().get(index);
            jdbc.update("INSERT INTO rescue_request_scope VALUES(?,?,?,?)",Ids.bytes(id),index+1,target.source(),Ids.bytes(target.localOrderId()));
        }
    }
    public Request request(UUID id,boolean lock) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),BIN_TO_UUID(warehouse_id),BIN_TO_UUID(device_id),BIN_TO_UUID(operator_id),
              BIN_TO_UUID(rescuer_id),device_key_sha256,original_text,device_signature FROM rescue_request WHERE id=?
            """+(lock?" FOR UPDATE":""),(rs,n)->new Request(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)),UUID.fromString(rs.getString(4)),UUID.fromString(rs.getString(5)),
                rs.getString(6),rs.getString(7),rs.getString(8)),Ids.bytes(id));
        if(rows.isEmpty()) throw new BusinessException(404,"RESCUE_REQUEST_NOT_FOUND","救援申请不存在或无权访问");
        return rows.getFirst();
    }
    public List<RescueCommands.Target> scope(UUID request) {
        return jdbc.query("SELECT source,BIN_TO_UUID(local_order_id) FROM rescue_request_scope WHERE request_id=? ORDER BY ordinal",
            (rs,n)->new RescueCommands.Target(rs.getString(1),UUID.fromString(rs.getString(2))),Ids.bytes(request));
    }
    public void grant(UUID id,Request request,UUID issuer,Instant issued,Instant expires,String token,String reason) {
        jdbc.update("INSERT INTO rescue_grant VALUES(?,?,?,?,?,?,?)",Ids.bytes(id),Ids.bytes(request.id()),Ids.bytes(issuer),
            java.sql.Timestamp.from(issued),expires==null?null:java.sql.Timestamp.from(expires),token,reason);
    }
    public RescueCommands.GrantReceipt grant(UUID id) {
        var grant=grantDetails(id);
        return new RescueCommands.GrantReceipt(grant.id().toString(),grant.token());
    }
    public Grant grantDetails(UUID id) {
        var rows=jdbc.query("SELECT BIN_TO_UUID(id),BIN_TO_UUID(request_id),issued_at,expires_at,original_token FROM rescue_grant WHERE id=?",
            (rs,n)->new Grant(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                rs.getTimestamp(3).toInstant(),rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toInstant(),rs.getString(5)),Ids.bytes(id));
        if(rows.isEmpty()) throw new BusinessException(404,"RESCUE_GRANT_NOT_FOUND","救援授权不存在或无权访问");
        return rows.getFirst();
    }
    public UUID grantForRequest(UUID id) {
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM rescue_grant WHERE request_id=?",String.class,Ids.bytes(id));
        return rows.isEmpty()?null:UUID.fromString(rows.getFirst());
    }
    public UUID requestForGrant(UUID id) {
        var rows=jdbc.queryForList("SELECT BIN_TO_UUID(request_id) FROM rescue_grant WHERE id=?",String.class,Ids.bytes(id));
        if(rows.isEmpty()) throw new BusinessException(404,"RESCUE_GRANT_NOT_FOUND","救援授权不存在或无权访问");
        return UUID.fromString(rows.getFirst());
    }
    public void revoke(UUID id,UUID issuer,Instant at,String token,String reason) {
        jdbc.update("INSERT INTO rescue_revocation VALUES(?,?,?,?,?)",Ids.bytes(id),Ids.bytes(issuer),java.sql.Timestamp.from(at),token,reason);
    }
    public RescueCommands.RevocationReceipt revocation(UUID id) {
        var rows=jdbc.query("SELECT BIN_TO_UUID(grant_id),original_token FROM rescue_revocation WHERE grant_id=?",
            (rs,n)->new RescueCommands.RevocationReceipt(rs.getString(1),rs.getString(2)),Ids.bytes(id));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public void requireRescuer(UUID user,UUID warehouse) {
        Integer count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM app_user u WHERE id=? AND status='ACTIVE'
             AND EXISTS(SELECT 1 FROM user_warehouse uw WHERE uw.user_id=u.id AND uw.warehouse_id=?)
             AND EXISTS(SELECT 1 FROM user_role ur INNER JOIN role_permission rp ON rp.role_id=ur.role_id
                INNER JOIN permission p ON p.id=rp.permission_id WHERE ur.user_id=u.id AND p.code='rescue:read')
            """,Integer.class,Ids.bytes(user),Ids.bytes(warehouse));
        if(count!=1) throw new BusinessException(403,"RESCUE_RECIPIENT_UNAUTHORIZED","救援人未获该仓库的专用只读救援资格");
    }
    public void requireFormalTargets(RescueCommands.Document doc) {
        for(var target:doc.orderScope()) {
            if(!target.source().equals("FORMAL")) continue;
            var count=jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE id=? AND device_id=? AND operator_id=? AND warehouse_id=?",
                Integer.class,Ids.bytes(target.localOrderId()),Ids.bytes(doc.deviceId()),Ids.bytes(doc.operatorId()),Ids.bytes(doc.warehouseId()));
            if(count!=1) throw new BusinessException(404,"RESCUE_ORDER_NOT_FOUND","申请中的原正式单不属于本人员/设备/仓库");
        }
    }
    public record Request(UUID id,UUID warehouseId,UUID deviceId,UUID operatorId,UUID rescuerId,
        String deviceKeySha256,String originalText,String signature) {
        public RescueCommands.RequestReceipt receipt() { return new RescueCommands.RequestReceipt(id.toString(),DeviceCrypto.sha(originalText)); }
    }
    public record Grant(UUID id,UUID requestId,Instant issuedAt,Instant expiresAt,String token) { }
}
