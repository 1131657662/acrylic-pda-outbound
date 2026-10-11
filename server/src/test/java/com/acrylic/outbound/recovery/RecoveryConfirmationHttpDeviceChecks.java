package com.acrylic.outbound.recovery;

import com.acrylic.outbound.devices.DeviceCrypto;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Current device and identity versions remain authoritative even for immutable receipt replay. */
final class RecoveryConfirmationHttpDeviceChecks {
    static void verify(WebApplicationContext app,Path attempt,RecoveryReceivedConfirmationHttpChecks.Request request) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class);
        var actor=jdbc.queryForMap("SELECT device_id,user_id FROM pda_session WHERE access_hash=?",DeviceCrypto.sha(request.token()));
        byte[] device=(byte[])actor.get("device_id"),user=(byte[])actor.get("user_id");
        assertThat(jdbc.update("UPDATE device SET status='DISABLED' WHERE id=?",device)).isEqualTo(1);
        try {
            var response=RecoveryReceivedConfirmationHttpChecks.post(request,request.command(),request.command().path("transportRecoveryEpoch").asText());
            RecoveryReceivedConfirmationHttpChecks.refused(attempt,"disabled-device",response,401,"DEVICE_DISABLED");
        } finally { assertThat(jdbc.update("UPDATE device SET status='ACTIVE' WHERE id=?",device)).isEqualTo(1); }
        assertThat(jdbc.update("UPDATE device SET row_version=row_version+1 WHERE id=?",device)).isEqualTo(1);
        try { refused(attempt,request,"changed-device-version"); }
        finally { assertThat(jdbc.update("UPDATE device SET row_version=row_version-1 WHERE id=?",device)).isEqualTo(1); }
        assertThat(jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",user)).isEqualTo(1);
        try { refused(attempt,request,"changed-user-version"); }
        finally { assertThat(jdbc.update("UPDATE app_user SET auth_version=auth_version-1 WHERE id=?",user)).isEqualTo(1); }
    }
    private static void refused(Path attempt,RecoveryReceivedConfirmationHttpChecks.Request request,String name) throws Exception {
        var response=RecoveryReceivedConfirmationHttpChecks.post(request,request.command(),request.command().path("transportRecoveryEpoch").asText());
        RecoveryReceivedConfirmationHttpChecks.refused(attempt,name,response,401,"AUTH_EXPIRED");
    }
    private RecoveryConfirmationHttpDeviceChecks() { }
}
