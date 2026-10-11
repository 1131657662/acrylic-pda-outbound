package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverRecoveredConfirmationTailChecks;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

final class RecoveryConfirmationTailChecks {
    static void verify(WebApplicationContext app,Path attempt,RecoveryReceivedSessionRestart.Reloaded input,ObjectNode command) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class);
        reject(app,attempt,input,command,()->assertThat(jdbc.update("UPDATE warehouse SET name=CONCAT(name,'-confirmation-tail') WHERE id<>?",
            Ids.bytes(uuid(input.session().facts(),"warehouseId")))).isPositive());
        reject(app,attempt,input,command,()->assertThat(jdbc.update("UPDATE pda_session SET revoked_at=UTC_TIMESTAMP(3) WHERE id=?",
            Ids.bytes(uuid(input.session().facts().path("login"),"sessionId")))).isEqualTo(1));
        reject(app,attempt,input,command,()->assertThat(jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",
            Ids.bytes(uuid(input.session().facts().path("recipient"),"userId")))).isEqualTo(1));
        Path material=Path.of(text(readCanonical(Files.readAllBytes(attempt.resolve("session-restart-input.json"))),"sessionFile"));
        byte[] original=Files.readAllBytes(material);
        try { reject(app,attempt,input,command,()->{try { Files.writeString(material,"altered confirmation pin"); }
            catch(java.io.IOException error) { throw new UncheckedIOException(error); }}); }
        finally { Files.write(material,original); }
        jdbc.execute("CREATE TRIGGER received_confirmation_last_sql BEFORE INSERT ON recovery_handover_confirmation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected final confirmation write'");
        try { assertThatThrownBy(()->RecoveryReceivedConfirmationChecks.confirm(app,attempt,input,command)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { jdbc.execute("DROP TRIGGER received_confirmation_last_sql"); }
    }
    private static void reject(WebApplicationContext app,Path attempt,RecoveryReceivedSessionRestart.Reloaded input,ObjectNode command,Runnable change) throws Exception {
        RecoveryReceivedDownloadChecks.authenticate(app,attempt);
        try { HandoverRecoveredConfirmationTailChecks.reject(app,RecoveryReceivedConfirmationChecks.encode(command),input.session(),input.request(),change); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private RecoveryConfirmationTailChecks() { }
}
