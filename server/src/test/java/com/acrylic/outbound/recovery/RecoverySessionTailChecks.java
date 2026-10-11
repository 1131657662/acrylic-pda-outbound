package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverRecoveredSessionTailChecks;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.springframework.security.core.context.SecurityContextHolder;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual original-row, login, identity and pinned-material tail changes remain uncommitted. */
final class RecoverySessionTailChecks {
    static void verify(RecoveryRecipientSessionFixture f,RecoveryRecipientSession.Request request,RecoveryRecipientSession session) throws Exception {
        var e=f.fixture.enrollment;
        reject(f,session,()->assertThat(e.jdbc.update("UPDATE warehouse SET name=CONCAT(name,'-session-tail') WHERE id<>?",
            Ids.bytes(uuid(f.body,"warehouseId")))).isPositive());
        reject(f,session,()->assertThat(e.jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",
            Ids.bytes(uuid(f.body.path("recipient"),"userId")))).isEqualTo(1));
        reject(f,session,()->assertThat(e.jdbc.update("UPDATE pda_session SET access_hash=REPEAT('a',64) WHERE id=?",
            Ids.bytes(uuid(f.body.path("login"),"sessionId")))).isEqualTo(1));
        reject(f,session,()->assertThat(e.jdbc.update("UPDATE device SET last_seen_at=TIMESTAMPADD(SECOND,1,last_seen_at) WHERE id=?",
            Ids.bytes(uuid(f.body.path("recipient"),"deviceId")))).isEqualTo(1));
        reject(f,session,()->assertThat(e.jdbc.update("UPDATE pda_session SET revoked_at=UTC_TIMESTAMP(3) WHERE id=?",
            Ids.bytes(uuid(f.body.path("login"),"sessionId")))).isEqualTo(1));
        byte[] original=Files.readAllBytes(request.file());
        try { reject(f,session,()->{
            try { Files.writeString(request.file(),"tampered receiving login"); }
            catch(java.io.IOException error) { throw new UncheckedIOException(error); }
        }); } finally { Files.write(request.file(),original); }
    }
    private static void reject(RecoveryRecipientSessionFixture f,RecoveryRecipientSession session,Runnable change) {
        f.authenticate();
        try { HandoverRecoveredSessionTailChecks.reject(f.fixture.enrollment.app,session,f.fixture.enrollment.setup.request(),change); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private RecoverySessionTailChecks() { }
}
