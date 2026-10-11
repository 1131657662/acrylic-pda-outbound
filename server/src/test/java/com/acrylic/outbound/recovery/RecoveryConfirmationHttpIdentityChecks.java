package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Revoked permission/warehouse/session and another valid but unadmitted login fail the production POST. */
final class RecoveryConfirmationHttpIdentityChecks {
    static void verify(WebApplicationContext app,Path attempt,RecoveryReceivedConfirmationHttpChecks.Request request) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class);
        byte[] session=jdbc.queryForObject("SELECT id FROM pda_session WHERE access_hash=?",byte[].class,DeviceCrypto.sha(request.token()));
        byte[] user=jdbc.queryForObject("SELECT user_id FROM pda_session WHERE id=?",byte[].class,session);
        unadmitted(jdbc,attempt,request,session); permission(jdbc,attempt,request,user);
        assertThat(jdbc.update("UPDATE pda_session SET revoked_at=UTC_TIMESTAMP(3) WHERE id=?",session)).isEqualTo(1);
        try { refused(attempt,request,"revoked-session",401,"AUTH_EXPIRED"); }
        finally { assertThat(jdbc.update("UPDATE pda_session SET revoked_at=NULL WHERE id=?",session)).isEqualTo(1); }
        byte[] warehouse=jdbc.queryForObject("SELECT warehouse_id FROM device WHERE id=(SELECT device_id FROM pda_session WHERE id=?)",byte[].class,session);
        assertThat(jdbc.update("DELETE FROM user_warehouse WHERE user_id=? AND warehouse_id=?",user,warehouse)).isEqualTo(1);
        try { refused(attempt,request,"revoked-warehouse",401,"AUTH_EXPIRED"); }
        finally { assertThat(jdbc.update("INSERT INTO user_warehouse VALUES (?,?)",user,warehouse)).isEqualTo(1); }
    }
    private static void unadmitted(JdbcTemplate jdbc,Path attempt,RecoveryReceivedConfirmationHttpChecks.Request request,byte[] session) throws Exception {
        byte[] extra=Ids.bytes(UUID.randomUUID()); String token=DeviceCrypto.secret();
        assertThat(jdbc.update("""
            INSERT INTO pda_session SELECT ?,device_id,user_id,user_auth_version,device_version,?,?,access_expires_at,expires_at,NULL,created_at
            FROM pda_session WHERE id=?
            """,extra,DeviceCrypto.sha(token),DeviceCrypto.sha(DeviceCrypto.secret()),session)).isEqualTo(1);
        try { refused(attempt,new RecoveryReceivedConfirmationHttpChecks.Request(request.path(),token,request.command()),"unadmitted-synthetic-session",503,"RECOVERY_SESSION_ACTOR"); }
        finally { assertThat(jdbc.update("DELETE FROM pda_session WHERE id=?",extra)).isEqualTo(1); }
    }
    private static void permission(JdbcTemplate jdbc,Path attempt,RecoveryReceivedConfirmationHttpChecks.Request request,byte[] user) throws Exception {
        byte[] permission=jdbc.queryForObject("SELECT id FROM permission WHERE code='pda:scan'",byte[].class);
        var roles=jdbc.query("SELECT role_id FROM user_role WHERE user_id=?",(rs,n)->rs.getBytes(1),user);
        var affected=roles.stream().filter(role->jdbc.queryForObject("SELECT COUNT(*) FROM role_permission WHERE role_id=? AND permission_id=?",Integer.class,role,permission)>0).toList();
        assertThat(affected).isNotEmpty();
        for(byte[] role:affected) assertThat(jdbc.update("DELETE FROM role_permission WHERE role_id=? AND permission_id=?",role,permission)).isEqualTo(1);
        try { refused(attempt,request,"revoked-scan-permission",503,"RECOVERY_RECIPIENT_PERMISSION"); }
        finally { for(byte[] role:affected) assertThat(jdbc.update("INSERT INTO role_permission VALUES (?,?)",role,permission)).isEqualTo(1); }
    }
    private static void refused(Path attempt,RecoveryReceivedConfirmationHttpChecks.Request request,String name,int status,String code) throws Exception {
        var response=RecoveryReceivedConfirmationHttpChecks.post(request,request.command(),request.command().path("transportRecoveryEpoch").asText());
        RecoveryReceivedConfirmationHttpChecks.refused(attempt,name,response,status,code);
    }
    private RecoveryConfirmationHttpIdentityChecks() { }
}
