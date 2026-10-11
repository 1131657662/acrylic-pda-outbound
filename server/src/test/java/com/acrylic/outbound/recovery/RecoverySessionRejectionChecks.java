package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.HandoverRecoveredRecipientSession;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Wrong signed scope, actual identity, expiry and final SQL never create a session admission. */
final class RecoverySessionRejectionChecks {
    static void verify(RecoveryRecipientSessionFixture f,RecoveryRecipientSession.Request request,RecoveryRecipientSession session) throws Exception {
        assertThatThrownBy(()->RecoveryRecipientSession.verify(new RecoveryRecipientSession.Request(request.authority(),request.file(),"a".repeat(64)),f.qualification))
            .hasMessage("RECOVERY_SESSION_PIN_MISMATCH");
        for(var change:Map.of("purpose","TEMP_WORK","caseId",UUID.randomUUID().toString(),"scopeId",UUID.randomUUID().toString(),
                "warehouseId",UUID.randomUUID().toString(),"qualificationSha256","b".repeat(64)).entrySet())
            invalid(f,f.body.deepCopy().put(change.getKey(),change.getValue()));
        var old=f.body.deepCopy(); ((ObjectNode)old.path("control")).put("transportRecoveryEpoch",UUID.randomUUID().toString()); invalid(f,old);
        var other=f.body.deepCopy(); ((ObjectNode)other.path("recipient")).put("userId",UUID.randomUUID().toString()); invalid(f,other);
        var login=f.body.deepCopy(); ((ObjectNode)login.path("login")).put("sessionId",UUID.randomUUID().toString());
        var wrongSession=RecoveryRecipientSession.verify(f.sign(login),f.qualification);
        assertThatThrownBy(()->f.admit(wrongSession)).hasMessage("RECOVERY_SESSION_ACTOR");
        var bad=Files.readAllBytes(request.file()); bad[bad.length/2]^=1;
        var corrupt=request.file().resolveSibling("session-corrupt.json"); Files.write(corrupt,bad);
        assertThatThrownBy(()->RecoveryRecipientSession.verify(new RecoveryRecipientSession.Request(request.authority(),corrupt,sha256(bad)),f.qualification))
            .isInstanceOf(RecoveryControlException.class);
        var expired=f.body.deepCopy().put("expiresAt",Instant.parse(text(f.body,"requestedAt")).plusMillis(1).toString());
        var denied=RecoveryRecipientSession.verify(f.sign(expired),f.qualification);
        assertThatThrownBy(()->f.admit(denied)).hasMessage("RECOVERY_SESSION_EXPIRED");
        actor(f,session); finalSql(f,session);
        var retained=RecoveryRecipientSession.reopen(request,f.qualification);
        assertThatThrownBy(()->f.admit(retained)).hasMessage("RECOVERY_SESSION_RETAINED_REQUIRED");
    }
    private static void invalid(RecoveryRecipientSessionFixture f,ObjectNode body) throws Exception {
        var request=f.sign(body);
        assertThatThrownBy(()->RecoveryRecipientSession.verify(request,f.qualification)).isInstanceOf(RecoveryControlException.class);
    }
    private static void actor(RecoveryRecipientSessionFixture f,RecoveryRecipientSession session) {
        SecurityContextHolder.clearContext(); var e=f.fixture.enrollment;
        assertThatThrownBy(()->e.app.getBean(HandoverRecoveredRecipientSession.class).admit(session,e.setup.request(),"missing-actor"))
            .hasMessage("RECOVERY_SESSION_PDA_REQUIRED");
        RecoveryRecipientFixture.authenticate(e.setup.registrar());
        try { assertThatThrownBy(()->e.app.getBean(HandoverRecoveredRecipientSession.class).admit(session,e.setup.request(),"web-actor"))
            .hasMessage("RECOVERY_SESSION_PDA_REQUIRED"); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private static void finalSql(RecoveryRecipientSessionFixture f,RecoveryRecipientSession session) {
        var jdbc=f.fixture.enrollment.jdbc;
        jdbc.execute("CREATE TRIGGER test_session_final BEFORE INSERT ON recovery_recipient_session FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='session final SQL'");
        try { assertThatThrownBy(()->f.admit(session)).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { jdbc.execute("DROP TRIGGER test_session_final"); }
    }
    private RecoverySessionRejectionChecks() { }
}
