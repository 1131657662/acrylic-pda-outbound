package com.acrylic.outbound.devices;

import com.acrylic.outbound.common.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceStore {
    private final JdbcTemplate jdbc;
    public DeviceStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String SELECT="SELECT BIN_TO_UUID(id),BIN_TO_UUID(warehouse_id),device_code,name,public_key,key_sha256,status,row_version,created_at,last_seen_at,temporary_order_enabled FROM device";
    public Device find(UUID id,boolean lock) {
        var rows=jdbc.query(SELECT+" WHERE id=?"+(lock?" FOR UPDATE":""),this::map,Ids.bytes(id));
        if(rows.isEmpty()) throw new BusinessException(404,"DEVICE_NOT_FOUND","设备不存在或无权访问");
        return rows.getFirst();
    }
    public Page<Device> list(UUID warehouse,String before,int pageSize) {
        int limit=Page.checkedLimit(pageSize);
        UUID cursor;
        try { cursor=before.isEmpty()?new UUID(-1L,-1L):UUID.fromString(before); }
        catch(IllegalArgumentException error) { throw new BusinessException(400,"INVALID_CURSOR","设备分页游标无效"); }
        return Page.from(jdbc.query(SELECT+" WHERE warehouse_id=? AND id<? ORDER BY id DESC LIMIT ?",this::map,
            Ids.bytes(warehouse),Ids.bytes(cursor),limit+1),limit,d->d.id().toString());
    }
    public void insert(NewDevice work,DeviceCommands.Register input) {
        jdbc.update("""
            INSERT INTO device(id,warehouse_id,device_code,name,public_key,key_sha256,status,row_version,created_by,created_at)
            VALUES(?,?,?,?,?,?,'ACTIVE',1,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(work.id()),Ids.bytes(work.warehouse()),input.deviceCode(),input.name().strip(),input.publicKey(),
            DeviceCrypto.sha(input.publicKey()),Ids.bytes(work.actor()));
    }
    public void status(Device device,String status) {
        new com.acrylic.outbound.pda.PdaSessionLocks(jdbc).device(device.id());
        jdbc.update("UPDATE device SET status=?,row_version=row_version+1 WHERE id=?",status,Ids.bytes(device.id()));
        jdbc.update("UPDATE pda_session SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(3)) WHERE device_id=?",Ids.bytes(device.id()));
    }
    public void temporaryOrder(Device device,boolean enabled) {
        new com.acrylic.outbound.pda.PdaSessionLocks(jdbc).device(device.id());
        jdbc.update("UPDATE device SET temporary_order_enabled=?,row_version=row_version+1 WHERE id=?",enabled,Ids.bytes(device.id()));
        jdbc.update("UPDATE pda_session SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(3)) WHERE device_id=?",Ids.bytes(device.id()));
    }
    private Device map(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        var seen=rs.getTimestamp(10);
        return new Device(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),rs.getString(3),rs.getString(4),
            rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getTimestamp(9).toInstant().toString(),seen==null?null:seen.toInstant().toString(),rs.getBoolean(11));
    }
    public record NewDevice(UUID id,UUID warehouse,UUID actor) { }
    public record Device(UUID id,UUID warehouseId,String deviceCode,String name,String publicKey,String keySha256,
        String status,String rowVersion,String createdAt,String lastSeenAt,boolean temporaryOrderEnabled) {
        public void requireActive() { if(!status.equals("ACTIVE")) throw new BusinessException(401,"DEVICE_DISABLED","设备已停用，请联系管理员；本机记录必须保留"); }
    }
}
