package com.acrylic.outbound.pairing;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PairingStore {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public PairingStore(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }
    public Row find(UUID id,boolean lock) {
        var rows=jdbc.query("SELECT * FROM device_pairing WHERE id=?"+(lock?" FOR UPDATE":""),this::map,Ids.bytes(id));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public Row byCode(String code) {
        var rows=jdbc.query("SELECT * FROM device_pairing WHERE live_code=? AND expires_at>UTC_TIMESTAMP(3)",this::map,code);
        if(rows.isEmpty()) throw new BusinessException(404,"PAIRING_NOT_FOUND","登记码不存在或已过期，请核对原PDA");
        return rows.getFirst();
    }
    public void lockKey(String hash) {
        jdbc.update("INSERT IGNORE INTO pairing_key_guard VALUES(?)",hash);
        jdbc.queryForObject("SELECT key_sha256 FROM pairing_key_guard WHERE key_sha256=? FOR UPDATE",String.class,hash);
        jdbc.update("UPDATE device_pairing SET status='EXPIRED' WHERE live_key=? AND expires_at<=UTC_TIMESTAMP(3)",hash);
    }
    public void requireNewKey(String hash) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM device WHERE key_sha256=?",Integer.class,hash)>0)
            throw new BusinessException(409,"DEVICE_ALREADY_REGISTERED","本机公钥已有登记，请恢复原申请或联系管理员核对原身份");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM device_pairing WHERE live_key=?",Integer.class,hash)>0)
            throw new BusinessException(409,"PAIRING_ALREADY_PENDING","本机已有未完成申请，请恢复原申请");
    }
    public void insert(PairingCommands.Payload p,String code,String epoch) {
        jdbc.update("""
            INSERT INTO device_pairing(id,public_key,key_sha256,credential_sha256,request_sha256,model,short_code,status,created_at,expires_at,recovery_epoch)
            VALUES(?,?,?,?,?,?,?,'WAITING',UTC_TIMESTAMP(3),TIMESTAMPADD(MINUTE,10,UTC_TIMESTAMP(3)),?)
            """,Ids.bytes(p.applicationId()),p.publicKey(),DeviceCrypto.sha(p.publicKey()),DeviceCrypto.sha(p.credential()),
            p.originalHash(),p.model(),code,epoch);
    }
    public Row expire(Row row) {
        jdbc.update("UPDATE device_pairing SET status='EXPIRED' WHERE id=? AND status IN ('WAITING','PENDING') AND expires_at<=UTC_TIMESTAMP(3)",Ids.bytes(row.id()));
        return find(row.id(),false);
    }
    public void cancel(UUID id) { jdbc.update("UPDATE device_pairing SET status='CANCELLED' WHERE id=?",Ids.bytes(id)); }
    public void approve(Row row,PairingCommands.Approval approval,Approver actor) {
        var text=new JsonText(json);String serialized=text.encode(approval);
        jdbc.update("UPDATE device_pairing SET status='PENDING',approval_json=?,approval_sha256=?,approved_by=?,approved_auth_version=? WHERE id=?",
            serialized,DeviceCrypto.sha(approval.binding()),Ids.bytes(actor.id()),actor.version(),Ids.bytes(row.id()));
    }
    public void complete(UUID id,UUID device) {
        jdbc.update("UPDATE device_pairing SET status='COMPLETED',device_id=? WHERE id=?",Ids.bytes(device),Ids.bytes(id));
    }
    public PairingCommands.View view(Row row) {
        String status=row.status();
        if((status.equals("WAITING") || status.equals("PENDING")) && !java.time.Instant.parse(row.expires()).isAfter(java.time.Instant.now())) status="EXPIRED";
        return new PairingCommands.View(row.id(),status,row.code(),row.model(),row.created(),row.expires(),row.keySha(),
            row.epoch(),row.approvalSha(),row.approval(),row.device());
    }
    private Row map(ResultSet r,int n) throws SQLException {
        PairingCommands.Approval approval=null;
        try { if(r.getString("approval_json")!=null) approval=json.readValue(r.getString("approval_json"),PairingCommands.Approval.class); }
        catch(java.io.IOException e) { throw new IllegalStateException("Invalid stored pairing approval",e); }
        return new Row(uuid(r,"id"),r.getString("public_key"),r.getString("key_sha256"),r.getString("credential_sha256"),
            r.getString("request_sha256"),r.getString("model"),r.getString("short_code"),r.getString("status"),
            r.getTimestamp("created_at").toInstant().toString(),r.getTimestamp("expires_at").toInstant().toString(),
            r.getString("recovery_epoch"),approval,r.getString("approval_sha256"),uuid(r,"approved_by"),r.getLong("approved_auth_version"),uuid(r,"device_id"));
    }
    private UUID uuid(ResultSet r,String column) throws SQLException {
        byte[] bytes=r.getBytes(column);if(bytes==null)return null;
        var b=java.nio.ByteBuffer.wrap(bytes);return new UUID(b.getLong(),b.getLong());
    }
    public record Approver(UUID id,long version) { }
    public record Row(UUID id,String publicKey,String keySha,String credentialSha,String requestSha,String model,String code,
        String status,String created,String expires,String epoch,PairingCommands.Approval approval,String approvalSha,
        UUID actor,long actorVersion,UUID device) { }
}
