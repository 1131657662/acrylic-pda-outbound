package com.acrylic.outbound.pda;

import com.acrylic.outbound.auth.*;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PdaSessions {
    private final JdbcTemplate jdbc;private final IdentityStore identities;private final DeviceStore devices;
    public PdaSessions(JdbcTemplate jdbc,IdentityStore identities,DeviceStore devices) { this.jdbc=jdbc;this.identities=identities;this.devices=devices; }
    public Principal authenticate(String token) { return validate(find(DeviceCrypto.sha(token),false)); }
    public Principal refresh(String token,DeviceStore.Device device) {
        var row=find(DeviceCrypto.sha(token),true);
        if(!row.device().equals(device.id())) throw expired();
        return validate(row);
    }
    public Principal recheck(Principal principal) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(s.id),BIN_TO_UUID(s.device_id),u.username,s.user_auth_version,s.device_version
            FROM pda_session s INNER JOIN app_user u ON u.id=s.user_id
            WHERE s.id=? AND s.revoked_at IS NULL AND s.access_expires_at>UTC_TIMESTAMP(3) AND s.expires_at>UTC_TIMESTAMP(3)
            """,this::map,Ids.bytes(principal.sessionId()));
        if(rows.isEmpty()) throw expired();return validate(rows.getFirst());
    }
    private Row find(String hash,boolean refresh) {
        String column=refresh?"refresh_hash":"access_hash";
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(s.id),BIN_TO_UUID(s.device_id),u.username,s.user_auth_version,s.device_version
            FROM pda_session s INNER JOIN app_user u ON u.id=s.user_id WHERE s.
            """+column+"=? AND s.revoked_at IS NULL AND s.expires_at>UTC_TIMESTAMP(3)"+
            (refresh?"":" AND s.access_expires_at>UTC_TIMESTAMP(3)"),this::map,hash);
        if(rows.isEmpty()) throw expired();return rows.getFirst();
    }
    private Principal validate(Row row) {
        var device=devices.find(row.device(),false);device.requireActive();var user=identities.loadUserByUsername(row.username());
        if(!user.active() || user.authVersion()!=row.userVersion() || !device.rowVersion().equals(row.deviceVersion())) throw expired();
        if(!identities.permissions(user.id()).contains("pda:login") || identities.warehouses(user.id()).stream().noneMatch(w->w.id().equals(device.warehouseId()))) throw expired();
        return new Principal(row.id(),user.id(),user.username(),device.id(),device.warehouseId(),Long.toString(user.authVersion()),device.rowVersion());
    }
    public Tokens issue(SessionUser user,DeviceStore.Device device) {
        UUID id=Ids.next();String access=DeviceCrypto.secret(),refresh=DeviceCrypto.secret();
        jdbc.update("""
            INSERT INTO pda_session VALUES(?,?,?,?,?,?,?,TIMESTAMPADD(SECOND,900,UTC_TIMESTAMP(3)),
              TIMESTAMPADD(DAY,30,UTC_TIMESTAMP(3)),NULL,UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(device.id()),Ids.bytes(user.id()),user.authVersion(),Long.parseLong(device.rowVersion()),
            DeviceCrypto.sha(access),DeviceCrypto.sha(refresh));
        return tokens(id,user.id(),device,access,refresh);
    }
    public Tokens rotate(Principal principal,DeviceStore.Device device) {
        String access=DeviceCrypto.secret(),refresh=DeviceCrypto.secret();
        jdbc.update("UPDATE pda_session SET access_hash=?,refresh_hash=?,access_expires_at=LEAST(expires_at,TIMESTAMPADD(SECOND,900,UTC_TIMESTAMP(3))) WHERE id=?",
            DeviceCrypto.sha(access),DeviceCrypto.sha(refresh),Ids.bytes(principal.sessionId()));
        return tokens(principal.sessionId(),principal.userId(),device,access,refresh);
    }
    private Tokens tokens(UUID id,UUID user,DeviceStore.Device device,String access,String refresh) {
        jdbc.update("UPDATE device SET last_seen_at=UTC_TIMESTAMP(3) WHERE id=?",Ids.bytes(device.id()));
        var times=jdbc.queryForObject("SELECT access_expires_at,expires_at FROM pda_session WHERE id=?",
            (rs,n)->new Instant[]{rs.getTimestamp(1).toInstant(),rs.getTimestamp(2).toInstant()},Ids.bytes(id));
        int remaining=(int)Math.max(0,java.time.Duration.between(Instant.now(),times[0]).toSeconds());
        String authVersion=jdbc.queryForObject("SELECT user_auth_version FROM pda_session WHERE id=?",String.class,Ids.bytes(id));
        return new Tokens(access,refresh,"Bearer",remaining,id.toString(),user.toString(),device.id().toString(),device.warehouseId().toString(),
            times[0].toString(),times[1].toString(),authVersion,device.rowVersion(),capabilities(user,device));
    }
    private Capabilities capabilities(UUID user,DeviceStore.Device device) {
        var permissions=identities.permissions(user);
        boolean formal=permissions.contains("pda:assign"), submit=permissions.contains("pda:submit");
        boolean temporary=device.temporaryOrderEnabled() && submit && permissions.contains("pda:scan") && permissions.contains("temporary_order:create");
        return new Capabilities(formal,temporary,formal || temporary,submit,permissions.contains("pda:recover"));
    }
    public void revoke(UUID session) { jdbc.update("UPDATE pda_session SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(3)) WHERE id=?",Ids.bytes(session)); }
    private Row map(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        return new Row(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),rs.getString(3),rs.getLong(4),rs.getString(5));
    }
    private BusinessException expired() { return new BusinessException(401,"AUTH_EXPIRED","PDA登录已失效，请重新认证；保留本机数据"); }
    private record Row(UUID id,UUID device,String username,long userVersion,String deviceVersion) { }
    public record Principal(UUID sessionId,UUID userId,String username,UUID deviceId,UUID warehouseId,String userAuthVersion,String deviceVersion) { }
    public record Tokens(String accessToken,String refreshToken,String tokenType,int expiresIn,String sessionId,String userId,
        String deviceId,String warehouseId,String accessExpiresAt,String refreshExpiresAt,String userAuthVersion,String deviceVersion,Capabilities capabilities) { }
    public record Capabilities(boolean formalOrders,boolean temporaryWork,boolean barcodeSync,boolean auditUpload,boolean recoveryRead) { }
}
