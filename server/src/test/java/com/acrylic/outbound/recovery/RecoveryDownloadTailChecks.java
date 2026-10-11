package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverRecoveredDownloadTailChecks;
import java.io.UncheckedIOException;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual late original-row and signature changes cannot yield a deliverable download. */
final class RecoveryDownloadTailChecks {
    static void verify(WebApplicationContext app,Path attempt,RecoveryReceivedSessionRestart.Reloaded input) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class);
        reject(app,attempt,input,()->assertThat(jdbc.update("UPDATE warehouse SET name=CONCAT(name,'-download-tail') WHERE id<>?",
            Ids.bytes(uuid(input.session().facts(),"warehouseId")))).isPositive());
        reject(app,attempt,input,()->assertThat(jdbc.update("UPDATE pda_session SET revoked_at=UTC_TIMESTAMP(3) WHERE id=?",
            Ids.bytes(uuid(input.session().facts().path("login"),"sessionId")))).isEqualTo(1));
        byte[] info=Files.readAllBytes(attempt.resolve("session-restart-input.json")); var body=readCanonical(info);
        Path file=Path.of(text(body,"sessionFile")); byte[] original=Files.readAllBytes(file);
        try { reject(app,attempt,input,()->{
            try { Files.writeString(file,"tampered download session"); }
            catch(java.io.IOException error) { throw new UncheckedIOException(error); }
        }); } finally { Files.write(file,original); }
    }
    private static void reject(WebApplicationContext app,Path attempt,RecoveryReceivedSessionRestart.Reloaded input,Runnable change) throws Exception {
        RecoveryReceivedDownloadChecks.authenticate(app,attempt);
        try { HandoverRecoveredDownloadTailChecks.reject(app,input.session(),input.request(),change); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private RecoveryDownloadTailChecks() { }
}
